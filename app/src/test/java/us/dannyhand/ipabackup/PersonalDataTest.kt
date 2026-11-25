package us.dannyhand.ipabackup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import us.dannyhand.ipabackup.afc.AfcClient
import us.dannyhand.ipabackup.afc.AfcZipper
import us.dannyhand.ipabackup.media.BooksLibrary
import us.dannyhand.ipabackup.media.VoiceMemos
import us.dannyhand.ipabackup.personal.AddressBook
import us.dannyhand.ipabackup.personal.BookmarksHtml
import us.dannyhand.ipabackup.personal.NotesDb
import us.dannyhand.ipabackup.personal.NotesExport
import us.dannyhand.ipabackup.personal.SafariBookmarks
import us.dannyhand.ipabackup.personal.VCard
import java.io.ByteArrayOutputStream
import java.sql.DriverManager
import java.text.DateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class PersonalDataTest {
    private fun db(vararg sql: String) = JdbcSqlDb(DriverManager.getConnection("jdbc:sqlite::memory:").also { c ->
        sql.forEach { c.createStatement().execute(it) }
    })

    @Test
    fun contactsToVcard() {
        val ab = db(
            "CREATE TABLE ABPerson (ROWID INTEGER PRIMARY KEY, First TEXT, Last TEXT, Middle TEXT, Organization TEXT, Department TEXT, Note TEXT, Birthday TEXT, JobTitle TEXT, Nickname TEXT, Prefix TEXT, Suffix TEXT)",
            "CREATE TABLE ABMultiValue (UID INTEGER PRIMARY KEY, record_id INTEGER, property INTEGER, identifier INTEGER, label INTEGER, value TEXT)",
            "CREATE TABLE ABMultiValueLabel (value TEXT)",
            "CREATE TABLE ABMultiValueEntry (parent_id INTEGER, key INTEGER, value TEXT)",
            "CREATE TABLE ABMultiValueEntryKey (value TEXT)",
            "INSERT INTO ABMultiValueLabel (ROWID, value) VALUES (1, '_\$!<Mobile>!\$_'), (2, '_\$!<Home>!\$_'), (3, 'Grandma''s')",
            "INSERT INTO ABMultiValueEntryKey (ROWID, value) VALUES (1, 'Street'), (2, 'City'), (3, 'State'), (4, 'ZIP'), (5, 'Country')",
            // Birthday 1990-07-04 00:00 local-ish as seconds since 2001 (negative: before 2001)
            "INSERT INTO ABPerson (ROWID, First, Last, Organization, Note, Birthday) VALUES (1, 'Jane', 'Doe', 'Ingalls', 'Line one\nLine; two, ok', '-331257600')",
            "INSERT INTO ABPerson (ROWID, Organization) VALUES (2, 'Pizza Place')",
            "INSERT INTO ABPerson (ROWID) VALUES (3)", // empty card: dropped
            "INSERT INTO ABMultiValue VALUES (10, 1, 3, 0, 1, '(228) 555-0100'), (11, 1, 4, 0, 2, 'jane@example.com'), (12, 1, 5, 0, 2, NULL), (13, 2, 3, 0, 3, '555-0199')",
            "INSERT INTO ABMultiValueEntry VALUES (12, 1, '1 Main St'), (12, 2, 'Pascagoula'), (12, 3, 'MS'), (12, 4, '39567'), (12, 5, 'USA')",
        )
        val contacts = AddressBook(ab).load()
        assertEquals(listOf("Jane Doe", "Pizza Place"), contacts.map { it.displayName })
        val v = VCard.write(contacts)
        assertTrue(v, v.contains("N:Doe;Jane;;;\r\n"))
        assertTrue(v.contains("FN:Jane Doe\r\n"))
        assertTrue(v.contains("ORG:Ingalls\r\n"))
        assertTrue(v.contains("TEL;TYPE=CELL:(228) 555-0100\r\n"))
        assertTrue(v.contains("EMAIL;TYPE=INTERNET,HOME:jane@example.com\r\n"))
        assertTrue(v.contains("ADR;TYPE=HOME:;;1 Main St;Pascagoula;MS;39567;USA\r\n"))
        assertTrue(v.contains("NOTE:Line one\\nLine\\; two\\, ok\r\n"))
        assertTrue(v, v.contains("BDAY:1990-07-04\r\n"))
        assertTrue(v.contains("FN:Pizza Place\r\nORG:Pizza Place\r\nTEL:555-0199\r\n"))
        assertEquals(2, Regex("BEGIN:VCARD").findAll(v).count())
        assertTrue("lines folded at 75", v.split("\r\n").all { it.length <= 75 })
    }

    @Test
    fun notesToHtmlAndText() {
        val n = db(
            "CREATE TABLE ZNOTE (Z_PK INTEGER PRIMARY KEY, ZTITLE TEXT, ZCREATIONDATE REAL, ZMODIFICATIONDATE REAL, ZDELETEDFLAG INTEGER)",
            "CREATE TABLE ZNOTEBODY (Z_PK INTEGER PRIMARY KEY, ZOWNER INTEGER, ZCONTENT TEXT)",
            "INSERT INTO ZNOTE VALUES (1, 'Groceries', 390000000, 390000100, 0), (2, 'Old idea', 300000000, 300000000, 0), (3, 'Trash', 1, 1, 1)",
            "INSERT INTO ZNOTEBODY VALUES (1, 1, 'Groceries<div>milk &amp; eggs</div><div>bread<br></div>'), (2, 2, 'Old idea')",
        )
        val notes = NotesDb(n).load()
        assertEquals(listOf("Groceries", "Old idea"), notes.map { it.title }) // newest first, deleted skipped
        assertEquals("Groceries\nmilk & eggs\nbread", NotesExport.toText(notes[0].html))
        val html = NotesExport.html(notes, "Danny's <iPod>", DateFormat.getDateInstance(DateFormat.SHORT, Locale.US))
        assertTrue(html.contains("Notes from Danny's &lt;iPod&gt;"))
        assertTrue(html.contains("<h2>Groceries</h2>"))
    }

    @Test
    fun safariBookmarksToNetscapeHtml() {
        val b = db(
            "CREATE TABLE bookmarks (id INTEGER PRIMARY KEY, special_id INTEGER, parent INTEGER, type INTEGER, title TEXT, url TEXT, order_index INTEGER, hidden INTEGER, deleted INTEGER)",
            "INSERT INTO bookmarks VALUES (0, 0, NULL, 1, '', NULL, 0, 0, 0)",
            "INSERT INTO bookmarks VALUES (1, 1, 0, 1, 'BookmarksBar', NULL, 0, 0, 0)",
            "INSERT INTO bookmarks VALUES (2, 0, 0, 1, 'com.apple.ReadingList', NULL, 2, 0, 0)",
            "INSERT INTO bookmarks VALUES (3, 0, 1, 0, 'Apple', 'http://apple.com/', 1, 0, 0)",
            "INSERT INTO bookmarks VALUES (4, 0, 1, 0, 'A & B <test>', 'http://example.com/?a=1&b=2', 0, 0, 0)",
            "INSERT INTO bookmarks VALUES (5, 0, 0, 0, 'Top level', 'http://top.example/', 1, 0, 0)",
            "INSERT INTO bookmarks VALUES (6, 0, 2, 0, 'Read later', 'http://later.example/', 0, 0, 0)",
            "INSERT INTO bookmarks VALUES (7, 0, 0, 0, 'Gone', 'http://deleted.example/', 3, 0, 1)",
        )
        val root = SafariBookmarks(b).load()
        assertEquals(listOf("Bookmarks Bar", "Top level", "Reading List"), root.children.map { it.title })
        assertEquals(listOf("A & B <test>", "Apple"), root.children[0].children.map { it.title }) // order_index
        assertEquals(4, root.count())
        val html = BookmarksHtml.write(root)
        assertTrue(html.startsWith("<!DOCTYPE NETSCAPE-Bookmark-file-1>"))
        assertTrue(html.contains("<DT><H3>Reading List</H3>"))
        assertTrue(html.contains("<A HREF=\"http://example.com/?a=1&amp;b=2\">A &amp; B &lt;test&gt;</A>"))
        assertFalse(html.contains("deleted.example"))
    }

    @Test
    fun voiceMemosFromDbAndNames() {
        val r = db(
            "CREATE TABLE Recording (id INTEGER PRIMARY KEY, date INTEGER, duration REAL, path TEXT, customLabel TEXT, labelPreset INTEGER)",
            // 2013-06-01 12:00:00 UTC as Unix seconds; second row uses a preset label
            "INSERT INTO Recording VALUES (1, 1370088000, 12.5, '/var/mobile/Media/Recordings/20130601 120000.m4a', 'Band practice', 0)",
            "INSERT INTO Recording VALUES (2, 1370174400, 3.0, 'Recordings/20130602 120000.m4a', NULL, 2)",
        )
        val meta = VoiceMemos.readDb(r)
        val memos = VoiceMemos.combine(listOf(
            VoiceMemos.FileEntry("20130601 120000.m4a", 1000, 0),
            VoiceMemos.FileEntry("20130602 120000.m4a", 2000, 0),
            VoiceMemos.FileEntry("20120101 080910.m4a", 3000, 0), // not in DB: date from name
            VoiceMemos.FileEntry("Recordings.db", 5, 0),
        ), meta)
        assertEquals(3, memos.size)
        val tz = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            assertEquals("2013-06-02 12.00.00 - Interview.m4a", memos[0].saveName())
            assertEquals("2013-06-01 12.00.00 - Band practice.m4a", memos[1].saveName())
            assertEquals(12.5, memos[1].durationSec!!, 0.001)
        } finally {
            TimeZone.setDefault(tz)
        }
        assertEquals("20120101 080910.m4a", memos[2].fileName)
        assertEquals(VoiceMemos.dateFromName("20120101 080910.m4a"), memos[2].dateMs)
    }

    @Test
    fun booksPlist() {
        val plist = mapOf("Books" to listOf(
            mapOf("Name" to "Moby Dick", "Artist" to "Herman Melville", "Path" to "1A2B.epub"),
            mapOf("Name" to "Manual", "Path" to "manual.pdf"),
            mapOf("Name" to "No path"),
        ))
        val metas = BooksLibrary.parsePlist(plist, "/Books")
        assertEquals(listOf("/Books/1A2B.epub", "/Books/manual.pdf"), metas.map { it.relPath })
        val book = BooksLibrary.Book("/Books/1A2B.epub", "Moby Dick: Or, the Whale", "Herman Melville", true, 0, false)
        assertEquals("Herman Melville - Moby Dick_ Or, the Whale.epub", book.saveName())
    }

    @Test
    fun epubRepackHasStoredMimetypeFirst() {
        val tree = mapOf(
            "/Books/1A2B.epub/mimetype" to "application/epub+zip".toByteArray(),
            "/Books/1A2B.epub/META-INF/container.xml" to "<container/>".toByteArray(),
            "/Books/1A2B.epub/OEBPS/chapter1.xhtml" to "<html>Call me Ishmael.</html>".toByteArray(),
            "/Books/1A2B.epub/iTunesMetadata.plist" to "x".toByteArray(),
        )
        val afc = AfcClient(FakeAfcDevice(tree))
        val out = ByteArrayOutputStream()
        AfcZipper.open(out).use { zos ->
            val z = AfcZipper(afc, zos)
            z.addStored("/Books/1A2B.epub/mimetype", "mimetype")
            z.addTree("/Books/1A2B.epub", "") { it != "mimetype" && it != "iTunesMetadata.plist" }
        }
        val bytes = out.toByteArray()
        // EPUB OCF: the file must literally start with a stored "mimetype" entry at offset 38.
        assertEquals("mimetype", String(bytes, 30, 8))
        assertEquals("application/epub+zip", String(bytes, 38, 20))
        val names = ArrayList<String>()
        ZipInputStream(bytes.inputStream()).use { z ->
            var first = true
            while (true) {
                val e = z.nextEntry ?: break
                if (first) { assertEquals(ZipEntry.STORED, e.method); first = false }
                names += e.name
            }
        }
        assertEquals("mimetype", names[0])
        assertTrue("OEBPS/chapter1.xhtml" in names)
        assertFalse("iTunesMetadata.plist" in names)
    }
}
