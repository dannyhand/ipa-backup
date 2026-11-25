package us.dannyhand.ipabackup.personal

import us.dannyhand.ipabackup.music.SqlDb
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Reads /var/mobile/Library/Calendar/Calendar.sqlitedb (needs AFC2). iOS 6 keeps events in
 * CalendarItem (older versions: Event), calendars in Calendar, repeat rules in Recurrence.
 * Dates are seconds since 2001; all-day events are "floating" (midnight, no time zone).
 */
class CalendarDb(private val db: SqlDb) {
    companion object { const val DB_PATH = "/var/mobile/Library/Calendar/Calendar.sqlitedb" }

    data class Recurrence(val frequency: String, val interval: Int, val count: Int, val untilMs: Long?)

    data class Event(
        val id: Long,
        val uid: String?,
        val summary: String,
        val location: String?,
        val description: String?,
        val url: String?,
        val startMs: Long,
        val endMs: Long?,
        val allDay: Boolean,
        val recurrence: Recurrence?,
    )

    data class Calendar(val id: Long, val title: String, val events: List<Event>)

    private fun appleMs(v: Any?): Long? = ((v as? Number)?.toDouble() ?: (v as? String)?.toDoubleOrNull())
        ?.let { AddressBook.APPLE_EPOCH_MS + (it * 1000).toLong() }

    fun load(): List<Calendar> {
        val tables = db.tables()
        val table = listOf("CalendarItem", "Event").firstOrNull { it in tables }
            ?: throw IOException("No CalendarItem/Event table. Tables: $tables")
        val cols = db.columns(table)
        fun c(vararg names: String) = names.firstOrNull { it in cols }?.let { "e.$it" } ?: "NULL"

        val joins = StringBuilder()
        var location = c("location")
        if (location == "NULL" && "location_id" in cols && "Location" in tables && "title" in db.columns("Location")) {
            joins.append(" LEFT JOIN Location l ON l.ROWID = e.location_id")
            location = "l.title"
        }
        val rows = db.query(
            "SELECT e.ROWID, ${c("UUID", "unique_identifier")}, ${c("summary", "title")}, $location, " +
                "${c("description", "notes")}, ${c("url")}, ${c("start_date")}, ${c("end_date")}, ${c("all_day")}, " +
                "${c("calendar_id")} FROM $table e$joins"
        )

        val recurrences = HashMap<Long, Recurrence>()
        if ("Recurrence" in tables) {
            val rc = db.columns("Recurrence")
            if ("owner_id" in rc && "frequency" in rc) {
                fun r(n: String) = if (n in rc) n else "NULL"
                for (r in db.query("SELECT owner_id, frequency, ${r("interval")}, ${r("count")}, ${r("end_date")} FROM Recurrence")) {
                    val owner = (r[0] as? Number)?.toLong() ?: continue
                    val freq = when ((r[1] as? Number)?.toInt()) { 1 -> "DAILY"; 2 -> "WEEKLY"; 3 -> "MONTHLY"; 4 -> "YEARLY"; else -> null } ?: continue
                    recurrences[owner] = Recurrence(freq, (r[2] as? Number)?.toInt()?.coerceAtLeast(1) ?: 1,
                        (r[3] as? Number)?.toInt() ?: 0, appleMs(r[4])?.takeIf { (r[4] as? Number)?.toDouble() != 0.0 })
                }
            }
        }

        val calendarNames: Map<Long, String> = if ("Calendar" in tables && "title" in db.columns("Calendar"))
            db.query("SELECT ROWID, title FROM Calendar").associate { (it[0] as Number).toLong() to ((it[1] as? String) ?: "Calendar") }
        else emptyMap()

        val events = rows.mapNotNull { r ->
            val start = appleMs(r[6]) ?: return@mapNotNull null // reminders without dates live here too
            val id = (r[0] as Number).toLong()
            ((r[9] as? Number)?.toLong() ?: -1L) to Event(
                id = id,
                uid = (r[1] as? String)?.ifEmpty { null },
                summary = (r[2] as? String)?.trim()?.ifEmpty { null } ?: "(No title)",
                location = (r[3] as? String)?.trim()?.ifEmpty { null },
                description = (r[4] as? String)?.trim()?.ifEmpty { null },
                url = (r[5] as? String)?.trim()?.ifEmpty { null },
                startMs = start,
                endMs = appleMs(r[7]),
                allDay = ((r[8] as? Number)?.toInt() ?: 0) != 0,
                recurrence = recurrences[id],
            )
        }
        return events.groupBy({ it.first }, { it.second })
            .map { (calId, evs) -> Calendar(calId, calendarNames[calId] ?: "Calendar", evs.sortedBy { it.startMs }) }
            .sortedBy { it.title.lowercase() }
    }
}

