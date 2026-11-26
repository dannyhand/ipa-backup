package us.dannyhand.ipabackup.util

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reads what the music library needs to know about a song file: tags, duration and audio format.
 *
 * MP3: tags from ID3v2.2/2.3/2.4 at the start (ID3v1 at the end as a fallback). Duration from the
 * Xing/Info or VBRI header that encoders put in the first frame (exact for VBR files), otherwise
 * estimated from the first frame's bitrate (exact for constant-bitrate files).
 *
 * M4A/MP4/M4V/MOV: tags from moov/udta/meta/ilst (©nam, ©ART, ©alb, aART, ©wrt, ©gen, ©day, trkn, disk,
 * covr, plus iTunes' stik media kind and the TV tags tvsh/tvsn/tves), duration from mvhd, codecs from
 * the tracks' stsd entries (mp4a = AAC, alac = Apple Lossless, drms = iTunes Store DRM; avc1 = H.264,
 * mp4v = MPEG-4 video), and for H.264 the profile and level from its avcC box.
 */
object AudioInfo {
    enum class Format(val kind: String, val ext: String) {
        MP3("MPEG audio file", "mp3"),
        AAC("AAC audio file", "m4a"),
        ALAC("Apple Lossless audio file", "m4a"),
        PROTECTED_AAC("Protected AAC audio file", "m4p"),
        H264_VIDEO("MPEG-4 video file", "m4v"),
        MPEG4_VIDEO("MPEG-4 video file", "m4v"),
        OTHER_VIDEO("Video file", "mp4"),
        PROTECTED_VIDEO("Protected MPEG-4 video file", "m4v"),
        ;
        val isVideo get() = this == H264_VIDEO || this == MPEG4_VIDEO || this == OTHER_VIDEO || this == PROTECTED_VIDEO
    }

    /**
     * A video track: codec (avc1, mp4v…), size, and for H.264 the profile (66 Baseline, 77 Main,
     * 100 High) and level ×10 (31 = 3.1).
     */
    data class Video(val codec: String, val width: Int, val height: Int, val profile: Int = 0, val level: Int = 0) {
        val profileName get() = when (profile) { 66 -> "Baseline"; 77 -> "Main"; 88 -> "Extended"; 100 -> "High"; 0 -> ""; else -> "profile $profile" }
    }

    data class Info(
        val format: Format,
        val title: String?,
        val artist: String?,
        val album: String?,
        val albumArtist: String?,
        val composer: String?,
        val genre: String?,
        val year: Int,
        val track: Int,
        val trackCount: Int,
        val disc: Int,
        val discCount: Int,
        val durationMs: Long,
        val bitrateKbps: Int,
        val sampleRate: Int,
        val cover: ByteArray?,
        /** iTunes media kind (stik): 1 music, 2 audiobook, 6 music video, 9 movie, 10 TV show, 14 ringtone, 21 podcast. */
        val stik: Int? = null,
        val show: String? = null,
        val season: Int = 0,
        val episode: Int = 0,
        val video: Video? = null,
    )

    /** Null if the file isn't an MP3 or M4A/MP4 audio file. */
    fun read(file: File): Info? = RandomAccessFile(file, "r").use { raf ->
        val head = ByteArray(minOf(12L, raf.length()).toInt()).also { raf.readFully(it) }
        when {
            head.size >= 8 && String(head, 4, 4, Charsets.ISO_8859_1) == "ftyp" -> Mp4.read(raf)
            else -> Mp3.read(raf)
        }
    }

    // ------------------------------------------------------------------ MP4

    private object Mp4 {
        private class Box(val type: String, val start: Int, val header: Int, val size: Int) {
            val body get() = start + header
            val end get() = start + size
        }

