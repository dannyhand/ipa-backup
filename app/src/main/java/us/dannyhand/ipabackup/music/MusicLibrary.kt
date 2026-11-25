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

    data class Track(
        val pid: Long,
        val title: String,
        val artist: String,
        val album: String,
        /** Path relative to the AFC media root, e.g. /iTunes_Control/Music/F12/ABCD.mp3 */
        val devicePath: String,
        val size: Long,
        val trackNumber: Int,
    ) {
        val extension: String get() = devicePath.substringAfterLast('.', "").lowercase()
        val isDrm: Boolean get() = extension == "m4p"
    }

    companion object {
        const val DB_PATH = "/iTunes_Control/iTunes/MediaLibrary.sqlitedb"
        const val UNKNOWN_ARTIST = "Unknown Artist"
        const val UNKNOWN_ALBUM = "Unknown Album"
        val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "m4p", "m4b", "aac", "wav", "aif", "aiff", "caf", "alac")
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

        val sql = "SELECT i.item_pid, $title, COALESCE(NULLIF($artist, ''), $albumArtist), $album, $base, " +
            "$location, $size, $track FROM item i$joins"

        return db.query(sql).mapNotNull { r ->
            val loc = (r[5] as? String)?.trim().orEmpty()
            if (loc.isEmpty()) return@mapNotNull null // not on the device (e.g. cloud-only)
            val path = listOf((r[4] as? String).orEmpty(), loc)
                .filter { it.isNotEmpty() }.joinToString("/").let { if (it.startsWith("/")) it else "/$it" }
            if (path.substringAfterLast('.', "").lowercase() !in AUDIO_EXTENSIONS) return@mapNotNull null
            Track(
                pid = (r[0] as? Number)?.toLong() ?: 0L,
                title = (r[1] as? String)?.trim()?.ifEmpty { null } ?: loc.substringBeforeLast('.'),
                artist = (r[2] as? String)?.trim()?.ifEmpty { null } ?: UNKNOWN_ARTIST,
                album = (r[3] as? String)?.trim()?.ifEmpty { null } ?: UNKNOWN_ALBUM,
                devicePath = path,
                size = (r[6] as? Number)?.toLong() ?: 0L,
                trackNumber = (r[7] as? Number)?.toInt() ?: 0,
            )
        }.sortedWith(
            compareBy<Track>({ it.artist.lowercase() }, { it.album.lowercase() }, { it.trackNumber }, { it.title.lowercase() })
        )
    }
}
