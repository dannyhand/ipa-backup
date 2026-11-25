package us.dannyhand.ipabackup.util

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/**
 * Writes song info into audio files copied off an iPod. iTunes keeps title/artist/album only in the
 * iPod's database, so synced files often arrive with those tags missing. Seen on a real iOS 6 sync: an .m4a's
 * ilst held only the encoder (©too), two sound-check "----" items and the lyrics (©lyr), no title/artist/album.
 * On that file the new tags fit in iTunes' 4 KB free padding, so the file size didn't change; with offsets
 * shifted instead, the decoded audio was still bit-identical (checked with ffmpeg + mutagen).
 *
 * - .m4a/.m4b/.aac (MP4): sets iTunes-style atoms in moov/udta/meta/ilst, keeping other items
 *   (lyrics, sound check…). Uses the `free` padding iTunes leaves next to ilst when it fits;
 *   otherwise shifts the stco/co64 chunk offsets so the audio still lines up.
 * - .mp3: adds an ID3v2.3 tag if the file has none (an existing ID3v2 tag is left alone).
 * - anything else (including DRM .m4p): copied unchanged.
 */
object AudioTagger {

    data class Tags(
        val title: String,
        val artist: String? = null,
        val album: String? = null,
        val albumArtist: String? = null,
        val genre: String? = null,
        val composer: String? = null,
        val year: Int = 0,
        val track: Int = 0,
        val trackCount: Int = 0,
        val disc: Int = 0,
        val discCount: Int = 0,
    )

    enum class Result { MP4_TAGGED, ID3_ADDED, ID3_KEPT, UNCHANGED }

    fun writeTagged(src: File, extension: String, tags: Tags, out: OutputStream): Result =
        when (extension.lowercase()) {
            "m4a", "m4b", "m4r", "aac", "mp4" -> Mp4.write(src, tags, out)
            "mp3" -> Id3.write(src, tags, out)
            else -> { src.inputStream().use { it.copyTo(out) }; Result.UNCHANGED }
        }

    // ------------------------------------------------------------------ MP4

    internal object Mp4 {
        private val L1 = Charsets.ISO_8859_1 // atom names like "©nam" are Latin-1 bytes
        private val PATH_TO_CHUNK_OFFSETS = setOf("moov", "trak", "mdia", "minf", "stbl")
        private const val MAX_MOOV = 64 shl 20

        private class TopAtom(val type: String, val offset: Long, val size: Long)
        private class Box(val type: String, val start: Int, val header: Int, val size: Int) {
            val payload get() = start + header
            val end get() = start + size
        }

        fun write(src: File, tags: Tags, out: OutputStream): Result {
            RandomAccessFile(src, "r").use { raf ->
                val atoms = topLevel(raf)
                val moov = atoms.firstOrNull { it.type == "moov" } ?: throw IOException("No moov atom (not an MP4 file?)")
                if (moov.size > MAX_MOOV) throw IOException("moov too large")
                val moovBytes = ByteArray(moov.size.toInt())
                raf.seek(moov.offset)
                raf.readFully(moovBytes)

                var newMoov = rewriteMoov(moovBytes, tags)
                val delta = newMoov.size - moovBytes.size
                val firstMdat = atoms.firstOrNull { it.type == "mdat" }
                if (delta != 0 && firstMdat != null && moov.offset < firstMdat.offset) {
                    newMoov = newMoov.copyOf()
                    patchChunkOffsets(newMoov, 0, newMoov.size, delta.toLong())
                }

                val buf = ByteArray(1 shl 16)
                for (a in atoms) {
                    if (a === moov) { out.write(newMoov); continue }
                    raf.seek(a.offset)
                    var left = a.size
                    while (left > 0) {
                        val n = raf.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                        if (n < 0) throw IOException("Unexpected end of file")
                        out.write(buf, 0, n)
                        left -= n
                    }
                }
            }
            return Result.MP4_TAGGED
        }

        private fun topLevel(raf: RandomAccessFile): List<TopAtom> {
            val list = ArrayList<TopAtom>()
            val len = raf.length()
            var pos = 0L
            val h = ByteArray(16)
            while (pos + 8 <= len) {
                raf.seek(pos)
                raf.readFully(h, 0, 8)
                var size = ByteBuffer.wrap(h, 0, 4).int.toLong() and 0xFFFFFFFFL
                val type = String(h, 4, 4, L1)
                if (size == 1L) { raf.readFully(h, 8, 8); size = ByteBuffer.wrap(h, 8, 8).long }
                if (size == 0L) size = len - pos
                if (size < 8 || pos + size > len) throw IOException("Bad atom '$type' at $pos")
                list += TopAtom(type, pos, size)
                pos += size
            }
            return list
        }

