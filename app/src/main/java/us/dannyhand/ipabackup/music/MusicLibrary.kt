package us.dannyhand.ipabackup.music

import java.io.IOException

/** Minimal SQL access so the library reader runs on Android (SQLiteDatabase) and in JVM tests (JDBC). */
interface SqlDb {
    fun tables(): Set<String>
    fun columns(table: String): Set<String>
    fun query(sql: String): List<List<Any?>>
}

/**
 * Reads the iPod's music library from /iTunes_Control/iTunes/MediaLibrary.sqlitedb (iOS 5+).
 * The schema moved columns between iOS versions (e.g. title/location in item vs item_extra),
 * so the query is built from whatever tables and columns are actually present.
 */
class MusicLibrary(private val db: SqlDb) {

    /** What kind of library item this is; decides the save folder and file name. */
    enum class Kind(val label: String, val folder: String) {
        MUSIC("Music", "Music"),
        PODCAST("Podcast", "Podcasts"),
        AUDIOBOOK("Audiobook", "Audiobooks"),
        ITUNES_U("iTunes U", "iTunes U"),
        VIDEO("Video", "Videos"),
        RINGTONE("Ringtone", "Ringtones"),
    }

    data class Track(
        val pid: Long,
        val title: String,
        val artist: String,
        val album: String,
        /** Path relative to the AFC media root, e.g. /iTunes_Control/Music/F12/ABCD.mp3 */
        val devicePath: String,
        val size: Long,
        val trackNumber: Int,
        val albumArtist: String? = null,
        val genre: String? = null,
        val composer: String? = null,
        val year: Int = 0,
        val trackCount: Int = 0,
        val discNumber: Int = 0,
        val discCount: Int = 0,
        val kind: Kind = Kind.MUSIC,
    ) {
        val extension: String get() = devicePath.substringAfterLast('.', "").lowercase()
        val isDrm: Boolean get() = extension == "m4p"
    }

    companion object {
        const val DB_PATH = "/iTunes_Control/iTunes/MediaLibrary.sqlitedb"
        const val UNKNOWN_ARTIST = "Unknown Artist"
        const val UNKNOWN_ALBUM = "Unknown Album"
        val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "m4p", "m4b", "aac", "wav", "aif", "aiff", "caf", "alac", "aa", "m4r")
        val VIDEO_EXTENSIONS = setOf("m4v", "mp4", "mov")
        const val RINGTONES_DIR = "/iTunes_Control/Ringtones"
        const val RINGTONES_PLIST = "/iTunes_Control/iTunes/Ringtones.plist"

        /**
         * MediaLibrary's media_type uses MPMediaType bits: 1 music, 2 podcast, 4 audiobook, 8 iTunes U,
         * 0x100 movie, 0x200 TV show, 0x400 video podcast, 0x800 music video, 0x1000 iTunes U video,
         * 0x2000 home video. Extensions and flags fill in when the type is missing.
         */
        fun kindOf(mediaType: Int?, ext: String, audibleFlag: Boolean = false, genre: String? = null): Kind {
            val mt = mediaType ?: 0
            return when {
                mt and 0x400 != 0 -> Kind.PODCAST
                mt and 0x1000 != 0 -> Kind.ITUNES_U
                mt and 0x2B00 != 0 -> Kind.VIDEO
                mt and 0x2 != 0 -> Kind.PODCAST
                mt and 0x4 != 0 || audibleFlag || ext == "m4b" || ext == "aa" -> Kind.AUDIOBOOK
                mt and 0x8 != 0 -> Kind.ITUNES_U
                mt == 0 && ext in VIDEO_EXTENSIONS -> Kind.VIDEO
                mt == 0 && genre.equals("Podcast", ignoreCase = true) -> Kind.PODCAST
                else -> Kind.MUSIC
            }
        }

        /** File name for a saved copy, by kind: music "Artist - Title", podcasts "Show - Episode", etc. */
        fun saveName(t: Track): String {
            val knownArtist = t.artist.takeIf { it != UNKNOWN_ARTIST }
            val knownAlbum = t.album.takeIf { it != UNKNOWN_ALBUM }
            val base = when (t.kind) {
                Kind.MUSIC, Kind.AUDIOBOOK -> "${t.artist} - ${t.title}"
                Kind.PODCAST, Kind.ITUNES_U -> listOfNotNull(knownAlbum ?: knownArtist, t.title).joinToString(" - ")
                Kind.VIDEO -> listOfNotNull(knownArtist, t.title).joinToString(" - ")
                Kind.RINGTONE -> t.title
            }
            return base.replace(BAD, "_").trim().take(180) + "." + t.extension
        }

        /** Ringtones synced by iTunes: names from Ringtones.plist ({"Ringtones": {"ABCD.m4r": {"Name": …}}}). */
        fun ringtones(plist: Any?, files: List<Pair<String, Long>>): List<Track> {
            val meta = ((plist as? Map<*, *>)?.get("Ringtones") as? Map<*, *>).orEmpty()
            return files.filter { it.first.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS }
                .mapIndexed { i, (name, size) ->
                    val m = meta[name] as? Map<*, *>
                    Track(
                        pid = -1L - i,
                        title = (m?.get("Name") as? String)?.trim()?.ifEmpty { null } ?: name.substringBeforeLast('.'),
                        artist = "Ringtone",
                        album = "Ringtones",
                        devicePath = "$RINGTONES_DIR/$name",
                        size = size,
                        trackNumber = 0,
                        kind = Kind.RINGTONE,
                    )
                }.sortedBy { it.title.lowercase() }
        }
        private val BAD = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")

