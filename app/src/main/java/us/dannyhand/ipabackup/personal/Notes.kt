package us.dannyhand.ipabackup.personal

import us.dannyhand.ipabackup.music.SqlDb
import java.io.IOException
import java.text.DateFormat
import java.util.Date

/**
 * Reads /var/mobile/Library/Notes/notes.sqlite (Core Data; needs AFC2): ZNOTE has titles and dates
 * (seconds since 2001), ZNOTEBODY has the HTML content.
 */
class NotesDb(private val db: SqlDb) {
    companion object { const val DB_PATH = "/var/mobile/Library/Notes/notes.sqlite" }

    data class Note(val title: String, val createdMs: Long?, val modifiedMs: Long?, val html: String)

    fun load(): List<Note> {
        val tables = db.tables()
        if ("ZNOTE" !in tables) throw IOException("Not a notes database (no ZNOTE table)")
        val nc = db.columns("ZNOTE")
        fun c(n: String) = if (n in nc) "n.$n" else "NULL"
        val hasBody = "ZNOTEBODY" in tables && "ZOWNER" in db.columns("ZNOTEBODY")
        val body = if (hasBody) "b.ZCONTENT" else "NULL"
        val join = if (hasBody) " LEFT JOIN ZNOTEBODY b ON b.ZOWNER = n.Z_PK" else ""
        val deleted = if ("ZDELETEDFLAG" in nc) " WHERE IFNULL(n.ZDELETEDFLAG, 0) = 0" else ""
        return db.query("SELECT ${c("ZTITLE")}, ${c("ZCREATIONDATE")}, ${c("ZMODIFICATIONDATE")}, $body FROM ZNOTE n$join$deleted")
            .map { r ->
                fun date(v: Any?) = (v as? Number)?.toDouble()?.let { AddressBook.APPLE_EPOCH_MS + (it * 1000).toLong() }
                Note(
                    title = (r[0] as? String)?.trim()?.ifEmpty { null } ?: "Untitled",
                    createdMs = date(r[1]),
                    modifiedMs = date(r[2]),
                    html = (r[3] as? String).orEmpty(),
                )
            }.sortedByDescending { it.modifiedMs ?: it.createdMs ?: 0 }
    }
}

object NotesExport {
    fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /** Notes' HTML to plain text: block tags become line breaks, entities decoded. */
    fun toText(html: String): String = html
        // iOS Notes stores each line as <div>line</div> (blank lines as <div><br></div>),
        // so the break belongs at each opening block tag.
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)<li[^>]*>"), "\n• ")
        .replace(Regex("(?i)<(div|p|h[1-6]|tr)(\\s[^>]*)?>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        .replace("&#39;", "'").replace("&amp;", "&")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    fun html(notes: List<NotesDb.Note>, deviceName: String, df: DateFormat): String = buildString {
        append("<!DOCTYPE html>\n<html><head><meta charset=\"utf-8\"><title>Notes from ${escape(deviceName)}</title>\n")
        append("<style>body{font-family:-apple-system,Roboto,sans-serif;max-width:46em;margin:2em auto;padding:0 1em;color:#222}")
        append("article{border-bottom:1px solid #ddd;padding:1em 0}h2{margin:0 0 .2em}.date{color:#888;font-size:.85em;margin-bottom:.8em}</style>\n")
        append("</head><body>\n<h1>Notes from ${escape(deviceName)}</h1>\n<p class=\"date\">${notes.size} notes</p>\n")
        for (n in notes) {
            append("<article><h2>").append(escape(n.title)).append("</h2>")
            val dates = listOfNotNull(
                n.createdMs?.let { "Created " + df.format(Date(it)) },
                n.modifiedMs?.takeIf { it != n.createdMs }?.let { "Modified " + df.format(Date(it)) },
            )
            if (dates.isNotEmpty()) append("<div class=\"date\">").append(dates.joinToString(" · ")).append("</div>")
            // Note bodies are already HTML; drop scripts just in case.
            append("<div>").append(n.html.replace(Regex("(?is)<script.*?</script>"), "")).append("</div></article>\n")
        }
        append("</body></html>\n")
    }
}