        private fun children(b: ByteArray, from: Int, to: Int): List<Box> {
            val out = ArrayList<Box>()
            var p = from
            while (p + 8 <= to) {
                var size = ByteBuffer.wrap(b, p, 4).int.toLong() and 0xFFFFFFFFL
                var header = 8
                if (size == 1L && p + 16 <= to) { size = ByteBuffer.wrap(b, p + 8, 8).long; header = 16 }
                if (size == 0L) size = (to - p).toLong()
                if (size < header || p + size > to) break
                out += Box(String(b, p + 4, 4, Charsets.ISO_8859_1), p, header, size.toInt())
                p += size.toInt()
            }
            return out
        }

        private fun find(b: ByteArray, parent: Box, type: String) = children(b, parent.body, parent.end).firstOrNull { it.type == type }

        fun read(raf: RandomAccessFile): Info? {
            // Find moov among the top-level boxes without reading mdat (the audio) into memory.
            var pos = 0L
            var moov: ByteArray? = null
            while (pos + 8 <= raf.length()) {
                raf.seek(pos)
                var size = raf.readInt().toLong() and 0xFFFFFFFFL
                val type = ByteArray(4).also { raf.readFully(it) }.toString(Charsets.ISO_8859_1)
                if (size == 1L) size = raf.readLong()
                if (size == 0L) size = raf.length() - pos
                if (size < 8) return null
                if (type == "moov") {
                    if (size > 64L shl 20) return null
                    moov = ByteArray(size.toInt()).also { raf.seek(pos); raf.readFully(it) }
                    break
                }
                pos += size
            }
            val b = moov ?: return null
            val root = Box("moov", 0, 8, b.size)

            var durationMs = 0L
            find(b, root, "mvhd")?.let { m ->
                val v = b[m.body].toInt()
                val bb = ByteBuffer.wrap(b)
                if (v == 1) {
                    val scale = bb.getInt(m.body + 20).toLong()
                    if (scale > 0) durationMs = bb.getLong(m.body + 24) * 1000 / scale
                } else {
                    val scale = bb.getInt(m.body + 12).toLong()
                    if (scale > 0) durationMs = (bb.getInt(m.body + 16).toLong() and 0xFFFFFFFFL) * 1000 / scale
                }
            }

            // The tracks' sample descriptions say the codecs.
            var codec: String? = null
            var sampleRate = 0
            var video: Video? = null
            for (trak in children(b, root.body, root.end).filter { it.type == "trak" }) {
                val mdia = find(b, trak, "mdia") ?: continue
                val hdlr = find(b, mdia, "hdlr") ?: continue
                val handler = String(b, hdlr.body + 8, 4, Charsets.ISO_8859_1)
                val stsd = find(b, mdia, "minf")?.let { find(b, it, "stbl") }?.let { find(b, it, "stsd") } ?: continue
                val entry = children(b, stsd.body + 8, stsd.end).firstOrNull() ?: continue
                if (handler == "soun" && codec == null) {
                    codec = entry.type
                    // AudioSampleEntry: 8 reserved/index, 8 reserved, 2 channels, 2 size, 4 reserved, 4 rate (16.16)
                    if (entry.body + 28 <= entry.end) sampleRate = (ByteBuffer.wrap(b).getInt(entry.body + 24) ushr 16)
                } else if (handler == "vide" && video == null && entry.body + 28 <= entry.end) {
                    // VisualSampleEntry: 8 reserved/index, 16 predefined/reserved, then 2 width, 2 height, … 78 bytes in all
                    val bb = ByteBuffer.wrap(b)
                    val w = bb.getShort(entry.body + 24).toInt() and 0xFFFF
                    val h = bb.getShort(entry.body + 26).toInt() and 0xFFFF
                    var profile = 0
                    var level = 0
                    if (entry.body + 78 <= entry.end) children(b, entry.body + 78, entry.end).firstOrNull { it.type == "avcC" }?.let { c ->
                        if (c.body + 4 <= c.end) { profile = b[c.body + 1].toInt() and 0xFF; level = b[c.body + 3].toInt() and 0xFF }
                    }
                    // drmi = protected video (iTunes Store); the real codec is inside, but it won't play elsewhere anyway.
                    video = Video(entry.type, w, h, profile, level)
                }
            }
            val format = when {
                video != null -> when (video.codec) {
                    "avc1", "avc3" -> Format.H264_VIDEO
                    "mp4v" -> Format.MPEG4_VIDEO
                    "drmi" -> Format.PROTECTED_VIDEO
                    else -> Format.OTHER_VIDEO
                }
                codec == null || codec == "mp4a" -> Format.AAC
                codec == "alac" -> Format.ALAC
                codec == "drms" -> Format.PROTECTED_AAC
                else -> return null
            }

            val tags = HashMap<String, ByteArray>()
            val ilst = find(b, root, "udta")?.let { find(b, it, "meta") }
                ?.let { meta -> children(b, meta.body + 4, meta.end).firstOrNull { it.type == "ilst" } }
            if (ilst != null) for (item in children(b, ilst.body, ilst.end)) {
                val data = children(b, item.body, item.end).firstOrNull { it.type == "data" } ?: continue
                // data: 4 bytes type/flags, 4 bytes locale, then the value
                if (data.body + 8 <= data.end) tags[item.type] = b.copyOfRange(data.body + 8, data.end)
            }
            fun text(k: String) = tags[k]?.toString(Charsets.UTF_8)?.trim()?.ifEmpty { null }
            fun pair(k: String): Pair<Int, Int> = tags[k]?.takeIf { it.size >= 6 }?.let {
                val bb = ByteBuffer.wrap(it)
                (bb.getShort(2).toInt() and 0xFFFF) to (bb.getShort(4).toInt() and 0xFFFF)
            } ?: (0 to 0)
            val (track, trackCount) = pair("trkn")
            val (disc, discCount) = pair("disk")
            val genre = text("©gen") ?: tags["gnre"]?.takeIf { it.size >= 2 }?.let {
                Id3Genres.name((ByteBuffer.wrap(it).getShort(0).toInt() and 0xFFFF) - 1)
            }
            val sizeBits = raf.length() * 8
            return Info(
                format = format,
                title = text("©nam"),
                artist = text("©ART"),
                album = text("©alb"),
                albumArtist = text("aART"),
                composer = text("©wrt"),
                genre = genre,
                year = text("©day")?.take(4)?.toIntOrNull() ?: 0,
                track = track, trackCount = trackCount, disc = disc, discCount = discCount,
                durationMs = durationMs,
                bitrateKbps = if (durationMs > 0) (sizeBits / durationMs).toInt() else 0,
                sampleRate = sampleRate,
                cover = tags["covr"],
                stik = tags["stik"]?.takeIf { it.isNotEmpty() }?.let { it[0].toInt() and 0xFF },
                show = text("tvsh"),
                season = tags["tvsn"]?.takeIf { it.size >= 4 }?.let { ByteBuffer.wrap(it).int } ?: 0,
                episode = tags["tves"]?.takeIf { it.size >= 4 }?.let { ByteBuffer.wrap(it).int } ?: 0,
                video = video,
            )
        }
    }

