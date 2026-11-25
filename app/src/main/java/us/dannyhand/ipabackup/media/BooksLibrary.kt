package us.dannyhand.ipabackup.media

/**
 * iBooks files in the media folder's /Books (no jailbreak needed). iTunes-synced EPUBs are stored
 * unzipped (a folder named *.epub); PDFs are plain files. Titles and authors come from
 * /Books/Books.plist and /Books/Purchases/Purchases.plist.
 */
object BooksLibrary {
    const val DIR = "/Books"
    val PLISTS = listOf("/Books/Books.plist", "/Books/Purchases/Purchases.plist")
    val BOOK_EXT = setOf("epub", "pdf", "ibooks")

    data class Meta(val relPath: String, val title: String?, val author: String?)

    /** Reads the "Books" array of a Books.plist / Purchases.plist dict. [baseDir] is the plist's folder. */
    fun parsePlist(plist: Any?, baseDir: String): List<Meta> {
        val books = (plist as? Map<*, *>)?.get("Books") as? List<*> ?: return emptyList()
        return books.mapNotNull { b ->
            val m = b as? Map<*, *> ?: return@mapNotNull null
            val path = (m["Path"] as? String)?.trim()?.ifEmpty { null } ?: return@mapNotNull null
            val full = if (path.startsWith("/")) path else "${baseDir.trimEnd('/')}/$path"
            Meta(
                relPath = full,
                title = (m["Name"] as? String ?: m["Title"] as? String ?: m["itemName"] as? String)?.trim()?.ifEmpty { null },
                author = (m["Artist"] as? String ?: m["Author"] as? String ?: m["artistName"] as? String)?.trim()?.ifEmpty { null },
            )
        }
    }

    data class Book(val devicePath: String, val title: String, val author: String?, val isFolder: Boolean, val size: Long, val drm: Boolean) {
        val extension get() = devicePath.substringAfterLast('.', "").lowercase()
        /** "Author - Title.epub" (or just "Title.pdf"). */
        fun saveName(): String {
            val bad = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")
            val base = (author?.let { "$it - " } ?: "") + title
            return base.replace(bad, "_").trim().take(180) + "." + extension
        }
    }
}
