package us.dannyhand.ipabackup.plist

import android.util.Base64
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * Minimal XML property-list codec. lockdownd answers in XML when asked in XML.
 * Supports dict, array, string, integer, real, true/false, data, date.
 * installation_proxy also accepts XML. (AFC uses its own binary packet format, not plists.)
 */
object Plist {

    fun toXml(value: Any?): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        append("<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n")
        append("<plist version=\"1.0\">\n")
        writeValue(this, value)
        append("</plist>\n")
    }

    private fun writeValue(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> {}
            is Map<*, *> -> {
                sb.append("<dict>\n")
                for ((k, value) in v) {
                    if (value == null) continue
                    sb.append("<key>").append(escape(k.toString())).append("</key>\n")
                    writeValue(sb, value)
                }
                sb.append("</dict>\n")
            }
            is List<*> -> {
                sb.append("<array>\n")
                v.forEach { writeValue(sb, it) }
                sb.append("</array>\n")
            }
            is String -> sb.append("<string>").append(escape(v)).append("</string>\n")
            is Boolean -> sb.append(if (v) "<true/>\n" else "<false/>\n")
            is Int, is Long, is Short, is java.math.BigInteger -> sb.append("<integer>").append(v).append("</integer>\n")
            is Float, is Double -> sb.append("<real>").append(v).append("</real>\n")
            is ByteArray -> sb.append("<data>").append(Base64.encodeToString(v, Base64.NO_WRAP)).append("</data>\n")
            else -> throw IllegalArgumentException("Unsupported plist type: ${v::class.java}")
        }
    }

    private fun escape(s: String) =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    fun parse(bytes: ByteArray): Any? {
        if (BinaryPlist.isBinary(bytes)) return BinaryPlist.parse(bytes)
        val p = Xml.newPullParser()
        p.setInput(ByteArrayInputStream(bytes), "UTF-8")
        while (!(p.eventType == XmlPullParser.START_TAG && p.name == "plist")) {
            if (p.next() == XmlPullParser.END_DOCUMENT) throw IOException("No <plist> element")
        }
        if (p.nextTag() != XmlPullParser.START_TAG) return null // empty <plist/>
        return readValue(p)
    }

    /** Parser is on the value's START_TAG; leaves it on that value's END_TAG. */
    private fun readValue(p: XmlPullParser): Any? = when (p.name) {
        "dict" -> {
            val m = LinkedHashMap<String, Any?>()
            while (p.nextTag() == XmlPullParser.START_TAG) {   // <key>
                val key = p.nextText()
                p.nextTag()
                m[key] = readValue(p)
            }
            m
        }
        "array" -> {
            val l = ArrayList<Any?>()
            while (p.nextTag() == XmlPullParser.START_TAG) l += readValue(p)
            l
        }
        "string", "date" -> p.nextText()
        "integer" -> p.nextText().trim().let { it.toLongOrNull() ?: it.toBigInteger() }
        "real" -> p.nextText().trim().toDouble()
        "true" -> { p.nextTag(); true }
        "false" -> { p.nextTag(); false }
        "data" -> Base64.decode(p.nextText(), Base64.DEFAULT)
        else -> throw IOException("Unknown plist element <${p.name}>")
    }
}
