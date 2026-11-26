package us.dannyhand.ipabackup.util

/** Plain, readable HTML pages for exported lists (history, packages). */
object HtmlPage {
    private const val CSS = "body{font-family:-apple-system,Roboto,Helvetica,sans-serif;max-width:60em;margin:1.5em auto;padding:0 1em;color:#222}" +
        "h1{font-size:1.3em}h2{font-size:1.1em;margin-top:2em}.sub{color:#888;font-size:.85em}" +
        "table{border-collapse:collapse;width:100%;font-size:.92em}th,td{text-align:left;padding:.35em .5em;border-bottom:1px solid #eee;vertical-align:top}" +
        "th{background:#f5f5f5;position:sticky;top:0}td.n{white-space:nowrap}a{color:#1e66c8;word-break:break-all}code{font-size:.9em}"

    fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    fun page(title: String, body: String) =
        "<!DOCTYPE html>\n<html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<title>${esc(title)}</title><style>$CSS</style></head><body>\n<h1>${esc(title)}</h1>\n$body</body></html>\n"

    /** A table; cells are already-escaped HTML. */
    fun table(headers: List<String>, rows: List<List<String>>): String = buildString {
        append("<table><tr>")
        headers.forEach { append("<th>").append(esc(it)).append("</th>") }
        append("</tr>\n")
        for (r in rows) {
            append("<tr>")
            r.forEachIndexed { i, c -> append(if (i == 0) "<td class=\"n\">" else "<td>").append(c).append("</td>") }
            append("</tr>\n")
        }
        append("</table>\n")
    }
}
