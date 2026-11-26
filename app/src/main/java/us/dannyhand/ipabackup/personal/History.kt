package us.dannyhand.ipabackup.personal

import us.dannyhand.ipabackup.music.SqlDb
import us.dannyhand.ipabackup.util.HtmlPage
import java.text.DateFormat
import java.util.Date

/**
 * Safari history: /var/mobile/Library/Safari/History.plist (iOS ≤ 7), a dict whose WebHistoryDates
 * array holds one dict per page: the URL under the empty key "", "title", "visitCount", and
 * "lastVisitedDate" as a string of seconds since 2001-01-01 (Apple's reference date).
 */
object SafariHistory {
    const val PATH = "/var/mobile/Library/Safari/History.plist"
    private const val APPLE_EPOCH_S = 978_307_200L

    data class Visit(val url: String, val title: String, val lastMs: Long?, val count: Int)

    fun parse(plist: Any?): List<Visit> {
        val dates = (plist as? Map<*, *>)?.get("WebHistoryDates") as? List<*> ?: return emptyList()
        return dates.mapNotNull { e ->
            val m = e as? Map<*, *> ?: return@mapNotNull null
            val url = m[""] as? String ?: return@mapNotNull null
            val t = (m["lastVisitedDate"] as? String)?.toDoubleOrNull() ?: (m["lastVisitedDate"] as? Number)?.toDouble()
            Visit(url, (m["title"] as? String).orEmpty(), t?.let { ((it + APPLE_EPOCH_S) * 1000).toLong() }, (m["visitCount"] as? Number)?.toInt() ?: 1)
        }.sortedByDescending { it.lastMs ?: 0 }
    }

    fun html(device: String, visits: List<Visit>, fmt: DateFormat): String = HtmlPage.page(
        "Safari history on $device",
        "<p class=\"sub\">${visits.size} pages, most recent first</p>\n" + HtmlPage.table(
            listOf("Last visited", "Page", "Visits"),
            visits.map { v ->
                listOf(v.lastMs?.let { HtmlPage.esc(fmt.format(Date(it))) } ?: "",
                    "<a href=\"${HtmlPage.esc(v.url)}\">${HtmlPage.esc(v.title.ifBlank { v.url })}</a>" +
                        if (v.title.isNotBlank()) "<div class=\"sub\">${HtmlPage.esc(v.url)}</div>" else "",
                    v.count.toString())
            },
        ),
    )
}

/**
 * Phone and FaceTime calls: /var/wireless/Library/CallHistory/call_history.db (iOS ≤ 7), table "call"
 * with address, date (Unix seconds), duration (seconds) and flags. In flags, bit 0 set = outgoing;
 * FaceTime calls on iOS 5–7 have bit 4 (16) set. An iPod touch only has FaceTime calls, if any.
 */
class CallHistory(private val db: SqlDb) {
    companion object { const val DB_PATH = "/var/wireless/Library/CallHistory/call_history.db" }

    data class Call(val address: String, val dateMs: Long, val durationS: Long, val outgoing: Boolean, val faceTime: Boolean)

    fun load(): List<Call> {
        if ("call" !in db.tables()) return emptyList()
        val cols = db.columns("call")
        val flags = if ("flags" in cols) "flags" else "0"
        return db.query("SELECT address, date, duration, $flags FROM call ORDER BY date DESC").map { r ->
            val f = (r[3] as? Number)?.toLong() ?: 0
            val addr = r[0]?.toString().orEmpty()
            Call(addr, ((r[1] as? Number)?.toLong() ?: 0) * 1000, (r[2] as? Number)?.toLong() ?: 0,
                outgoing = f and 1L == 1L, faceTime = f and 16L != 0L || '@' in addr)
        }
    }
}

object CallHistoryHtml {
    private fun duration(s: Long) = if (s <= 0) "" else if (s < 3600) "%d:%02d".format(s / 60, s % 60) else "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60)

    fun html(device: String, calls: List<CallHistory.Call>, names: ContactNames, fmt: DateFormat): String = HtmlPage.page(
        "Call history on $device",
        "<p class=\"sub\">${calls.size} calls, most recent first</p>\n" + HtmlPage.table(
            listOf("When", "Who", "Type", "Length"),
            calls.map { c ->
                val name = names.display(c.address)
                val kind = (if (c.faceTime) "FaceTime, " else "") +
                    when { c.outgoing -> "outgoing"; c.durationS > 0 -> "incoming"; else -> "missed" }
                listOf(HtmlPage.esc(fmt.format(Date(c.dateMs))),
                    HtmlPage.esc(name) + if (name != c.address && c.address.isNotEmpty()) "<div class=\"sub\">${HtmlPage.esc(c.address)}</div>" else "",
                    kind, duration(c.durationS))
            },
        ),
    )
}
