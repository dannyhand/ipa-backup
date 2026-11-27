package us.dannyhand.ipabackup.music

import java.text.Normalizer

/**
 * Sort keys for MediaLibrary.sqlitedb's sort_map, the way iOS 6 makes them (an ICU 4.x collation key
 * with Apple's tweaks), worked out from a real iOS 6.1.6 library of 17,888 names: 97% of generated keys
 * match byte for byte and 99.5% of recent names sort between the same neighbours.
 *
 * Layout: [lead] [primary weights] 01 [secondary] 01 [tertiary] 00
 *  - lead: 0x30 when the name starts with a Latin letter, 0x31 otherwise (numbers, symbols, other
 *    scripts: the "#" section, after Z). Leading punctuation and spaces are skipped first, so
 *    "(B Quiet Interlude)" files under B.
 *  - primary: one weight per character, case- and accent-blind (a = 0x27, b = 0x29 … z = 0x59,
 *    space 0x04, punctuation two bytes). A run of digits is one numeric weight (0x12, 0x80 + number
 *    of digit pairs, then 2×pair+6 per pair, the last minus 1), so "2" sorts before "10".
 *  - secondary (accents) and tertiary (case): mostly "common" weights, written as run counts: a run
 *    before a higher weight counts down from the top, a final run counts up from 0x05, 64 at a time.
 *    An accent adds its own secondary weight (acute 0x8D, grave 0x8F, diaeresis 0x9D, tilde 0xA1);
 *    a capital letter is tertiary 0x8F.
 * The library also mixes an older generation of punctuation weights (e.g. & = 0B6B instead of 0BAD);
 * the newer ones, which iOS 6 writes today, are used here.
 *
 * Names sort by these keys (byte by byte), except the empty name, which is always first.
 * The grouping_key of albums, artists, genres and composers is the primary part without the lead.
 */
object SortKeys {
    private val TABLE: Map<String, ByteArray> = mapOf(
        " " to "04",
        "_" to "0642",
        "-" to "0646",
        "," to "07",
        ";" to "0858",
        ":" to "0861",
        "!" to "0881",
        "?" to "0888",
        "." to "09",
        "'" to "0a93",
        "\u2019" to "0a97",
        "\"" to "0aa1",
        "(" to "0ab5",
        ")" to "0ab7",
        "@" to "0b9d",
        "*" to "0b9f",
        "/" to "0ba9",
        "\\" to "0bab",
        "&" to "0bad",
        "#" to "0bb1",
        "^" to "0d0f",
        "+" to "0d5f",
        "<" to "0d67",
        "=" to "0d69",
        ">" to "0d6b",
        "|" to "0d6f",
        "~" to "0d73",
        "\u2665" to "0d900e",
        "\$" to "0f0e",
        "\u00e0" to "27",
        "\u00e1" to "27",
        "\u00e3" to "27",
        "\u00e4" to "27",
        "\u00e8" to "2f",
        "\u00e9" to "2f",
        "\u00eb" to "2f",
        "\u33cc" to "3741",
        "\u00f1" to "41",
        "\u00f6" to "43",
        "\u00f8" to "43",
        "\u00fa" to "4f",
        "\u00fc" to "4f",
        "\u044f" to "5d4503",
        "\u4400" to "e0cc94",
    ).mapValues { (_, h) -> h.chunked(2).map { it.toInt(16).toByte() }.toByteArray() }

    private val MARKS = mapOf('\u0301' to 0x8D, '\u0300' to 0x8F, '\u0308' to 0x9D, '\u0303' to 0xA1)

    /** Skips leading characters that aren't letters or digits. */
    fun stripLead(s: String): String = s.dropWhile { !it.isLetterOrDigit() }

    private fun weightOf(c: Char): ByteArray? {
        val l = c.lowercaseChar()
        if (l in 'a'..'z') return byteArrayOf((0x27 + 2 * (l - 'a')).toByte())
        return TABLE[l.toString()]
    }

    private fun numeric(digits: String): ByteArray {
        var d = digits.trimStart('0').ifEmpty { "0" }
        if (d.length % 2 == 1) d = "0$d"
        val pairs = d.chunked(2).map { it.toInt() }
        val out = byteArrayOf(0x12, (0x80 + pairs.size).toByte()) + ByteArray(pairs.size) { (2 * pairs[it] + 6).toByte() }
        out[out.size - 1] = (out[out.size - 1] - 1).toByte()
        return out
    }

    /** One collation unit: primary bytes (null for an accent), and either "is upper case" or the accent's secondary weight. */
    private class Unit(val primary: ByteArray?, val upper: Boolean, val accent: Int = 0)

    private fun units(s: String): List<Unit> {
        val out = ArrayList<Unit>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c in '0'..'9') {
                var j = i
                while (j < s.length && s[j] in '0'..'9') j++
                out += Unit(numeric(s.substring(i, j)), false)
                i = j
                continue
            }
            val nfd = Normalizer.normalize(c.toString(), Normalizer.Form.NFD)
            val w = (if (nfd.length > 1) weightOf(nfd[0]) else null) ?: weightOf(c)
            // Characters not seen in a real library are skipped (treated as ignorable) rather than guessed.
            if (w != null && w.isNotEmpty()) out += Unit(w, c.isUpperCase())
            for (m in nfd.drop(1)) MARKS[m]?.let { out += Unit(null, false, it) }
            i++
        }
        return out
    }

    private fun compress(levels: List<Int?>, top: Int, out: java.io.ByteArrayOutputStream) {
        val bottom = 0x05
        val cap = 0x40
        var run = 0
        fun flush(next: Int?) {
            if (run == 0) return
            if (next != null && next > bottom) {
                while (run > cap) { out.write(top - cap + 1); run -= cap }
                out.write(top - run + 1)
            } else {
                while (run > cap) { out.write(bottom + cap); run -= cap }
                out.write(bottom + run - 1)
            }
            run = 0
        }
        for (v in levels) {
            if (v == null) { run++; continue }
            flush(v); out.write(v)
        }
        flush(null)
    }

    fun key(name: String): ByteArray {
        val s = stripLead(name)
        if (s.isEmpty()) return byteArrayOf(0x31, 0x01, 0x01, 0x00)
        val u = units(s)
        val first = Normalizer.normalize(s.substring(0, 1), Normalizer.Form.NFD)[0].lowercaseChar()
        val out = java.io.ByteArrayOutputStream()
        out.write(if (first in 'a'..'z') 0x30 else 0x31)
        for (x in u) x.primary?.let { out.write(it) }
        out.write(0x01)
        compress(u.map { if (it.primary == null) it.accent else null }, 0x86, out)
        out.write(0x01)
        compress(u.filter { it.primary != null }.map { if (it.upper) 0x8F else null }, 0x85, out)
        out.write(0x00)
        return out.toByteArray()
    }

    /** The primary part without the lead byte: the grouping_key of albums, artists, genres and composers. */
    fun groupingKey(name: String): ByteArray {
        if (stripLead(name).isEmpty()) return ByteArray(0)
        val k = key(name)
        val end = k.indexOf(0x01.toByte())
        return k.copyOfRange(1, end)
    }

    /** Index section: 0–25 for A–Z, 26 for "#" (numbers, symbols, other scripts). */
    fun section(name: String): Int {
        val s = stripLead(name)
        if (s.isEmpty()) return 0
        val c = Normalizer.normalize(s.substring(0, 1), Normalizer.Form.NFD)[0].uppercaseChar()
        return if (c in 'A'..'Z') c - 'A' else 26
    }
}
