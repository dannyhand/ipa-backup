package us.dannyhand.ipabackup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import us.dannyhand.ipabackup.personal.AddressBook
import us.dannyhand.ipabackup.personal.CalendarDb
import us.dannyhand.ipabackup.personal.ContactNames
import us.dannyhand.ipabackup.personal.Ics
import us.dannyhand.ipabackup.personal.MessagesDb
import us.dannyhand.ipabackup.personal.MessagesHtml
import java.sql.DriverManager
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class CalendarMessagesTest {
    private fun db(vararg sql: String) = JdbcSqlDb(DriverManager.getConnection("jdbc:sqlite::memory:").also { c ->
        sql.forEach { c.createStatement().execute(it) }
    })

    /** Seconds since 2001-01-01 UTC for a UTC date-time. */
    private fun apple(y: Int, mo: Int, d: Int, h: Int = 0, mi: Int = 0, s: Int = 0): Long {
        val f = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        return f.parse("%04d-%02d-%02d %02d:%02d:%02d".format(y, mo, d, h, mi, s))!!.time / 1000 - 978_307_200L
    }

    @Test
    fun calendarToIcs() {
        val c = db(
            "CREATE TABLE Calendar (ROWID INTEGER PRIMARY KEY, title TEXT)",
            "CREATE TABLE Location (ROWID INTEGER PRIMARY KEY, title TEXT)",
            "CREATE TABLE CalendarItem (ROWID INTEGER PRIMARY KEY, summary TEXT, location_id INTEGER, description TEXT, start_date REAL, end_date REAL, all_day INTEGER, calendar_id INTEGER, UUID TEXT)",
            "CREATE TABLE Recurrence (ROWID INTEGER PRIMARY KEY, frequency INTEGER, interval INTEGER, count INTEGER, end_date REAL, owner_id INTEGER)",
            "INSERT INTO Calendar VALUES (1, 'Home'), (2, 'Work')",
            "INSERT INTO Location VALUES (5, 'Ingalls; Gate 2')",
            "INSERT INTO CalendarItem VALUES (10, 'Dentist', NULL, 'Bring card,\nand forms', ${apple(2013, 6, 3, 14, 30)}, ${apple(2013, 6, 3, 15, 0)}, 0, 1, 'AAA-1')",
            "INSERT INTO CalendarItem VALUES (11, 'Birthday', NULL, NULL, ${apple(2013, 7, 4)}, ${apple(2013, 7, 4, 23, 59, 59)}, 1, 1, NULL)",
            "INSERT INTO CalendarItem VALUES (12, 'Toolbox talk', 5, NULL, ${apple(2013, 6, 5, 12)}, ${apple(2013, 6, 5, 12, 15)}, 0, 2, 'BBB-2')",
            "INSERT INTO CalendarItem VALUES (13, 'A reminder', NULL, NULL, NULL, NULL, 0, 1, NULL)",
            "INSERT INTO Recurrence VALUES (1, 4, 1, 0, 0, 11), (2, 2, 2, 10, 0, 12)",
        )
        val cals = CalendarDb(c).load()
        assertEquals(listOf("Home", "Work"), cals.map { it.title })
        assertEquals(2, cals[0].events.size) // dateless reminder skipped

        val home = Ics.write(cals[0], "Danny Hand Jr", nowMs = 0)
        assertTrue(home, home.contains("X-WR-CALNAME:Home\r\n"))
        assertTrue(home.contains("UID:AAA-1@Danny-Hand-Jr\r\n"))
        assertTrue(home.contains("DTSTART:20130603T143000Z\r\nDTEND:20130603T150000Z\r\n"))
        assertTrue(home.contains("DESCRIPTION:Bring card\\,\\nand forms\r\n"))
        assertTrue(home.contains("DTSTART;VALUE=DATE:20130704\r\nDTEND;VALUE=DATE:20130705\r\n"))
        assertTrue(home.contains("RRULE:FREQ=YEARLY\r\n"))
        assertEquals(2, Regex("BEGIN:VEVENT").findAll(home).count())

        val work = Ics.write(cals[1], "iPod", nowMs = 0)
        assertTrue(work.contains("LOCATION:Ingalls\\; Gate 2\r\n"))
        assertTrue(work.contains("RRULE:FREQ=WEEKLY;INTERVAL=2;COUNT=10\r\n"))
        assertTrue(work.startsWith("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n"))
        assertTrue(work.trimEnd().endsWith("END:VCALENDAR"))
    }

    @Test
    fun messagesToHtml() {
        val c = db(
            "CREATE TABLE handle (ROWID INTEGER PRIMARY KEY, id TEXT, service TEXT)",
            "CREATE TABLE chat (ROWID INTEGER PRIMARY KEY, guid TEXT, chat_identifier TEXT, service_name TEXT, display_name TEXT)",
            "CREATE TABLE message (ROWID INTEGER PRIMARY KEY, text TEXT, handle_id INTEGER, service TEXT, date INTEGER, is_from_me INTEGER, subject TEXT)",
            "CREATE TABLE chat_message_join (chat_id INTEGER, message_id INTEGER)",
            "CREATE TABLE chat_handle_join (chat_id INTEGER, handle_id INTEGER)",
            "CREATE TABLE attachment (ROWID INTEGER PRIMARY KEY, filename TEXT, mime_type TEXT, transfer_name TEXT, total_bytes INTEGER)",
            "CREATE TABLE message_attachment_join (message_id INTEGER, attachment_id INTEGER)",
            "INSERT INTO handle VALUES (1, '+12285550100', 'iMessage'), (2, 'pal@example.com', 'iMessage'), (3, '+15551234567', 'iMessage')",
            "INSERT INTO chat VALUES (1, 'g1', '+12285550100', 'iMessage', NULL), (2, 'g2', 'chat123', 'iMessage', 'Crew <Night>')",
            "INSERT INTO chat_handle_join VALUES (1, 1), (2, 2), (2, 3)",
            "INSERT INTO message VALUES (1, 'Hey!', 1, 'iMessage', ${apple(2013, 6, 1, 15)}, 0, NULL)",
            "INSERT INTO message VALUES (2, 'Look at this ￼', 0, 'iMessage', ${apple(2013, 6, 1, 15, 1)}, 1, NULL)",
            "INSERT INTO message VALUES (3, 'A & B <ok>', 2, 'iMessage', ${apple(2013, 6, 2, 9)}, 0, NULL)",
            "INSERT INTO message VALUES (4, 'Me too', 3, 'iMessage', ${apple(2013, 6, 2, 9, 5)}, 0, NULL)",
            "INSERT INTO chat_message_join VALUES (1, 1), (1, 2), (2, 3), (2, 4)",
            "INSERT INTO attachment VALUES (7, '~/Library/SMS/Attachments/ab/12/G/IMG_0001.JPG', 'image/jpeg', 'IMG_0001.JPG', 12345)",
            "INSERT INTO message_attachment_join VALUES (2, 7)",
        )
        val mdb = MessagesDb(c)
        val chats = mdb.load()
        assertEquals(listOf(2L, 1L), chats.map { it.id }) // most recent conversation first
        val one = chats.first { it.id == 1L }
        assertEquals(listOf("Hey!", "Look at this"), one.messages.map { it.text })
        val att = one.messages[1].attachments.single()
        assertEquals("/var/mobile/Library/SMS/Attachments/ab/12/G/IMG_0001.JPG", att.devicePath)
        assertTrue(att.isImage)

        // Contact names: phone matched on last 10 digits regardless of formatting.
        val jane = AddressBook.Contact(1, "Jane", "Doe", null, null, null, null, null, null, null, null, null,
            listOf(AddressBook.Labeled("Mobile", "(228) 555-0100")), emptyList(), emptyList(), emptyList())
        val names = ContactNames(listOf(jane))
        assertEquals("Jane Doe", MessagesHtml.title(one, names))
        assertEquals("Crew <Night>", MessagesHtml.title(chats[0], names))

        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val tfmt = SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val withLinks = MessagesHtml.conversation(one, names, fmt, tfmt) { "attachments/${it.id}-${it.name}" }
        assertTrue(withLinks, withLinks.contains("<img loading=\"lazy\" src=\"attachments/7-IMG_0001.JPG\""))
        assertTrue(withLinks.contains("<div class=\"m me\">"))
        assertTrue(withLinks.contains("<div class=\"day\">2013-06-01</div>"))
        val noLinks = MessagesHtml.conversation(one, names, fmt, tfmt) { null }
        assertTrue(noLinks.contains("📎 IMG_0001.JPG"))
        assertFalse(noLinks.contains("<img"))

        val group = MessagesHtml.conversation(chats[0], names, fmt, tfmt) { null }
        assertTrue(group, group.contains("A &amp; B &lt;ok&gt;"))
        assertTrue("group shows sender names", group.contains("<div class=\"who\">pal@example.com</div>") && group.contains("<div class=\"who\">+15551234567</div>"))

        val index = MessagesHtml.index("iPod", chats, names, { "chat-${it.id}.html" }, fmt)
        assertTrue(index.contains("2 conversations, 4 messages"))
        assertTrue(index.contains("<a href=\"chat-1.html\">Jane Doe</a>"))
    }
}
