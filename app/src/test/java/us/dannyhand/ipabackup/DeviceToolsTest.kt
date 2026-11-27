package us.dannyhand.ipabackup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import us.dannyhand.ipabackup.device.DeviceReport
import us.dannyhand.ipabackup.device.HomeScreen
import us.dannyhand.ipabackup.jailbreak.Dpkg
import us.dannyhand.ipabackup.media.BookImport
import us.dannyhand.ipabackup.personal.CallHistory
import us.dannyhand.ipabackup.personal.CallHistoryHtml
import us.dannyhand.ipabackup.personal.ContactNames
import us.dannyhand.ipabackup.personal.SafariHistory
import us.dannyhand.ipabackup.plist.Plist
import us.dannyhand.ipabackup.util.CpBitmap
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.sql.DriverManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class CpBitmapTest {
    private fun make(w: Int, h: Int, stride: Int, pixel: (Int, Int) -> IntArray): ByteArray {
        val bb = ByteBuffer.allocate(stride * h + 20).order(ByteOrder.LITTLE_ENDIAN)
        for (y in 0 until h) for (x in 0 until w) {
            val (b, g, r, a) = pixel(x, y)
            bb.put(y * stride + x * 4, b.toByte()); bb.put(y * stride + x * 4 + 1, g.toByte())
            bb.put(y * stride + x * 4 + 2, r.toByte()); bb.put(y * stride + x * 4 + 3, a.toByte())
        }
        bb.putInt(stride * h, w).putInt(stride * h + 4, h)
        return bb.array()
    }

    @Test fun decodesBgraWithRowPadding() {
        // 3 px wide, rows padded to 8 px (32 bytes), like SpringBoard's aligned rows.
        val data = make(3, 2, 32) { x, y -> intArrayOf(x * 10, y * 20, 200, 0) }
        val img = CpBitmap.decode(data)!!
        assertEquals(3, img.width); assertEquals(2, img.height)
        assertEquals(0xFFC81414.toInt(), img.argb[1 * 3 + 2]) // r=200 g=20 b=20, alpha forced opaque
    }

    @Test fun rejectsGarbage() {
        assertNull(CpBitmap.decode(ByteArray(10)))
        assertNull(CpBitmap.decode(ByteArray(1000) { 7 }))
    }
}

class DpkgTest {
    private val status = """
Package: base
Status: install ok installed
Section: System
Version: 1-5

Package: com.example.removed
Status: deinstall ok config-files
Name: Gone
Version: 1.0

Package: net.r-ch.iCleaner
Status: install ok installed
Name: iCleaner
Version: 7.0.4
Section: Utilities
Author: Ivano Bilenchi <ivano@example.com>
Description: Clean your device
 second line of description

Package: cydia
Status: install ok installed
Section: Packaging
Version: 1.1.9
""".trimIndent()

    @Test fun parsesInstalledPackagesOnly() {
        val p = Dpkg.parseStatus(status)
        assertEquals(listOf("base", "cydia", "iCleaner"), p.map { it.name })
        val ic = p.first { it.id == "net.r-ch.iCleaner" }
        assertEquals("7.0.4", ic.version); assertEquals("Ivano Bilenchi", ic.author); assertEquals("Clean your device", ic.description)
        assertEquals(listOf("net.r-ch.iCleaner"), p.filter { !Dpkg.isSystem(it) }.map { it.id })
    }

    @Test fun sourcesAndText() {
        val src = Dpkg.parseSources("# comment\ndeb http://apt.thebigboss.org/repofiles/cydia/ stable main\ndeb http://repo666.ultrasn0w.com/ ./\n")
        assertEquals(listOf("http://apt.thebigboss.org/repofiles/cydia/", "http://repo666.ultrasn0w.com/"), src)
        val txt = Dpkg.text(Dpkg.parseStatus(status), src)
        assertTrue(txt.contains("net.r-ch.iCleaner  # iCleaner 7.0.4"))
        assertTrue(Dpkg.html("iPod", Dpkg.parseStatus(status), src, "2026-11-26").contains("1 tweaks and apps, 2 system packages, 2 sources"))
    }
}

