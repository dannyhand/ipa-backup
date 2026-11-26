package us.dannyhand.ipabackup.certs

import us.dannyhand.ipabackup.service.ByteConnection
import us.dannyhand.ipabackup.service.PlistService
import java.io.IOException

/**
 * com.apple.mobile.MCInstall: the profile service Apple's iPhone Configuration Utility and Apple
 * Configurator used to install profiles over USB (see pymobiledevice3's mobile_config). On a device
 * that isn't supervised, the iPod then shows the usual "Install Profile" screen for the user to accept.
 */
class McInstall(private val conn: ByteConnection) {
    companion object { const val SERVICE = "com.apple.mobile.MCInstall" }

    private fun request(msg: Map<String, Any?>): Map<String, Any?> {
        PlistService.send(conn, msg)
        val reply = PlistService.receive(conn, timeoutMs = 60_000)
        if (reply["Status"] != "Acknowledged") {
            val chain = (reply["ErrorChain"] as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.get("LocalizedDescription") as? String }
            throw IOException(chain?.joinToString(" — ")?.ifEmpty { null } ?: "MCInstall said: $reply")
        }
        return reply
    }

    fun installProfile(profile: ByteArray) {
        request(mapOf("RequestType" to "InstallProfile", "Payload" to profile))
    }

    /** Identifiers of installed profiles, or null if this iOS version doesn't answer. */
    fun installedIdentifiers(): List<String>? = runCatching {
        (request(mapOf("RequestType" to "GetProfileList"))["OrderedIdentifiers"] as? List<*>)?.filterIsInstance<String>()
    }.getOrNull()
}