/** iCalendar (RFC 5545) output, importable into Google Calendar, Outlook and Apple Calendar. */
object Ics {
    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\r", "").replace("\n", "\\n").replace(",", "\\,").replace(";", "\\;")

    private fun fold(line: String): String {
        if (line.length <= 75) return line
        val sb = StringBuilder(line.substring(0, 75))
        var i = 75
        while (i < line.length) { sb.append("\r\n ").append(line, i, minOf(i + 74, line.length)); i += 74 }
        return sb.toString()
    }

    private fun utc(pattern: String) = SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

    fun write(cal: CalendarDb.Calendar, deviceName: String, nowMs: Long = System.currentTimeMillis()): String {
        val stamp = utc("yyyyMMdd'T'HHmmss'Z'")
        val day = utc("yyyyMMdd")
        val lines = ArrayList<String>()
        lines += "BEGIN:VCALENDAR"
        lines += "VERSION:2.0"
        lines += "PRODID:-//dannyhand.us//IPA Backup//EN"
        lines += "CALSCALE:GREGORIAN"
        lines += "X-WR-CALNAME:" + esc(cal.title)
        for (e in cal.events) {
            lines += "BEGIN:VEVENT"
            lines += "UID:" + esc(e.uid ?: "ipabackup-${e.id}") + "@" + esc(deviceName.replace(" ", "-"))
            lines += "DTSTAMP:" + stamp.format(Date(nowMs))
            if (e.allDay) {
                // Floating dates: the stored instant is midnight as if in UTC.
                val dayMs = 86_400_000L
                val startDay = Math.floorDiv(e.startMs, dayMs)
                // DTEND is exclusive. iOS ends all-day events at 23:59:59 (-> next day) or exactly at midnight.
                val endDay = e.endMs?.let { end -> if (Math.floorMod(end, dayMs) == 0L) end / dayMs else Math.floorDiv(end, dayMs) + 1 }
                    ?.coerceAtLeast(startDay + 1) ?: (startDay + 1)
                lines += "DTSTART;VALUE=DATE:" + day.format(Date(startDay * dayMs))
                lines += "DTEND;VALUE=DATE:" + day.format(Date(endDay * dayMs))
            } else {
                lines += "DTSTART:" + stamp.format(Date(e.startMs))
                lines += "DTEND:" + stamp.format(Date(maxOf(e.endMs ?: e.startMs, e.startMs)))
            }
            lines += "SUMMARY:" + esc(e.summary)
            e.location?.let { lines += "LOCATION:" + esc(it) }
            e.description?.let { lines += "DESCRIPTION:" + esc(it) }
            e.url?.let { lines += "URL:" + esc(it) }
            e.recurrence?.let { r ->
                val parts = mutableListOf("FREQ=${r.frequency}")
                if (r.interval > 1) parts += "INTERVAL=${r.interval}"
                if (r.count > 0) parts += "COUNT=${r.count}" else r.untilMs?.let { parts += "UNTIL=" + stamp.format(Date(it)) }
                lines += "RRULE:" + parts.joinToString(";")
            }
            lines += "END:VEVENT"
        }
        lines += "END:VCALENDAR"
        return lines.joinToString("") { fold(it) + "\r\n" }
    }
}
