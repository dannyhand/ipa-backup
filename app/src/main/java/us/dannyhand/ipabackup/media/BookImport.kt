package us.dannyhand.ipabackup.media

import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlin.random.Random

/**
 * Adding books to iBooks the way iTunes sync does (iOS 4–7):
 *  - an EPUB is stored unzipped, as a folder named "<Persistent ID>.epub" in the media folder's /Books;
 *    a PDF is stored as a plain "<Persistent ID>.pdf" file
 *  - each book gets an entry in /Books/Books.plist (Name, Artist, Path, Persistent ID…), which iBooks
 *    reads when it starts; iBooks needs to be closed fully and reopened to pick up new entries
 * The Persistent ID is a random 64-bit number in hex, like iTunes' own IDs.
 */
object BookImport {
    const val PLIST = "/Books/Books.plist"
    const val BACKUP = "/Books/Books.plist.before-ipa-backup"

    data class Info(val title: String, val author: String?, val isPdf: Boolean)

    /** Title and author from an EPUB's OPF metadata (dc:title, dc:creator), or the file name. */
    fun inspect(bytes: ByteArray, fileName: String): Info {
        val base = fileName.substringBeforeLast('.').ifBlank { "Untitled" }
        if (fileName.lowercase().endsWith(".pdf") || (bytes.size > 4 && String(bytes, 0, 4, Charsets.ISO_8859_1) == "%PDF"))
            return Info(base, null, true)
        val texts = HashMap<String, String>()
        runCatching {
            ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    val n = e.name
                    if (n.endsWith(".opf", true) || n.equals("META-INF/container.xml", true))
                        texts[n] = z.readBytes().toString(Charsets.UTF_8)
                }
            }
        }
        val opfPath = texts.entries.firstOrNull { it.key.equals("META-INF/container.xml", true) }?.value
            ?.let { Regex("full-path\\s*=\\s*\"([^\"]+)\"").find(it)?.groupValues?.get(1) }
        val opf = opfPath?.let { texts[it] } ?: texts.entries.firstOrNull { it.key.endsWith(".opf", true) }?.value
        fun tag(name: String) = opf?.let {
            Regex("<(?:dc:)?$name\\b[^>]*>(.*?)</(?:dc:)?$name>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).find(it)
                ?.groupValues?.get(1)?.let(::unescape)?.trim()?.ifEmpty { null }
        }
        return Info(tag("title") ?: base, tag("creator"), false)
    }

    private fun unescape(s: String) = s.replace(Regex("<!\\[CDATA\\[(.*?)]]>", RegexOption.DOT_MATCHES_ALL), "$1")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&#39;", "'").replace("&amp;", "&")

    fun isEpubZip(bytes: ByteArray) = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

    fun newPersistentId(rnd: Random = Random.Default) = "%016X".format(rnd.nextLong())

    /** The existing entries of a Books.plist (missing or unreadable = none). */
    fun entries(plist: Any?): List<Map<*, *>> = ((plist as? Map<*, *>)?.get("Books") as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty()

    fun alreadyThere(plist: Any?, info: Info): Boolean = entries(plist).any { e ->
        (e["Name"] as? String)?.equals(info.title, true) == true &&
            ((e["Artist"] as? String) ?: "").equals(info.author ?: "", true)
    }

    /** A copy of the plist with one more book; other keys and entries are kept as they were. */
    fun withBook(plist: Any?, info: Info, pid: String, bytes: ByteArray): Map<String, Any?> {
        val root = LinkedHashMap<String, Any?>()
        (plist as? Map<*, *>)?.forEach { (k, v) -> if (k is String) root[k] = v }
        val ext = if (info.isPdf) "pdf" else "epub"
        val entry = linkedMapOf<String, Any?>(
            "Name" to info.title,
            "Artist" to (info.author ?: ""),
            "Path" to "$pid.$ext",
            "Persistent ID" to pid,
            "Package Hash" to MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02X".format(it) },
            "Inserted-By-iBooks" to false,
            "Page Progression Direction" to "default",
        )
        root["Books"] = entries(plist) + entry
        return root
    }
}
