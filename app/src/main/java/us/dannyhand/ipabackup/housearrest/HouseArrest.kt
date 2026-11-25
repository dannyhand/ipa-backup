package us.dannyhand.ipabackup.housearrest

import us.dannyhand.ipabackup.service.ByteConnection
import us.dannyhand.ipabackup.service.PlistService
import java.io.IOException

/**
 * com.apple.mobile.house_arrest: after VendContainer, the same connection speaks AFC,
 * rooted at the app's sandbox (Documents/, Library/, tmp/ and, before iOS 8, the .app itself).
 */
object HouseArrest {
    const val SERVICE = "com.apple.mobile.house_arrest"

    fun vendContainer(conn: ByteConnection, bundleId: String) {
        PlistService.send(conn, mapOf("Command" to "VendContainer", "Identifier" to bundleId))
        val r = PlistService.receive(conn)
        r["Error"]?.let { throw IOException("VendContainer failed: $it") }
        if (r["Status"] != "Complete") throw IOException("VendContainer: unexpected reply $r")
    }
}