class HistoryTest {
    @Test fun safariHistoryFromPlistMap() {
        // 2026-01-01T00:00:00Z = 788918400 s after 2001-01-01.
        val plist = mapOf("WebHistoryDates" to listOf(
            mapOf("" to "http://old.example/", "title" to "Old", "lastVisitedDate" to "700000000.0", "visitCount" to 2L),
            mapOf("" to "https://new.example/a?b=1&c=2", "title" to "", "lastVisitedDate" to "788918400.5", "visitCount" to 5L),
        ))
        val v = SafariHistory.parse(plist)
        assertEquals("https://new.example/a?b=1&c=2", v[0].url)
        assertEquals(1767225600500L, v[0].lastMs)
        assertEquals(5, v[0].count)
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val html = SafariHistory.html("iPod", v, fmt)
        assertTrue(html.contains("a?b=1&amp;c=2"))
        assertTrue(html.contains("2026-01-01"))
        assertTrue(SafariHistory.parse(mapOf("x" to 1)).isEmpty())
    }

    @Test fun callHistory() {
        val c = DriverManager.getConnection("jdbc:sqlite::memory:")
        c.createStatement().use { st ->
            st.execute("CREATE TABLE call (ROWID INTEGER PRIMARY KEY, address TEXT, date INTEGER, duration INTEGER, flags INTEGER, id INTEGER)")
            st.execute("INSERT INTO call VALUES (1, 'friend@example.com', 1700000000, 125, 21, -1)") // FaceTime outgoing
            st.execute("INSERT INTO call VALUES (2, '2285550100', 1700000100, 0, 4, -1)") // missed
        }
        val calls = CallHistory(JdbcSqlDb(c)).load()
        assertEquals(2, calls.size)
        assertEquals("2285550100", calls[0].address) // newest first
        assertFalse(calls[0].outgoing)
        assertTrue(calls[1].faceTime && calls[1].outgoing)
        val html = CallHistoryHtml.html("iPod", calls, ContactNames(emptyList()), SimpleDateFormat("yyyy-MM-dd", Locale.US))
        assertTrue(html.contains("missed")); assertTrue(html.contains("FaceTime, outgoing")); assertTrue(html.contains("2:05"))
    }

    @Test fun noCallTable() {
        val c = DriverManager.getConnection("jdbc:sqlite::memory:")
        assertTrue(CallHistory(JdbcSqlDb(c)).load().isEmpty())
    }
}

class DeviceReportTest {
    @Test fun untrackedGaugeAndRawStorage() {
        val r = DeviceReport.text(DeviceReport.Input(
            info = mapOf("ProductType" to "iPod4,1"),
            disk = mapOf("TotalDataCapacity" to 58_040_000_000L, "TotalDataAvailable" to 11_210_000_000L, "AmountDataReserved" to 209_715_200L, "NANDInfo" to byteArrayOf(1, 2)),
            battery = null, gasGauge = mapOf("CycleCount" to 0L, "DesignCapacity" to 930L, "FullChargeCapacity" to 930L),
            powerSource = null, jailbreak = "no", deviceTime = null,
        ))
        assertTrue(r, r.contains("Health                  unknown"))
        assertTrue(r, r.contains("  " + "AmountDataReserved".padEnd(22) + "200.0 MB (209715200)"))
        assertTrue(r.contains("  " + "NANDInfo".padEnd(22) + "2 bytes of data"))
    }

