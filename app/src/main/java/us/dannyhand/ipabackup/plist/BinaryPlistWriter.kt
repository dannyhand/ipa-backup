package us.dannyhand.ipabackup.plist

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.Date

/**
 * Writes Apple's binary property list format ("bplist00"), which DeviceLink services such as
 * mobilebackup2 expect. Layout: the 8-byte header, every object one after another, a table of their
 * offsets, then a 32-byte trailer (offset and reference sizes, object count, top object, table offset).
 * Supports maps (string keys), lists, strings, booleans, whole numbers (negative ones as 8 bytes),
 * floating point, dates (seconds since 2001-01-01) and byte arrays. Nulls are left out of maps and lists.
 */
object BinaryPlistWriter {
    private const val APPLE_EPOCH_MS = 978_307_200_000L

    fun write(root: Any): ByteArray {
        // Flatten into a list of objects; containers refer to their children by index.
        val objects = ArrayList<Any>()
        fun add(v: Any): Int {
            val idx = objects.size
            when (v) {
                is Map<*, *> -> {
                    objects += Unit // placeholder, filled below
                    val entries = v.entries.filter { it.value != null }
                    val keys = entries.map { add(it.key.toString()) }
                    val values = entries.map { add(it.value!!) }
                    objects[idx] = DictRefs(keys, values)
                }
                is List<*> -> {
                    objects += Unit
                    objects[idx] = ArrayRefs(v.filterNotNull().map { add(it) })
                }
                is Array<*> -> return add(v.toList())
                else -> objects += v
            }
            return idx
        }
        add(root)
        val refSize = when { objects.size < 0x100 -> 1; objects.size < 0x10000 -> 2; else -> 4 }

        val out = ByteArrayOutputStream()
        out.write("bplist00".toByteArray(Charsets.US_ASCII))
        val offsets = LongArray(objects.size)
        fun marker(type: Int, count: Int) {
            if (count < 15) out.write(type or count) else { out.write(type or 0x0F); writeInt(out, count.toLong()) }
        }
        fun ref(i: Int) { for (b in refSize - 1 downTo 0) out.write((i ushr (8 * b)) and 0xFF) }
        for ((i, o) in objects.withIndex()) {
            offsets[i] = out.size().toLong()
            when (o) {
                is DictRefs -> { marker(0xD0, o.keys.size); o.keys.forEach(::ref); o.values.forEach(::ref) }
                is ArrayRefs -> { marker(0xA0, o.items.size); o.items.forEach(::ref) }
                is Boolean -> out.write(if (o) 0x09 else 0x08)
                is Float, is Double -> { out.write(0x23); out.write(ByteBuffer.allocate(8).putDouble((o as Number).toDouble()).array()) }
                is Number -> writeInt(out, o.toLong())
                is Date -> { out.write(0x33); out.write(ByteBuffer.allocate(8).putDouble((o.time - APPLE_EPOCH_MS) / 1000.0).array()) }
                is ByteArray -> { marker(0x40, o.size); out.write(o) }
                is String -> {
                    if (o.all { it.code < 0x80 }) { marker(0x50, o.length); out.write(o.toByteArray(Charsets.US_ASCII)) }
                    else { marker(0x60, o.length); out.write(o.toByteArray(Charsets.UTF_16BE)) }
                }
                else -> throw IllegalArgumentException("Can't write ${o::class.java.simpleName} to a plist")
            }
        }
        val tableOffset = out.size().toLong()
        val maxOffset = offsets.maxOrNull() ?: 0
        val offSize = when { maxOffset < 0x100 -> 1; maxOffset < 0x10000 -> 2; maxOffset < 0x100000000L -> 4; else -> 8 }
        for (off in offsets) for (b in offSize - 1 downTo 0) out.write(((off ushr (8 * b)) and 0xFF).toInt())
        val trailer = ByteBuffer.allocate(32)
        trailer.position(6)
        trailer.put(offSize.toByte()).put(refSize.toByte()).putLong(objects.size.toLong()).putLong(0).putLong(tableOffset)
        out.write(trailer.array())
        return out.toByteArray()
    }

    /** Integers: 1, 2 or 4 bytes when they fit unsigned, otherwise 8 bytes (two's complement). */
    private fun writeInt(out: ByteArrayOutputStream, v: Long) {
        when {
            v in 0..0xFF -> { out.write(0x10); out.write(v.toInt()) }
            v in 0..0xFFFF -> { out.write(0x11); out.write(ByteBuffer.allocate(2).putShort(v.toShort()).array()) }
            v in 0..0xFFFFFFFFL -> { out.write(0x12); out.write(ByteBuffer.allocate(4).putInt(v.toInt()).array()) }
            else -> { out.write(0x13); out.write(ByteBuffer.allocate(8).putLong(v).array()) }
        }
    }

    private class DictRefs(val keys: List<Int>, val values: List<Int>)
    private class ArrayRefs(val items: List<Int>)
}
