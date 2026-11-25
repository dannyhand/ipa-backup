package us.dannyhand.ipabackup.springboard

import us.dannyhand.ipabackup.service.ByteConnection
import us.dannyhand.ipabackup.service.PlistService

/** com.apple.springboardservices (see libimobiledevice src/sbservices.c). */
class SpringBoardServices(private val conn: ByteConnection) {
    companion object { const val SERVICE = "com.apple.springboardservices" }

    /** The app's home-screen icon as PNG bytes, or null if SpringBoard has none. */
    fun iconPng(bundleId: String): ByteArray? {
        PlistService.send(conn, mapOf("command" to "getIconPNGData", "bundleId" to bundleId))
        return PlistService.receive(conn)["pngData"] as? ByteArray
    }
}
