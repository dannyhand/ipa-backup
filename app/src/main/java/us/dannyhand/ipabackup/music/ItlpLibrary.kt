package us.dannyhand.ipabackup.music

import java.io.IOException

/**
 * iPhone OS 3 / iOS 4 music library: /iTunes_Control/iTunes/iTunes Library.itlp/, a folder of SQLite
 * files (layout as written by libgpod's itdb_sqlite.c):
 *   Library.itdb   - item (pid, title, artist, album, album_artist, composer, track/disc numbers, year,
 *                    genre_id -> genre_map, media_kind)
 *   Locations.itdb - location (item_pid, base_location_id, location, file_size) + base_location (id, path)
 *
 * The caller opens Library.itdb and ATTACHes Locations.itdb as [locSchema] before calling [load].
 * media_kind uses iTunes' numbering: 1 music, 2 movie, 4 podcast, 8 audiobook, 0x20 music video,
 * 0x40 TV show, 0x4000 ringtone, 0x200000 iTunes U.
 */
class ItlpLibrary(private val db: SqlDb, private val locSchema: String = "loc") {

    companion object {
        const val DIR = "/iTunes_Control/iTunes/iTunes Library.itlp"
        const val LIBRARY = "$DIR/Library.itdb"
        const val LOCATIONS = "$DIR/Locations.itdb"

        fun kindOf(mediaKind: Int?, ext: String): MusicLibrary.Kind {
            val k = mediaKind ?: 0
            return when {
                k and 0x4 != 0 -> MusicLibrary.Kind.PODCAST        // includes video podcasts (0x6)
                k and 0x8 != 0 -> MusicLibrary.Kind.AUDIOBOOK
                k and 0x200000 != 0 -> MusicLibrary.Kind.ITUNES_U
                k and 0x4000 != 0 -> MusicLibrary.Kind.RINGTONE
                k and (0x2 or 0x20 or 0x40) != 0 -> MusicLibrary.Kind.VIDEO
                k and 0x1 != 0 -> MusicLibrary.Kind.MUSIC
                k == 0 && ext in MusicLibrary.VIDEO_EXTENSIONS -> MusicLibrary.Kind.VIDEO
                k == 0 && (ext == "m4b" || ext == "aa") -> MusicLibrary.Kind.AUDIOBOOK
                k == 0 -> MusicLibrary.Kind.MUSIC
                else -> MusicLibrary.Kind.OTHER
            }
        }
    }

    fun load(): List<MusicLibrary.Track> {
        if ("item" !in db.tables()) throw IOException("Library.itdb has no item table")
        val ic = db.columns("item")
        val lc = db.columns("$locSchema.location")
        if ("item_pid" !in lc || "location" !in lc) throw IOException("Locations.itdb has no usable location table ($lc)")
        val bc = db.columns("$locSchema.base_location")
        fun i(n: String) = if (n in ic) "i.$n" else "NULL"

        val joins = StringBuilder(" JOIN $locSchema.location l ON l.item_pid = i.pid")
        val base = if ("id" in bc && "path" in bc) {
            joins.append(" LEFT JOIN $locSchema.base_location bl ON bl.id = l.base_location_id"); "bl.path"
        } else "''"
        val genre = if ("genre_map" in db.tables() && "genre_id" in ic && db.columns("genre_map").containsAll(listOf("id", "genre"))) {
            joins.append(" LEFT JOIN genre_map g ON g.id = i.genre_id"); "g.genre"
        } else "NULL"
        val size = if ("file_size" in lc) "l.file_size" else "0"

        val sql = "SELECT i.pid, ${i("title")}, COALESCE(NULLIF(${i("artist")}, ''), ${i("album_artist")}), ${i("album")}, " +
            "$base, l.location, $size, ${i("track_number")}, ${i("album_artist")}, $genre, ${i("composer")}, ${i("year")}, " +
            "${i("track_count")}, ${i("disc_number")}, ${i("disc_count")}, ${i("media_kind")} FROM item i$joins"

        return db.query(sql).mapNotNull { r ->
            val loc = (r[5] as? String)?.trim().orEmpty()
            if (loc.isEmpty()) return@mapNotNull null
            val path = listOf((r[4] as? String).orEmpty().trim('/'), loc.trimStart('/'))
                .filter { it.isNotEmpty() }.joinToString("/").let { "/$it" }
            val ext = path.substringAfterLast('.', "").lowercase()
            if (ext !in MusicLibrary.AUDIO_EXTENSIONS && ext !in MusicLibrary.VIDEO_EXTENSIONS) return@mapNotNull null
            fun s(n: Int) = (r[n] as? String)?.trim()?.ifEmpty { null }
            fun n(k: Int) = (r[k] as? Number)?.toInt() ?: 0
            MusicLibrary.Track(
                pid = (r[0] as? Number)?.toLong() ?: 0L,
                title = s(1) ?: loc.substringAfterLast('/').substringBeforeLast('.'),
                artist = s(2) ?: MusicLibrary.UNKNOWN_ARTIST,
                album = s(3) ?: MusicLibrary.UNKNOWN_ALBUM,
                devicePath = path,
                size = (r[6] as? Number)?.toLong() ?: 0L,
                trackNumber = n(7),
                albumArtist = s(8),
                genre = s(9),
                composer = s(10),
                year = n(11),
                trackCount = n(12),
                discNumber = n(13),
                discCount = n(14),
                kind = kindOf((r[15] as? Number)?.toInt(), ext),
            )
        }.sortedWith(compareBy({ it.kind.ordinal }, { it.artist.lowercase() }, { it.album.lowercase() }, { it.trackNumber }, { it.title.lowercase() }))
    }
}