    @Test fun formatsReport() {
        val r = DeviceReport.text(DeviceReport.Input(
            info = mapOf("DeviceName" to "Danny's iPod", "ProductType" to "iPod4,1", "HardwareModel" to "N81AP",
                "ProductVersion" to "6.1.6", "BuildVersion" to "10B500", "SerialNumber" to "ABC123"),
            disk = mapOf("TotalDiskCapacity" to 32_000_000_000L, "TotalDataCapacity" to 30_000_000_000L, "TotalDataAvailable" to 10_000_000_000L),
            battery = mapOf("BatteryCurrentCapacity" to 87L, "BatteryIsCharging" to true),
            gasGauge = mapOf("CycleCount" to 512L, "DesignCapacity" to 930L, "FullChargeCapacity" to 744L),
            powerSource = null,
            jailbreak = "yes (AFC2 installed, Cydia present)",
            deviceTime = 0L,
        ), generatedMs = 1_790_000_000_000L)
        assertTrue(r, r.contains("iPod touch (4th generation) (iPod4,1, N81AP)"))
        assertTrue(r.contains("6.1.6 (10B500)"))
        assertTrue(r.contains("Cycle count             512"))
        assertTrue(r.contains("80% of design capacity"))
        assertTrue(r.contains("Other storage values") == false)
        assertTrue(r.contains("87%"))
        assertTrue(r.contains("⚠ wrong date"))
        assertTrue(r.contains("27.94 GB, 9.31 GB free"))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeScreenTest {
    private val state = listOf(
        listOf(mapOf("displayIdentifier" to "com.apple.mobilesafari", "displayName" to "Safari")),
        listOf(
            mapOf("displayIdentifier" to "com.example.game", "displayName" to "Game", "iconModDate" to Date(1_700_000_000_000L)),
            mapOf("listType" to "folder", "displayName" to "Utilities", "iconLists" to listOf(listOf(
                mapOf("displayIdentifier" to "com.apple.calculator", "displayName" to "Calculator"),
                mapOf("displayIdentifier" to "com.apple.compass", "displayName" to "Compass"),
            ))),
        ),
    )

    @Test fun summaryAndCounts() {
        assertEquals(Triple(1, 1, 4), HomeScreen.counts(state))
        val s = HomeScreen.summary(state)
        assertTrue(s.startsWith("Dock\n  Safari\nPage 1\n  Game\n  📁 Utilities\n      Calculator\n"))
    }

    @Test fun roundTripsThroughXmlPlistKeepingDates() {
        val back = Plist.parse(Plist.toXml(state).toByteArray())
        assertTrue(HomeScreen.looksValid(back))
        val game = ((back as List<*>)[1] as List<*>)[0] as Map<*, *>
        assertEquals(Date(1_700_000_000_000L), game["iconModDate"])
        assertNotNull(Plist.toXml(back))
        assertFalse(HomeScreen.looksValid(mapOf("a" to 1)))
        assertFalse(HomeScreen.looksValid(listOf("x")))
    }
}

class BookImportTest {
    private fun epub(opf: String, containerPath: String = "OEBPS/content.opf"): ByteArray {
        val bos = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(bos).use { z ->
            fun put(n: String, t: String) { z.putNextEntry(java.util.zip.ZipEntry(n)); z.write(t.toByteArray()); z.closeEntry() }
            put("mimetype", "application/epub+zip")
            put("META-INF/container.xml", "<container><rootfiles><rootfile full-path=\"$containerPath\" media-type=\"application/oebps-package+xml\"/></rootfiles></container>")
            put(containerPath, opf)
            put("OEBPS/ch1.xhtml", "<html/>")
        }
        return bos.toByteArray()
    }

    @Test fun readsTitleAndAuthor() {
        val b = epub("<package><metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:title>Pride &amp; Prejudice</dc:title>" +
            "<dc:creator opf:role=\"aut\">Jane Austen</dc:creator></metadata></package>")
        val i = BookImport.inspect(b, "pp.epub")
        assertEquals("Pride & Prejudice", i.title); assertEquals("Jane Austen", i.author); assertFalse(i.isPdf)
        assertTrue(BookImport.isEpubZip(b))
    }

    @Test fun fallsBackToFileNameAndDetectsPdf() {
        assertEquals("My Book", BookImport.inspect(epub("<package/>"), "My Book.epub").title)
        val pdf = BookImport.inspect("%PDF-1.4 ...".toByteArray(), "manual.pdf")
        assertTrue(pdf.isPdf); assertEquals("manual", pdf.title)
    }

    @Test fun addsEntryKeepingExisting() {
        val existing = mapOf("Books" to listOf(mapOf("Name" to "Old", "Artist" to "A", "Path" to "1.epub")), "Other" to 1L)
        val info = BookImport.Info("New", "B", false)
        assertFalse(BookImport.alreadyThere(existing, info))
        val pid = BookImport.newPersistentId(kotlin.random.Random(1))
        assertTrue(pid.matches(Regex("[0-9A-F]{16}")))
        val out = BookImport.withBook(existing, info, pid, byteArrayOf(1, 2, 3))
        val books = out["Books"] as List<*>
        assertEquals(2, books.size); assertEquals(1L, out["Other"])
        val e = books[1] as Map<*, *>
        assertEquals("$pid.epub", e["Path"]); assertEquals("New", e["Name"]); assertEquals("B", e["Artist"])
        assertTrue(BookImport.alreadyThere(out, info))
        assertEquals(1, (BookImport.withBook(null, info, pid, byteArrayOf())["Books"] as List<*>).size)
    }
}
