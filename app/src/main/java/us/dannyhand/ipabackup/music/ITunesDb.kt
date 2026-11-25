package us.dannyhand.ipabackup.music

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater

/**
 * iPhone OS 1–2 music library: /iTunes_Control/iTunes/iTunesDB, or iTunesCDB (same thing with everything
 * after the mhbd header zlib-compressed). This is the binary format from click-wheel iPods, documented by
 * the iPodLinux wiki and libgpod (itdb_itunesdb.c). All integers are little-endian.
 *
 * Layout (each record: 4-char tag, u32 header length, u32 total length / child count, …):
 *   mhbd  database header
 *    └ mhsd  data set (type 1 = track list; 2/3 = playlists, 4+ = albums etc., skipped)
 *       └ mhlt  track list header (u32 track count)
 *          └ mhit  one track: numeric fields at fixed offsets, then its mhods
 *             └ mhod  strings: 1 title, 2 location (":iTunes_Control:Music:F00:ABCD.mp3"), 3 album, 4 artist,
 *                     5 genre, 12 composer, 22 album artist. Text is UTF-16LE (or UTF-8 when the encoding field is 2).
 *
 * mhit media type (offset 0xD0, present in newer headers) uses the same iTunes numbering as the .itlp format,
 * so [ItlpLibrary.kindOf] classifies it.
 */
object ITunesDb {
    const val CDB_PATH = "/iTunes_Control/iTunes/iTunesCDB"
    const val DB_PATH = "/iTunes_Control/iTunes/iTunesDB"

    private class Reader(val b: ByteArray) {
        val bb: ByteBuffer = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        fun tag(p: Int) = if (p + 4 <= b.size) String(b, p, 4, Charsets.ISO_8859_1) else ""
        fun u32(p: Int): Long = if (p + 4 <= b.size) bb.getInt(p).toLong() and 0xFFFFFFFFL else 0L
        fun i32(p: Int): Int = u32(p).toInt()
    }

    /** iTunesCDB -> plain iTunesDB bytes (header kept, rest inflated). Plain files are returned as-is. */
    fun decompressIfNeeded(data: ByteArray): ByteArray {
        val r = Reader(data)
        if (r.tag(0) != "mhbd") throw IOException("Not an iTunesDB (no mhbd header)")
        val hdr = r.i32(4)
        if (hdr <= 0 || hdr + 2 > data.size) return data
        // zlib streams start 0x78 0x01/0x5E/0x9C/0xDA; an uncompressed DB has "mhsd" here instead.
        val looksZlib = (data[hdr].toInt() and 0xFF) == 0x78 && r.tag(hdr) != "mhsd"
        if (!looksZlib) return data
        val inf = Inflater()
        try {
            inf.setInput(data, hdr, data.size - hdr)
            val out = ByteArrayOutputStream(data.size * 4)
            out.write(data, 0, hdr)
            val buf = ByteArray(1 shl 16)
            while (!inf.finished()) {
                val n = inf.inflate(buf)
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        } finally {
            inf.end()
        }
    }

    fun parse(raw: ByteArray): List<MusicLibrary.Track> {
        val data = decompressIfNeeded(raw)
        val r = Reader(data)
        val tracks = ArrayList<MusicLibrary.Track>()
        val dbHeader = r.i32(4)
        val sections = r.i32(20) // number of mhsd children
        var p = dbHeader
        for (s in 0 until maxOf(sections, 1)) {
            if (r.tag(p) != "mhsd") break
            val sdHeader = r.i32(p + 4)
            val sdTotal = r.i32(p + 8)
            if (sdTotal <= 0) break
            if (r.i32(p + 12) == 1) parseTrackList(r, p + sdHeader, p + sdTotal, tracks)
            p += sdTotal
        }
        return tracks.sortedWith(compareBy({ it.kind.ordinal }, { it.artist.lowercase() }, { it.album.lowercase() }, { it.trackNumber }, { it.title.lowercase() }))
    }

    private fun parseTrackList(r: Reader, start: Int, end: Int, out: MutableList<MusicLibrary.Track>) {
        if (r.tag(start) != "mhlt") return
        val count = r.i32(start + 8)
        var p = start + r.i32(start + 4)
        repeat(count) {
            if (p >= end || r.tag(p) != "mhit") return
            val header = r.i32(p + 4)
            val total = r.i32(p + 8)
            if (header <= 0 || total < header || p + total > r.b.size) return
            parseTrack(r, p, header, total)?.let { out += it }
            p += total
        }
    }

    private fun parseTrack(r: Reader, p: Int, header: Int, total: Int): MusicLibrary.Track? {
        fun field(off: Int) = if (off + 4 <= header) r.i32(p + off) else 0
        val strings = HashMap<Int, String>()
        var m = p + header
        val end = p + total
        while (m + 24 <= end && r.tag(m) == "mhod") {
            val mTotal = r.i32(m + 8)
            if (mTotal <= 0 || m + mTotal > end) break
            val type = r.i32(m + 12)
            if (type in 1..30 && mTotal >= 40) {
                val encoding = r.i32(m + 24)
                val len = r.i32(m + 28)
                if (len in 0..(mTotal - 40)) {
                    strings[type] = String(r.b, m + 40, len, if (encoding == 2) Charsets.UTF_8 else Charsets.UTF_16LE)
                }
            }
            m += mTotal
        }

        val location = strings[2]?.trim()?.ifEmpty { null } ?: return null
        val path = location.replace(':', '/').let { if (it.startsWith("/")) it else "/$it" }
        val ext = path.substringAfterLast('.', "").lowercase()
        if (ext !in MusicLibrary.AUDIO_EXTENSIONS && ext !in MusicLibrary.VIDEO_EXTENSIONS) return null
        val discs = field(92)
        return MusicLibrary.Track(
            pid = r.u32(p + 16),
            title = strings[1]?.trim()?.ifEmpty { null } ?: path.substringAfterLast('/').substringBeforeLast('.'),
            artist = (strings[4] ?: strings[22])?.trim()?.ifEmpty { null } ?: MusicLibrary.UNKNOWN_ARTIST,
            album = strings[3]?.trim()?.ifEmpty { null } ?: MusicLibrary.UNKNOWN_ALBUM,
            devicePath = path,
            size = field(36).toLong() and 0xFFFFFFFFL,
            trackNumber = field(44),
            albumArtist = strings[22]?.trim()?.ifEmpty { null },
            genre = strings[5]?.trim()?.ifEmpty { null },
            composer = strings[12]?.trim()?.ifEmpty { null },
            year = field(52),
            trackCount = field(48),
            discNumber = discs,
            discCount = field(96),
            kind = ItlpLibrary.kindOf(if (header >= 0xD4) field(0xD0) else 0, ext),
        )
    }
}
