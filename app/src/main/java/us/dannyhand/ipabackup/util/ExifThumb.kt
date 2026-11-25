package us.dannyhand.ipabackup.util

/**
 * Pulls the small preview JPEG that cameras (including the iPod's) embed in a photo's EXIF block,
 * so a thumbnail needs only the first few KB of the file instead of the whole photo.
 */
object ExifThumb {
    /** [orientation] is the EXIF value (1 = normal, 3 = 180°, 6 = 90° CW, 8 = 270° CW). */
    class Result(val thumbnail: ByteArray?, val orientation: Int)

    private val NONE = Result(null, 1)

    fun parse(b: ByteArray): Result = try {
        parseJpeg(b)
    } catch (e: IndexOutOfBoundsException) {
        NONE
    }

    /** Degrees to rotate clockwise for an EXIF orientation value. */
    fun rotationDegrees(orientation: Int) = when (orientation) { 3 -> 180; 6 -> 90; 8 -> 270; else -> 0 }

    private fun u8(b: ByteArray, p: Int) = b[p].toInt() and 0xFF

    private fun parseJpeg(b: ByteArray): Result {
        if (b.size < 4 || u8(b, 0) != 0xFF || u8(b, 1) != 0xD8) return NONE
        var p = 2
        while (p + 4 <= b.size) {
            if (u8(b, p) != 0xFF) return NONE
            val marker = u8(b, p + 1)
            if (marker == 0xFF) { p++; continue } // fill byte
            if (marker == 0xDA || marker == 0xD9) return NONE // image data starts: no EXIF before it
            val len = (u8(b, p + 2) shl 8) or u8(b, p + 3)
            if (len < 2) return NONE
            if (marker == 0xE1 && len >= 8 && p + 10 <= b.size &&
                String(b, p + 4, 6, Charsets.ISO_8859_1) == "Exif\u0000\u0000"
            ) {
                return parseTiff(b, p + 10, minOf(b.size, p + 2 + len))
            }
            p += 2 + len
        }
        return NONE
    }

    private fun parseTiff(b: ByteArray, t: Int, end: Int): Result {
        val le = when (String(b, t, 2, Charsets.ISO_8859_1)) { "II" -> true; "MM" -> false; else -> return NONE }
        fun u16(p: Int) = if (le) u8(b, p) or (u8(b, p + 1) shl 8) else (u8(b, p) shl 8) or u8(b, p + 1)
        fun u32(p: Int): Long = if (le) (u16(p).toLong() or (u16(p + 2).toLong() shl 16))
            else ((u16(p).toLong() shl 16) or u16(p + 2).toLong())
        if (u16(t + 2) != 42) return NONE

        var orientation = 1
        var thumbOffset = -1L
        var thumbLength = -1L

        fun readIfd(ifdOff: Long, isIfd1: Boolean): Long {
            val p = t + ifdOff.toInt()
            if (ifdOff <= 0 || p + 2 > end) return 0
            val count = u16(p)
            for (i in 0 until count) {
                val e = p + 2 + i * 12
                if (e + 12 > end) break
                val tag = u16(e)
                val type = u16(e + 2)
                val value = if (type == 3) u16(e + 8).toLong() else u32(e + 8) // SHORT sits in the first 2 bytes
                when {
                    !isIfd1 && tag == 0x0112 -> orientation = value.toInt()
                    isIfd1 && tag == 0x0201 -> thumbOffset = value
                    isIfd1 && tag == 0x0202 -> thumbLength = value
                }
            }
            val next = p + 2 + count * 12
            return if (next + 4 <= end) u32(next) else 0
        }

        val ifd1 = readIfd(u32(t + 4), isIfd1 = false)
        readIfd(ifd1, isIfd1 = true)

        if (thumbOffset <= 0 || thumbLength <= 0) return Result(null, orientation)
        val start = t + thumbOffset.toInt()
        val stop = start + thumbLength.toInt()
        if (stop > b.size || stop > end || u8(b, start) != 0xFF || u8(b, start + 1) != 0xD8) return Result(null, orientation)
        return Result(b.copyOfRange(start, stop), orientation)
    }
}
