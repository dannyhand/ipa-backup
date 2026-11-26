package us.dannyhand.ipabackup.springboard

import us.dannyhand.ipabackup.service.ByteConnection
import us.dannyhand.ipabackup.service.PlistService

/** com.apple.mobile.notification_proxy: posts a system notification on the device, as iTunes does after a sync. */
class NotificationProxy(private val conn: ByteConnection) {
    companion object {
        const val SERVICE = "com.apple.mobile.notification_proxy"
        const val SYNC_DID_FINISH = "com.apple.itunes-mobdev.syncDidFinish"
    }

    fun post(name: String) {
        PlistService.send(conn, mapOf("Command" to "PostNotification", "Name" to name))
        PlistService.send(conn, mapOf("Command" to "Shutdown"))
        runCatching { PlistService.receive(conn, 3_000) } // "ProxyDeath"
    }
}
