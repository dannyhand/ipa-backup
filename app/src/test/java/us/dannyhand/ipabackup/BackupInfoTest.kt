package us.dannyhand.ipabackup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import us.dannyhand.ipabackup.backup.BackupInfo
import us.dannyhand.ipabackup.plist.Plist
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupInfoTest {
    @Test fun infoPlistHasWhatItunesReads() {
        val udid = "0123456789ABCDEF0123456789ABCDEF01234567"
        val m = BackupInfo.build(udid,
            mapOf("DeviceName" to "Danny's iPod", "ProductType" to "iPod4,1", "ProductVersion" to "6.1.6", "BuildVersion" to "10B500", "SerialNumber" to "XYZ"),
            mapOf("MinITunesVersion" to "10.5"), mapOf("iTunesPrefs" to byteArrayOf(1, 2)), listOf("com.example.app"), null, Date(1_700_000_000_000L))
        val back = Plist.parse(Plist.toXml(m).toByteArray()) as Map<*, *>
        assertEquals("Danny's iPod", back["Device Name"]); assertEquals("Danny's iPod", back["Display Name"])
        assertEquals(udid.lowercase(), back["Target Identifier"]); assertEquals(udid, back["Unique Identifier"])
        assertEquals("Device", back["Target Type"]); assertEquals("iPod4,1", back["Product Type"]); assertEquals("6.1.6", back["Product Version"])
        assertEquals(Date(1_700_000_000_000L), back["Last Backup Date"])
        assertArrayEquals(byteArrayOf(1, 2), (back["iTunes Files"] as Map<*, *>)["iTunesPrefs"] as ByteArray)
        assertEquals(listOf("com.example.app"), back["Installed Applications"])
        assertTrue((back["GUID"] as String).matches(Regex("[0-9A-F]{32}")))
        assertTrue("IMEI" !in back) // left out when the device has none (iPod)
    }
}