        private fun children(b: ByteArray, from: Int, to: Int): List<Box> {
            val list = ArrayList<Box>()
            var pos = from
            while (pos + 8 <= to) {
                var size = ByteBuffer.wrap(b, pos, 4).int.toLong() and 0xFFFFFFFFL
                val type = String(b, pos + 4, 4, L1)
                var header = 8
                if (size == 1L) { size = ByteBuffer.wrap(b, pos + 8, 8).long; header = 16 }
                if (size == 0L) size = (to - pos).toLong()
                if (size < header || pos + size > to) throw IOException("Bad atom '$type' inside moov")
                list += Box(type, pos, header, size.toInt())
                pos += size.toInt()
            }
            return list
        }

        private fun box(type: String, vararg parts: ByteArray): ByteArray {
            val size = 8 + parts.sumOf { it.size }
            val o = ByteBuffer.allocate(size).putInt(size).put(type.toByteArray(L1))
            parts.forEach { o.put(it) }
            return o.array()
        }

        private fun ints(vararg v: Int) = ByteBuffer.allocate(4 * v.size).apply { v.forEach { putInt(it) } }.array()
        private fun shorts(vararg v: Int) = ByteBuffer.allocate(2 * v.size).apply { v.forEach { putShort(it.toShort()) } }.array()

        private fun text(type: String, value: String) = box(type, box("data", ints(1, 0), value.toByteArray(Charsets.UTF_8)))
        private fun pair(type: String, n: Int, total: Int, trailing: Boolean) =
            box(type, box("data", ints(0, 0), if (trailing) shorts(0, n, total, 0) else shorts(0, n, total)))

        fun items(t: Tags): LinkedHashMap<String, ByteArray> = LinkedHashMap<String, ByteArray>().apply {
            put("©nam", text("©nam", t.title))
            t.artist?.let { put("©ART", text("©ART", it)) }
            t.album?.let { put("©alb", text("©alb", it)) }
            t.albumArtist?.let { put("aART", text("aART", it)) }
            t.genre?.let { put("©gen", text("©gen", it)) }
            t.composer?.let { put("©wrt", text("©wrt", it)) }
            if (t.year > 0) put("©day", text("©day", t.year.toString()))
            if (t.track > 0) put("trkn", pair("trkn", t.track, t.trackCount, trailing = true))
            if (t.disc > 0) put("disk", pair("disk", t.disc, t.discCount, trailing = false))
        }

        /** iTunes-style metadata handler for a new meta atom. */
        private val HDLR = box("hdlr", ints(0, 0), "mdirappl".toByteArray(L1), ints(0, 0), byteArrayOf(0, 0))

        private fun rewriteMoov(moov: ByteArray, tags: Tags): ByteArray {
            val newItems = items(tags)
            val kids = children(moov, 8, moov.size)
            val udta = kids.firstOrNull { it.type == "udta" }
            val newUdta = rewriteUdta(moov, udta, newItems)
            val parts = ArrayList<ByteArray>()
            for (k in kids) if (k !== udta) parts += moov.copyOfRange(k.start, k.end)
            parts += newUdta
            return box("moov", *parts.toTypedArray())
        }

        private fun rewriteUdta(b: ByteArray, udta: Box?, newItems: Map<String, ByteArray>): ByteArray {
            if (udta == null) return box("udta", newMeta(newItems))
            val kids = children(b, udta.payload, udta.end)
            val meta = kids.firstOrNull { it.type == "meta" }
            val parts = kids.map { k -> if (k === meta) rewriteMeta(b, k, newItems) else b.copyOfRange(k.start, k.end) }
                .toMutableList()
            if (meta == null) parts += newMeta(newItems)
            return box("udta", *parts.toTypedArray())
        }

        private fun newMeta(items: Map<String, ByteArray>) =
            box("meta", ints(0), HDLR, box("ilst", *items.values.toTypedArray()))

