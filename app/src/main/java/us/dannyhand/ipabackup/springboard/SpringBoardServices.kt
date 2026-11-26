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

    /** The home screen layout: a list of pages (dock first), each a list of icons and folders. */
    fun iconState(): List<*> {
        PlistService.send(conn, mapOf("command" to "getIconState", "formatVersion" to "2"))
        return PlistService.receiveAny(conn, 30_000) as? List<*> ?: throw java.io.IOException("Unexpected icon state reply")
    }

    /** Rearranges the home screen. SpringBoard sends no reply. */
    fun setIconState(state: List<*>) {
        PlistService.send(conn, mapOf("command" to "setIconState", "iconState" to state))
    }

    /** The home screen wallpaper as PNG (iOS 4.2 and later). */
    fun homeWallpaperPng(): ByteArray? {
        PlistService.send(conn, mapOf("command" to "getHomeScreenWallpaperPNGData"))
        return PlistService.receive(conn, 30_000)["pngData"] as? ByteArray
    }
}