        /** "Artist - Title.ext", with characters that aren't allowed in file names replaced. */
        fun fileName(t: Track): String =
            "${t.artist} - ${t.title}".replace(BAD, "_").trim().take(180) + "." + t.extension

        /** Adds " (2)", " (3)"… when a batch has two songs with the same artist and title. */
        fun uniqueName(name: String, used: MutableSet<String>): String {
            if (used.add(name.lowercase())) return name
            val base = name.substringBeforeLast('.')
            val ext = name.substringAfterLast('.', "")
            var n = 2
            while (true) {
                val candidate = "$base ($n)" + if (ext.isNotEmpty()) ".$ext" else ""
                if (used.add(candidate.lowercase())) return candidate
                n++
            }
        }
    }

    fun load(): List<Track> {
        val tables = db.tables()
        if ("item" !in tables) throw IOException("Not a media library (no item table). Tables: $tables")
        val ic = db.columns("item")
        val ec = if ("item_extra" in tables) db.columns("item_extra") else emptySet()
        fun col(name: String): String? = when (name) {
            in ec -> "e.$name"
            in ic -> "i.$name"
            else -> null
        }

        val title = col("title") ?: throw IOException("No title column. item: $ic, item_extra: $ec")
        val location = col("location") ?: throw IOException("No location column. item: $ic, item_extra: $ec")
        val size = col("file_size") ?: "0"
        val track = col("track_number") ?: "0"
        val year = col("year") ?: "0"
        val trackCount = col("track_count") ?: "0"
        val discNumber = col("disc_number") ?: "0"
        val discCount = col("disc_count") ?: "0"
        val mediaType = col("media_type") ?: "NULL"
        val audible = col("is_audible_audio_book") ?: "0"

        val joins = StringBuilder()
        if (ec.isNotEmpty() && "item_pid" in ec) joins.append(" LEFT JOIN item_extra e ON e.item_pid = i.item_pid")

        fun joinIf(table: String, fk: String, key: String, valueCol: String, alias: String): String? {
            if (table !in tables || fk !in ic) return null
            val cols = db.columns(table)
            if (key !in cols || valueCol !in cols) return null
            joins.append(" LEFT JOIN $table $alias ON $alias.$key = i.$fk")
            return "$alias.$valueCol"
        }

        val base = joinIf("base_location", "base_location_id", "base_location_id", "path", "bl") ?: "''"
        val artist = joinIf("item_artist", "item_artist_pid", "item_artist_pid", "item_artist", "ia")
            ?: col("artist") ?: "NULL"
        val albumArtist = joinIf("album_artist", "album_artist_pid", "album_artist_pid", "album_artist", "aa")
            ?: col("album_artist") ?: "NULL"
        val album = joinIf("album", "album_pid", "album_pid", "album", "al") ?: col("album") ?: "NULL"
        val genre = joinIf("genre", "genre_id", "genre_id", "genre", "g") ?: col("genre") ?: "NULL"
        val composer = joinIf("composer", "composer_pid", "composer_pid", "composer", "cp") ?: col("composer") ?: "NULL"

        val sql = "SELECT i.item_pid, $title, COALESCE(NULLIF($artist, ''), $albumArtist), $album, $base, " +
            "$location, $size, $track, $albumArtist, $genre, $composer, $year, $trackCount, $discNumber, $discCount, " +
            "$mediaType, $audible " +
            "FROM item i$joins"

        return db.query(sql).mapNotNull { r ->
            val loc = (r[5] as? String)?.trim().orEmpty()
            if (loc.isEmpty()) return@mapNotNull null // not on the device (e.g. cloud-only)
            val path = listOf((r[4] as? String).orEmpty(), loc)
                .filter { it.isNotEmpty() }.joinToString("/").let { if (it.startsWith("/")) it else "/$it" }
            val ext = path.substringAfterLast('.', "").lowercase()
            if (ext !in AUDIO_EXTENSIONS && ext !in VIDEO_EXTENSIONS) return@mapNotNull null
            Track(
                pid = (r[0] as? Number)?.toLong() ?: 0L,
                title = (r[1] as? String)?.trim()?.ifEmpty { null } ?: loc.substringBeforeLast('.'),
                artist = (r[2] as? String)?.trim()?.ifEmpty { null } ?: UNKNOWN_ARTIST,
                album = (r[3] as? String)?.trim()?.ifEmpty { null } ?: UNKNOWN_ALBUM,
                devicePath = path,
                size = (r[6] as? Number)?.toLong() ?: 0L,
                trackNumber = (r[7] as? Number)?.toInt() ?: 0,
                albumArtist = (r[8] as? String)?.trim()?.ifEmpty { null },
                genre = (r[9] as? String)?.trim()?.ifEmpty { null },
                composer = (r[10] as? String)?.trim()?.ifEmpty { null },
                year = (r[11] as? Number)?.toInt() ?: 0,
                trackCount = (r[12] as? Number)?.toInt() ?: 0,
                discNumber = (r[13] as? Number)?.toInt() ?: 0,
                discCount = (r[14] as? Number)?.toInt() ?: 0,
                kind = kindOf((r[15] as? Number)?.toInt(), ext, ((r[16] as? Number)?.toInt() ?: 0) != 0, r[9] as? String),
            )
        }.sortedWith(
            compareBy<Track>({ it.kind.ordinal }, { it.artist.lowercase() }, { it.album.lowercase() }, { it.trackNumber }, { it.title.lowercase() })
        )
    }
}