        private fun rewriteMeta(b: ByteArray, meta: Box, newItems: Map<String, ByteArray>): ByteArray {
            // iTunes meta is a full box (4 bytes version/flags) — QuickTime-style meta isn't.
            val full = String(b, meta.payload + 4, 4, L1) != "hdlr"
            val first = meta.payload + if (full) 4 else 0
            val kids = children(b, first, meta.end)
            val ilst = kids.firstOrNull { it.type == "ilst" }

            val keep = if (ilst == null) emptyList() else
                children(b, ilst.payload, ilst.end).filter { it.type !in newItems.keys }.map { b.copyOfRange(it.start, it.end) }
            val newIlst = box("ilst", *(keep + newItems.values).toTypedArray())
            val growth = newIlst.size - (ilst?.size ?: 0)

            // Absorb the size change into iTunes' padding so the audio data doesn't move.
            val free = kids.firstOrNull { it.type == "free" || it.type == "skip" }
            val newFreeSize = free?.let { it.size - growth }?.takeIf { it >= 8 }

            val parts = ArrayList<ByteArray>()
            if (full) parts += b.copyOfRange(meta.payload, meta.payload + 4)
            for (k in kids) {
                parts += when {
                    k === ilst -> newIlst
                    k === free && newFreeSize != null -> box(k.type, ByteArray(newFreeSize - 8))
                    else -> b.copyOfRange(k.start, k.end)
                }
            }
            if (ilst == null) parts += newIlst
            if (kids.none { it.type == "hdlr" }) parts.add(if (full) 1 else 0, HDLR)
            return box("meta", *parts.toTypedArray())
        }

        /** Adds [delta] to every chunk offset (stco/co64) under moov/trak/mdia/minf/stbl. */
        private fun patchChunkOffsets(b: ByteArray, from: Int, to: Int, delta: Long) {
            for (k in children(b, from, to)) {
                when (k.type) {
                    in PATH_TO_CHUNK_OFFSETS -> patchChunkOffsets(b, k.payload, k.end, delta)
                    "stco" -> {
                        val bb = ByteBuffer.wrap(b)
                        val n = bb.getInt(k.payload + 4)
                        for (i in 0 until n) {
                            val p = k.payload + 8 + i * 4
                            bb.putInt(p, ((bb.getInt(p).toLong() and 0xFFFFFFFFL) + delta).toInt())
                        }
                    }
                    "co64" -> {
                        val bb = ByteBuffer.wrap(b)
                        val n = bb.getInt(k.payload + 4)
                        for (i in 0 until n) {
                            val p = k.payload + 8 + i * 8
                            bb.putLong(p, bb.getLong(p) + delta)
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ ID3

    internal object Id3 {
        fun write(src: File, tags: Tags, out: OutputStream): Result {
            val head = ByteArray(3)
            val hasTag = src.inputStream().use { it.read(head) == 3 } && String(head, Charsets.ISO_8859_1) == "ID3"
            if (!hasTag) out.write(build(tags))
            src.inputStream().use { it.copyTo(out, 1 shl 16) }
            return if (hasTag) Result.ID3_KEPT else Result.ID3_ADDED
        }

        /** ID3v2.3 with UTF-16 text frames (widest player support). */
        fun build(t: Tags): ByteArray {
            val frames = ByteArrayOutputStream()
            fun frame(id: String, value: String?) {
                if (value.isNullOrEmpty()) return
                val body = byteArrayOf(1) + byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + value.toByteArray(Charsets.UTF_16LE)
                frames.write(id.toByteArray(Charsets.ISO_8859_1))
                frames.write(ByteBuffer.allocate(4).putInt(body.size).array())
                frames.write(byteArrayOf(0, 0))
                frames.write(body)
            }
            fun numPair(n: Int, total: Int) = if (n <= 0) null else if (total > 0) "$n/$total" else "$n"
            frame("TIT2", t.title)
            frame("TPE1", t.artist)
            frame("TALB", t.album)
            frame("TPE2", t.albumArtist)
            frame("TCON", t.genre)
            frame("TCOM", t.composer)
            frame("TYER", if (t.year > 0) t.year.toString() else null)
            frame("TRCK", numPair(t.track, t.trackCount))
            frame("TPOS", numPair(t.disc, t.discCount))
            val body = frames.toByteArray()
            val n = body.size
            val syncsafe = byteArrayOf(((n shr 21) and 0x7F).toByte(), ((n shr 14) and 0x7F).toByte(),
                ((n shr 7) and 0x7F).toByte(), (n and 0x7F).toByte())
            return "ID3".toByteArray() + byteArrayOf(3, 0, 0) + syncsafe + body
        }
    }
}