    // ------------------------------------------------------------------ MP3

    private object Mp3 {
        private val BITRATES_V1_L3 = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0)
        private val BITRATES_V2_L3 = intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0)
        private val RATES = arrayOf(intArrayOf(44100, 48000, 32000), intArrayOf(22050, 24000, 16000), intArrayOf(11025, 12000, 8000))

        fun read(raf: RandomAccessFile): Info? {
            val len = raf.length()
            val tags = HashMap<String, String>()
            var cover: ByteArray? = null
            var audioStart = 0L
            // ID3v2
            if (len > 10) {
                val h = ByteArray(10).also { raf.seek(0); raf.readFully(it) }
                if (h[0] == 'I'.code.toByte() && h[1] == 'D'.code.toByte() && h[2] == '3'.code.toByte()) {
                    val ver = h[3].toInt()
                    val flags = h[5].toInt()
                    val size = syncsafe(h, 6)
                    audioStart = 10L + size + if (flags and 0x10 != 0) 10 else 0
                    if (size in 1..(32 shl 20) && 10L + size <= len) {
                        var t = ByteArray(size).also { raf.readFully(it) }
                        if (flags and 0x80 != 0 && ver < 4) t = unsync(t) // whole-tag unsynchronisation (v2.2/2.3)
                        parseId3(t, ver, flags, tags) { cover = it }
                    }
                }
            }
            // ID3v1 fallback
            var end = len
            if (len >= 128) {
                val v1 = ByteArray(128).also { raf.seek(len - 128); raf.readFully(it) }
                if (String(v1, 0, 3, Charsets.ISO_8859_1) == "TAG") {
                    end = len - 128
                    fun f(o: Int, n: Int) = String(v1, o, n, Charsets.ISO_8859_1).substringBefore('\u0000').trim().ifEmpty { null }
                    tags.putIfAbsent("TIT2", f(3, 30) ?: "")
                    f(33, 30)?.let { tags.putIfAbsent("TPE1", it) }
                    f(63, 30)?.let { tags.putIfAbsent("TALB", it) }
                    f(93, 4)?.let { tags.putIfAbsent("TYER", it) }
                    if (v1[125].toInt() == 0 && v1[126].toInt() != 0) tags.putIfAbsent("TRCK", (v1[126].toInt() and 0xFF).toString())
                    Id3Genres.name(v1[127].toInt() and 0xFF)?.let { tags.putIfAbsent("TCON", it) }
                    if (tags["TIT2"] == "") tags.remove("TIT2")
                }
            }
            // First MPEG frame (search a little, in case of padding or junk after the tag).
            val scanLen = minOf(end - audioStart, 64L * 1024).toInt().coerceAtLeast(0)
            val buf = ByteArray(scanLen).also { raf.seek(audioStart); raf.readFully(it) }
            var i = 0
            var frame: Frame? = null
            while (i + 4 <= buf.size) {
                if (buf[i] == 0xFF.toByte() && (buf[i + 1].toInt() and 0xE0) == 0xE0) {
                    frame = parseHeader(buf, i)
                    // Make sure the next frame header is where this one says, to avoid false syncs.
                    if (frame != null && (i + frame.length + 2 > buf.size ||
                            (buf[i + frame.length] == 0xFF.toByte() && (buf[i + frame.length + 1].toInt() and 0xE0) == 0xE0))) break
                    frame = null
                }
                i++
            }
            val f = frame ?: return null
            val frameStart = audioStart + i
            var durationMs: Long
            val xingOff = i + 4 + f.sideInfo
            val xingFrames = if (xingOff + 12 <= buf.size) {
                val tag = String(buf, xingOff, 4, Charsets.ISO_8859_1)
                if ((tag == "Xing" || tag == "Info") && (buf[xingOff + 7].toInt() and 1) != 0) ByteBuffer.wrap(buf).getInt(xingOff + 8).toLong() and 0xFFFFFFFFL else null
            } else null
            val vbriOff = i + 36
            val vbriFrames = if (vbriOff + 18 <= buf.size && String(buf, vbriOff, 4, Charsets.ISO_8859_1) == "VBRI")
                ByteBuffer.wrap(buf).getInt(vbriOff + 14).toLong() and 0xFFFFFFFFL else null
            val frames = xingFrames ?: vbriFrames
            durationMs = if (frames != null && frames > 0) frames * f.samples * 1000 / f.sampleRate
                else (end - frameStart) * 8 / f.bitrateKbps
            val bitrate = if (frames != null && durationMs > 0) ((end - frameStart) * 8 / durationMs).toInt() else f.bitrateKbps

            fun num(s: String?) = s?.substringBefore('/')?.trim()?.toIntOrNull() ?: 0
            fun total(s: String?) = s?.substringAfter('/', "")?.trim()?.toIntOrNull() ?: 0
            return Info(
                format = Format.MP3,
                title = tags["TIT2"], artist = tags["TPE1"], album = tags["TALB"], albumArtist = tags["TPE2"],
                composer = tags["TCOM"], genre = tags["TCON"]?.let(::genre),
                year = (tags["TDRC"] ?: tags["TYER"])?.take(4)?.toIntOrNull() ?: 0,
                track = num(tags["TRCK"]), trackCount = total(tags["TRCK"]),
                disc = num(tags["TPOS"]), discCount = total(tags["TPOS"]),
                durationMs = durationMs, bitrateKbps = bitrate, sampleRate = f.sampleRate, cover = cover,
            )
        }

        private class Frame(val bitrateKbps: Int, val sampleRate: Int, val samples: Int, val length: Int, val sideInfo: Int)

        private fun parseHeader(b: ByteArray, o: Int): Frame? {
            val h = ByteBuffer.wrap(b, o, 4).int
            val verBits = (h ushr 19) and 3 // 3 = MPEG1, 2 = MPEG2, 0 = MPEG2.5
            val layer = (h ushr 17) and 3   // 1 = Layer III
            if (verBits == 1 || layer != 1) return null
            val br = (h ushr 12) and 15
            val sr = (h ushr 10) and 3
            if (br == 0 || br == 15 || sr == 3) return null
            val mpeg1 = verBits == 3
            val bitrate = (if (mpeg1) BITRATES_V1_L3 else BITRATES_V2_L3)[br]
            val rate = RATES[if (mpeg1) 0 else if (verBits == 2) 1 else 2][sr]
            val pad = (h ushr 9) and 1
            val mono = ((h ushr 6) and 3) == 3
            val samples = if (mpeg1) 1152 else 576
            val length = samples / 8 * bitrate * 1000 / rate + pad
            val side = if (mpeg1) (if (mono) 17 else 32) else (if (mono) 9 else 17)
            return Frame(bitrate, rate, samples, length, side)
        }

        private fun syncsafe(b: ByteArray, o: Int) =
            ((b[o].toInt() and 0x7F) shl 21) or ((b[o + 1].toInt() and 0x7F) shl 14) or ((b[o + 2].toInt() and 0x7F) shl 7) or (b[o + 3].toInt() and 0x7F)

        /** Undo ID3 unsynchronisation (FF 00 → FF). */
        private fun unsync(b: ByteArray): ByteArray {
            val out = java.io.ByteArrayOutputStream(b.size)
            var i = 0
            while (i < b.size) {
                out.write(b[i].toInt())
                if (b[i] == 0xFF.toByte() && i + 1 < b.size && b[i + 1].toInt() == 0) i++
                i++
            }
            return out.toByteArray()
        }

        /** ID3v2.2 uses 3-letter frame IDs; map the ones we use to their v2.3 names. */
        private val V22 = mapOf("TT2" to "TIT2", "TP1" to "TPE1", "TP2" to "TPE2", "TAL" to "TALB", "TCM" to "TCOM",
            "TCO" to "TCON", "TYE" to "TYER", "TRK" to "TRCK", "TPA" to "TPOS", "PIC" to "APIC")

        private fun parseId3(t: ByteArray, ver: Int, flags: Int, out: MutableMap<String, String>, onCover: (ByteArray) -> Unit) {
            var p = 0
            if (flags and 0x40 != 0 && ver >= 3) { // extended header
                p = if (ver == 4) syncsafe(t, 0) else 4 + ByteBuffer.wrap(t, 0, 4).int
            }
            val idLen = if (ver == 2) 3 else 4
            val hdrLen = if (ver == 2) 6 else 10
            while (p + hdrLen <= t.size) {
                if (t[p].toInt() == 0) break // padding
                val id = String(t, p, idLen, Charsets.ISO_8859_1)
                val size = when (ver) {
                    2 -> ((t[p + 3].toInt() and 0xFF) shl 16) or ((t[p + 4].toInt() and 0xFF) shl 8) or (t[p + 5].toInt() and 0xFF)
                    3 -> ByteBuffer.wrap(t, p + 4, 4).int
                    else -> syncsafe(t, p + 4)
                }
                val start = p + hdrLen
                if (size <= 0 || start + size > t.size) break
                var body = t.copyOfRange(start, start + size)
                if (ver == 4 && (t[p + 9].toInt() and 0x02) != 0) body = unsync(body) // per-frame unsync (v2.4)
                val key = if (ver == 2) V22[id] ?: id else id
                if (key.startsWith("T") && key != "TXXX" && body.isNotEmpty()) {
                    out.putIfAbsent(key, decode(body[0].toInt(), body, 1, body.size).substringBefore('\u0000').trim())
                } else if (key == "APIC" && body.size > 4) {
                    pictureData(body, ver)?.let(onCover)
                }
                p = start + size
            }
            out.entries.removeIf { it.value.isEmpty() }
        }

        private fun decode(enc: Int, b: ByteArray, from: Int, to: Int): String = when (enc) {
            1 -> String(b, from, to - from, Charsets.UTF_16)   // with BOM
            2 -> String(b, from, to - from, Charsets.UTF_16BE)
            3 -> String(b, from, to - from, Charsets.UTF_8)
            else -> String(b, from, to - from, Charsets.ISO_8859_1)
        }

        /** APIC: encoding, MIME (or 3-letter format in v2.2), picture type, description, then the image. */
        private fun pictureData(b: ByteArray, ver: Int): ByteArray? {
            val enc = b[0].toInt()
            var p = 1
            if (ver == 2) p += 3 else { while (p < b.size && b[p].toInt() != 0) p++; p++ }
            p++ // picture type
            if (enc == 1 || enc == 2) {
                while (p + 1 < b.size && !(b[p].toInt() == 0 && b[p + 1].toInt() == 0)) p += 2
                p += 2
            } else {
                while (p < b.size && b[p].toInt() != 0) p++
                p++
            }
            return if (p < b.size) b.copyOfRange(p, b.size) else null
        }

        /** "(13)", "13" or "(13)Pop" → "Pop"; anything else as written. */
        private fun genre(s: String): String {
            val m = Regex("^\\((\\d+)\\)(.*)$").find(s)
            if (m != null) return m.groupValues[2].ifBlank { Id3Genres.name(m.groupValues[1].toInt()) ?: s }
            return s.toIntOrNull()?.let { Id3Genres.name(it) } ?: s
        }
    }
}

