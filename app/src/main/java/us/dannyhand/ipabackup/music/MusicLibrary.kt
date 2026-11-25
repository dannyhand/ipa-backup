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
        OTHER("Other audio", "Other Audio"),
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

        /** Per-item yes/no columns some iOS versions keep, in priority order. */
        val FLAG_KINDS = linkedMapOf(
            "is_podcast" to Kind.PODCAST,
            "is_audio_book" to Kind.AUDIOBOOK,
            "is_itunes_u" to Kind.ITUNES_U,
            "is_music_video" to Kind.VIDEO,
            "is_movie" to Kind.VIDEO,
            "is_tv_show" to Kind.VIDEO,
            "is_home_video" to Kind.VIDEO,
            "is_ringtone" to Kind.RINGTONE,
            "is_tone" to Kind.RINGTONE,
            "is_song" to Kind.MUSIC,
        )

        /**
         * MediaLibrary's media_type uses MPMediaType bits: 1 music, 2 podcast, 4 audiobook, 8 iTunes U,
         * 0x100 movie, 0x200 TV show, 0x400 video podcast, 0x800 music video, 0x1000 iTunes U video,
         * 0x2000 home video. Extensions and flags fill in when the type is missing.
         */
        fun kindOf(mediaType: Int?, ext: String, audibleFlag: Boolean = false, genre: String? = null, musicType: Int? = null): Kind {
            val mt = mediaType ?: 0
            // iOS 6 numbers items differently from MPMediaType (songs are 8, podcasts 4, confirmed on an
            // iPod touch 4G). When the library's music type isn't 1, use that numbering and don't guess the rest.
            if (musicType != null && musicType != 1 && mediaType != null) {
                return when {
                    genre.equals("Podcast", ignoreCase = true) || mt == 4 -> Kind.PODCAST
                    audibleFlag || ext == "m4b" || ext == "aa" -> Kind.AUDIOBOOK
                    ext in VIDEO_EXTENSIONS -> Kind.VIDEO
                    else -> Kind.OTHER
                }
            }
            return when {
                genre.equals("Podcast", ignoreCase = true) -> Kind.PODCAST
                mt and 0x400 != 0 -> Kind.PODCAST
                mt and 0x1000 != 0 -> Kind.ITUNES_U
                mt and 0x2B00 != 0 -> Kind.VIDEO
                mt and 0x2 != 0 -> Kind.PODCAST
                mt and 0x4 != 0 || audibleFlag || ext == "m4b" || ext == "aa" -> Kind.AUDIOBOOK
                mt and 0x8 != 0 -> Kind.ITUNES_U
                mt == 0 && ext in VIDEO_EXTENSIONS -> Kind.VIDEO
                else -> Kind.MUSIC
            }
        }

        /** File name for a saved copy, by kind: music "Artist - Title", podcasts "Show - Episode", etc. */
        fun saveName(t: Track): String {
            val knownArtist = t.artist.takeIf { it != UNKNOWN_ARTIST }
            val knownAlbum = t.album.takeIf { it != UNKNOWN_ALBUM }
            val base = when (t.kind) {
                Kind.MUSIC, Kind.AUDIOBOOK, Kind.OTHER -> "${t.artist} - ${t.title}"
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

    /** Summary of how items were classified, for the log. */
    var diagnostics: String = ""
        private set

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
        val flagCols = FLAG_KINDS.keys.filter { it in ic || it in ec }
        val flagSel = flagCols.joinToString("") { ", " + col(it) }

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
            "$mediaType, $audible$flagSel " +
            "FROM item i$joins"

        class Raw(val track: Track, val mediaType: Int?, val audible: Boolean, val flags: List<Boolean>)
        val raws = db.query(sql).mapNotNull { r ->
            val loc = (r[5] as? String)?.trim().orEmpty()
            if (loc.isEmpty()) return@mapNotNull null // not on the device (e.g. cloud-only)
            val path = listOf((r[4] as? String).orEmpty(), loc)
                .filter { it.isNotEmpty() }.joinToString("/").let { if (it.startsWith("/")) it else "/$it" }
            val ext = path.substringAfterLast('.', "").lowercase()
            if (ext !in AUDIO_EXTENSIONS && ext !in VIDEO_EXTENSIONS) return@mapNotNull null
            Raw(
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
                ),
                mediaType = (r[15] as? Number)?.toInt(),
                audible = ((r[16] as? Number)?.toInt() ?: 0) != 0,
                flags = flagCols.indices.map { i -> ((r[17 + i] as? Number)?.toInt() ?: 0) != 0 },
            )
        }

        // The stored media_type numbering isn't documented for every iOS version. Whatever value most
        // ordinary audio tracks share is taken to be "music" (a library is overwhelmingly songs).
        val musicType = raws.filter {
            it.mediaType != null && it.track.extension in setOf("mp3", "m4a", "m4p", "aac", "wav", "aif", "aiff") &&
                !it.track.genre.equals("Podcast", true) && !it.audible
        }.groupingBy { it.mediaType!! }.eachCount().maxByOrNull { it.value }?.key

        diagnostics = buildString {
            append("media_type values: ")
            append(raws.groupingBy { it.mediaType }.eachCount().entries.sortedByDescending { it.value }
                .joinToString(", ") { "${it.key}×${it.value}" })
            if (musicType != null) append(" (music = $musicType)")
            append(if (flagCols.isEmpty()) "; no is_* flags" else "; flags: " + flagCols.joinToString(", "))
        }

        return raws.map { raw ->
            val ext = raw.track.extension
            val byFlag = flagCols.indices.firstOrNull { raw.flags[it] }?.let { FLAG_KINDS[flagCols[it]] }
            val kind = when {
                byFlag != null -> byFlag
                raw.mediaType != null && raw.mediaType == musicType && ext !in VIDEO_EXTENSIONS -> Kind.MUSIC
                else -> kindOf(raw.mediaType, ext, raw.audible, raw.track.genre, musicType)
            }
            raw.track.copy(kind = kind)
        }.sortedWith(
            compareBy<Track>({ it.kind.ordinal }, { it.artist.lowercase() }, { it.album.lowercase() }, { it.trackNumber }, { it.title.lowercase() })
        )
    }
}
