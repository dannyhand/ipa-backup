package us.dannyhand.ipabackup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import us.dannyhand.ipabackup.afc.AfcClient
import us.dannyhand.ipabackup.afc.unzipInto
import us.dannyhand.ipabackup.plist.BinaryPlist
import us.dannyhand.ipabackup.util.IpaInfo
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RestoreTest {
    private fun zip(vararg entries: Pair<String, ByteArray?>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            for ((name, data) in entries) {
                z.putNextEntry(ZipEntry(name))
                data?.let { z.write(it) }
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /** Binary Info.plist made with Python plistlib (iOS apps ship binary plists). */
    private val infoPlist = Base64.getDecoder().decode("YnBsaXN0MDDVAQIDBAUGBwgJCl8QE0NGQnVuZGxlRGlzcGxheU5hbWVfEBJDRkJ1bmRsZUlkZW50aWZpZXJfEBpDRkJ1bmRsZVNob3J0VmVyc2lvblN0cmluZ18QD0NGQnVuZGxlVmVyc2lvbl8QEE1pbmltdW1PU1ZlcnNpb25ZVGlueSBHYW1lXxAUY29tLmV4YW1wbGUudGlueWdhbWVTMi4xUzIxMFM0LjMIEyk+W22AiqGlqQAAAAAAAAEBAAAAAAAAAAsAAAAAAAAAAAAAAAAAAACt")

    @Test
    fun readsIpaInfo() {
        val ipa = zip(
            "Payload/" to null,
            "Payload/TinyGame.app/TinyGame" to ByteArray(5000) { 1 },
            "Payload/TinyGame.app/Info.plist" to infoPlist,
            "Payload/TinyGame.app/Plugins/Ext.appex/Info.plist" to "x".toByteArray(), // nested plist must be ignored
            "Payload/TinyGame.app/SC_Info/TinyGame.sinf" to "SINF".toByteArray(),
            "iTunesMetadata.plist" to "<plist/>".toByteArray(),
        )
        val info = IpaInfo.read(ipa.inputStream()) { BinaryPlist.parse(it) }
        assertEquals("com.example.tinygame", info.bundleId)
        assertEquals("Tiny Game", info.name)
        assertEquals("2.1", info.version)
        assertEquals("4.3", info.minimumOs)
        assertArrayEquals("SINF".toByteArray(), info.sinf)
        assertArrayEquals("<plist/>".toByteArray(), info.iTunesMetadata)
    }

    @Test
    fun osVersionCheck() {
        assertFalse(IpaInfo.needsNewerOs("4.3", "6.1.6"))
        assertFalse(IpaInfo.needsNewerOs("6.1", "6.1.6"))
        assertTrue(IpaInfo.needsNewerOs("7.0", "6.1.6"))
        assertTrue(IpaInfo.needsNewerOs("6.1.7", "6.1.6"))
        assertFalse(IpaInfo.needsNewerOs(null, "6.1.6"))
    }

    @Test
    fun restoresDataZipSafely() {
        val device = FakeAfcDevice(mapOf("/Documents/old.sav" to "old".toByteArray(), "/Documents/keep.txt" to "keep".toByteArray()))
        val afc = AfcClient(device)
        val data = zip(
            "Documents/" to null,
            "Documents/old.sav" to "new progress".toByteArray(),
            "Library/Preferences/com.example.tinygame.plist" to "prefs".toByteArray(),
            "../../etc/evil" to "no".toByteArray(),
            "Documents/../../evil2" to "no".toByteArray(),
            "tmp/junk" to "no".toByteArray(),
        )
        val n = afc.unzipInto(data.inputStream(), "/", setOf("Documents", "Library"))
        assertEquals(2, n)
        assertArrayEquals("new progress".toByteArray(), device.files["/Documents/old.sav"])
        assertArrayEquals("prefs".toByteArray(), device.files["/Library/Preferences/com.example.tinygame.plist"])
        assertArrayEquals("keep".toByteArray(), device.files["/Documents/keep.txt"]) // untouched
        assertTrue(device.files.keys.none { "evil" in it || "junk" in it })
    }
}
