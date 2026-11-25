package us.dannyhand.ipabackup.personal

import us.dannyhand.ipabackup.music.SqlDb
import java.io.IOException

/**
 * Reads /var/mobile/Library/Safari/Bookmarks.db (needs AFC2) and writes the Netscape bookmark
 * HTML format that Chrome, Firefox, Edge and Safari all import. Includes the Reading List.
 */
class SafariBookmarks(private val db: SqlDb) {
    companion object { const val DB_PATH = "/var/mobile/Library/Safari/Bookmarks.db" }

    class Node(val title: String, val url: String?, val children: MutableList<Node> = ArrayList()) {
        val isFolder get() = url == null
        fun count(): Int = if (isFolder) children.sumOf { it.count() } else 1
    }

    /** Returns the root folder (its children are the top-level folders and bookmarks). */
    fun load(): Node {
        if ("bookmarks" !in db.tables()) throw IOException("Not a Safari bookmarks database")
        val cols = db.columns("bookmarks")
        for (needed in listOf("id", "parent", "type", "title", "url"))
            if (needed !in cols) throw IOException("bookmarks table has no '$needed' column: $cols")
        val order = if ("order_index" in cols) "order_index" else "id"
        val deleted = if ("deleted" in cols) " WHERE IFNULL(deleted, 0) = 0" else ""
        val hidden = if ("hidden" in cols) "hidden" else "0"
        val rows = db.query("SELECT id, parent, type, title, url, $hidden FROM bookmarks$deleted ORDER BY parent, $order")

        class Row(val id: Long, val parent: Long?, val folder: Boolean, val title: String, val url: String?, val hidden: Boolean)
        val all = rows.map { r ->
            Row((r[0] as Number).toLong(), (r[1] as? Number)?.toLong(), (r[2] as? Number)?.toInt() == 1,
                (r[3] as? String).orEmpty(), (r[4] as? String)?.ifEmpty { null }, ((r[5] as? Number)?.toInt() ?: 0) != 0)
        }
        val kids = all.groupBy { it.parent }
        val ids = all.map { it.id }.toSet()

        fun build(r: Row): Node? {
            if (!r.folder) return if (r.url != null) Node(r.title.ifEmpty { r.url }, r.url) else null
            val title = when (r.title) {
                "com.apple.ReadingList" -> "Reading List"
                "BookmarksBar" -> "Bookmarks Bar"
                "BookmarksMenu" -> "Bookmarks Menu"
                else -> r.title.ifEmpty { "Folder" }
            }
            val node = Node(title, null)
            kids[r.id].orEmpty().forEach { c -> build(c)?.let { node.children += it } }
            return node.takeIf { it.children.isNotEmpty() || !r.hidden }
        }

        val root = Node("Bookmarks", null)
        // Top level = rows whose parent is missing (the invisible root has id 0 / parent NULL).
        val roots = all.filter { it.parent == null || it.parent !in ids }
        for (r in roots) {
            if (r.folder && r.title.isEmpty() && r.parent == null) {
                kids[r.id].orEmpty().forEach { c -> build(c)?.let { root.children += it } } // unwrap the invisible root
            } else build(r)?.let { root.children += it }
        }
        return root
    }
}

object BookmarksHtml {
    fun write(root: SafariBookmarks.Node): String = buildString {
        append("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n")
        append("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n")
        append("<TITLE>Bookmarks</TITLE>\n<H1>Bookmarks</H1>\n")
        fun list(n: SafariBookmarks.Node, indent: String) {
            append(indent).append("<DL><p>\n")
            for (c in n.children) {
                if (c.isFolder) {
                    append(indent).append("    <DT><H3>").append(NotesExport.escape(c.title)).append("</H3>\n")
                    list(c, "$indent    ")
                } else {
                    append(indent).append("    <DT><A HREF=\"").append(NotesExport.escape(c.url!!)).append("\">")
                        .append(NotesExport.escape(c.title)).append("</A>\n")
                }
            }
            append(indent).append("</DL><p>\n")
        }
        list(root, "")
    }
}
