package us.dannyhand.ipabackup.plist

import java.io.IOException
import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.Date

/**
 * Parser for Apple binary property lists ("bplist00"). Pure Kotlin, no Android deps.
 * Types: dict -> Map<String, Any?>, array/set -> List, string -> String, int -> Long
 * (BigInteger for 128-bit), real -> Double, bool, data -> ByteArray, date -> Date, uid -> Long.
 */
object BinaryPlist {
    private const val APPLE_EPOCH_MS = 978_307_200_000L // 2001-01-01 UTC

    fun isBinary(bytes: ByteArray) =
        bytes.size >= 8 && String(bytes, 0, 6, Charsets.US_ASCII) == "bplist"

    fun parse(bytes: ByteArray): Any? {
        if (!isBinary(bytes) || bytes.size < 40) throw IOException("Not a binary plist")
        val t = ByteBuffer.wrap(bytes, bytes.size - 32, 32)
        t.position(bytes.size - 32 + 6)
        val offsetSize = t.get().toInt() and 0xFF
        val refSize = t.get().toInt() and 0xFF
        val numObjects = t.long
        val topObject = t.long
        val offsetTable = t.long
        if (numObjects <= 0 || numObjects > 10_000_000 || topObject !in 0 until numObjects ||
            offsetTable < 8 || offsetTable + numObjects * offsetSize > bytes.size - 32
        ) throw IOException("Corrupt binary plist trailer")
        return Reader(bytes, offsetSize, refSize, numObjects.toInt(), offsetTable.toInt()).read(topObject.toInt(), 0)
    }

    private class Reader(
        val b: ByteArray,
        val offsetSize: Int,
        val refSize: Int,
        val numObjects: Int,
        val offsetTable: Int,
    ) {
        fun uint(pos: Int, size: Int): Long {
            var v = 0L
            for (i in 0 until size) v = (v shl 8) or (b[pos + i].toLong() and 0xFF)
            return v
        }

        fun offsetOf(obj: Int): Int {
            if (obj !in 0 until numObjects) throw IOException("Bad object ref $obj")
            return uint(offsetTable + obj * offsetSize, offsetSize).toInt()
        }

        /** Returns (count, position after the count). */
        fun count(pos: Int, low: Int): Pair<Int, Int> {
            if (low != 0xF) return low to pos + 1
            val marker = b[pos + 1].toInt() and 0xFF
            if (marker shr 4 != 0x1) throw IOException("Bad extended count marker")
            val n = 1 shl (marker and 0xF)
            return uint(pos + 2, n).toInt() to pos + 2 + n
        }

        fun read(obj: Int, depth: Int): Any? {
            if (depth > 64) throw IOException("Plist nesting too deep")
            val pos = offsetOf(obj)
            val marker = b[pos].toInt() and 0xFF
            val hi = marker shr 4
            val low = marker and 0xF
            return when (hi) {
                0x0 -> when (marker) {
                    0x08 -> false
                    0x09 -> true
                    else -> null
                }
                0x1 -> {
                    val n = 1 shl low
                    when (n) {
                        1, 2, 4 -> uint(pos + 1, n)
                        8 -> ByteBuffer.wrap(b, pos + 1, 8).long
                        16 -> BigInteger(b.copyOfRange(pos + 1, pos + 17))
                        else -> throw IOException("Bad int size $n")
                    }
                }
                0x2 -> when (1 shl low) {
                    4 -> ByteBuffer.wrap(b, pos + 1, 4).float.toDouble()
                    8 -> ByteBuffer.wrap(b, pos + 1, 8).double
                    else -> throw IOException("Bad real size")
                }
                0x3 -> Date(APPLE_EPOCH_MS + (ByteBuffer.wrap(b, pos + 1, 8).double * 1000).toLong())
                0x4 -> { val (n, p) = count(pos, low); b.copyOfRange(p, p + n) }
                0x5 -> { val (n, p) = count(pos, low); String(b, p, n, Charsets.US_ASCII) }
                0x6 -> { val (n, p) = count(pos, low); String(b, p, n * 2, Charsets.UTF_16BE) }
                0x8 -> uint(pos + 1, low + 1)
                0xA, 0xC -> {
                    val (n, p) = count(pos, low)
                    List(n) { i -> read(uint(p + i * refSize, refSize).toInt(), depth + 1) }
                }
                0xD -> {
                    val (n, p) = count(pos, low)
                    val m = LinkedHashMap<String, Any?>(n)
                    for (i in 0 until n) {
                        val key = read(uint(p + i * refSize, refSize).toInt(), depth + 1).toString()
                        m[key] = read(uint(p + (n + i) * refSize, refSize).toInt(), depth + 1)
                    }
                    m
                }
                else -> throw IOException("Unknown bplist marker 0x%02x".format(marker))
            }
        }
    }
}