/** The standard ID3v1 genre list (index → name), used by numeric genres in ID3 and MP4 "gnre" tags. */
object Id3Genres {
    private val NAMES = listOf(
        "Blues", "Classic Rock", "Country", "Dance", "Disco", "Funk", "Grunge", "Hip-Hop", "Jazz", "Metal",
        "New Age", "Oldies", "Other", "Pop", "R&B", "Rap", "Reggae", "Rock", "Techno", "Industrial",
        "Alternative", "Ska", "Death Metal", "Pranks", "Soundtrack", "Euro-Techno", "Ambient", "Trip-Hop", "Vocal", "Jazz+Funk",
        "Fusion", "Trance", "Classical", "Instrumental", "Acid", "House", "Game", "Sound Clip", "Gospel", "Noise",
        "AlternRock", "Bass", "Soul", "Punk", "Space", "Meditative", "Instrumental Pop", "Instrumental Rock", "Ethnic", "Gothic",
        "Darkwave", "Techno-Industrial", "Electronic", "Pop-Folk", "Eurodance", "Dream", "Southern Rock", "Comedy", "Cult", "Gangsta",
        "Top 40", "Christian Rap", "Pop/Funk", "Jungle", "Native American", "Cabaret", "New Wave", "Psychedelic", "Rave", "Showtunes",
        "Trailer", "Lo-Fi", "Tribal", "Acid Punk", "Acid Jazz", "Polka", "Retro", "Musical", "Rock & Roll", "Hard Rock",
    )

    fun name(i: Int): String? = NAMES.getOrNull(i)
}

@Suppress("unused")
private val LE = ByteOrder.LITTLE_ENDIAN
