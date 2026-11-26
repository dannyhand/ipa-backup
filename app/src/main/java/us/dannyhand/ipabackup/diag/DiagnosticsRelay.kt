package us.dannyhand.ipabackup.diag

import us.dannyhand.ipabackup.service.ByteConnection
import us.dannyhand.ipabackup.service.PlistService
import java.io.IOException

/**
 * com.apple.mobile.diagnostics_relay (see libimobiledevice diagnostics_relay.c, idevicediagnostics).
 * Restarts, shuts down or sleeps the device, and reads battery (GasGauge) and hardware info.
 *
 * There's no "respring" here: restarting just SpringBoard needs code running on the device itself
 * (e.g. the `killall SpringBoard` that Cydia runs), which nothing reachable over USB can do.
 */
class DiagnosticsRelay(private val conn: ByteConnection) {
    companion object { const val SERVICE = "com.apple.mobile.diagnostics_relay" }

    private fun request(msg: Map<String, Any?>): Map<String, Any?> {
        PlistService.send(conn, msg)
        val r = PlistService.receive(conn, 30_000)
        val status = r["Status"] as? String
        if (status != "Success") throw IOException("${msg["Request"]}: ${status ?: r}")
        return r
    }

    /** WaitForDisconnect: the device acts once this connection closes, so the reply arrives first. */
    private fun power(what: String) = request(mapOf("Request" to what, "WaitForDisconnect" to true, "DisplayPass" to false, "DisplayFail" to false))

    fun restart() { power("Restart") }
    fun shutdown() { power("Shutdown") }
    fun sleep() { request(mapOf("Request" to "Sleep")) }

    @Suppress("UNCHECKED_CAST")
    private fun diagnostics(type: String): Map<String, Any?>? = runCatching {
        request(mapOf("Request" to type))["Diagnostics"] as? Map<String, Any?>
    }.getOrNull()

    /** Battery gauge (cycle count, design and full-charge capacity…). Older iOS answers this; newer may not. */
    @Suppress("UNCHECKED_CAST")
    fun gasGauge(): Map<String, Any?>? = (diagnostics("GasGauge")?.get("GasGauge") as? Map<String, Any?>)
        ?: (diagnostics("All")?.get("GasGauge") as? Map<String, Any?>)

    /** An IORegistry entry's properties, e.g. "IOPMPowerSource" for the battery; null if not supported. */
    @Suppress("UNCHECKED_CAST")
    fun ioRegistry(entryClass: String): Map<String, Any?>? = runCatching {
        (request(mapOf("Request" to "IORegistry", "EntryClass" to entryClass))["Diagnostics"] as? Map<String, Any?>)
            ?.get("IORegistry") as? Map<String, Any?>
    }.getOrNull()

    fun goodbye() { runCatching { request(mapOf("Request" to "Goodbye")) } }
}
