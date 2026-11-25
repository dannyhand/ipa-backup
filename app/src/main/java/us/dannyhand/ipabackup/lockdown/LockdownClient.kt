package us.dannyhand.ipabackup.lockdown

import us.dannyhand.ipabackup.mux.MuxStream
import us.dannyhand.ipabackup.pairing.LegacyTls
import us.dannyhand.ipabackup.pairing.PairRecord
import us.dannyhand.ipabackup.service.ByteConnection
import us.dannyhand.ipabackup.service.PlistService

class LockdownException(val code: String) : java.io.IOException("lockdownd error: $code")

/**
 * lockdownd on port 62078 (see libimobiledevice src/lockdown.c).
 * Unpaired: QueryType, some GetValue keys. Paired: StartSession (+TLS), StartService.
 */
class LockdownClient(
    private val stream: MuxStream,
    private val label: String = "IpaBackup",
    private val log: (String) -> Unit = {},
) {
    companion object { const val PORT = 62078 }

    /** Plain mux stream until StartSession enables TLS, then the TLS connection. */
    private var channel: ByteConnection = stream
    private var tls: ByteConnection? = null
    var sessionId: String? = null
        private set
    @Volatile private var tlsLogging = true

    fun request(req: Map<String, Any?>): Map<String, Any?> {
        PlistService.send(channel, linkedMapOf<String, Any?>("Label" to label) + req)
        return PlistService.receive(channel)
    }

    private fun checked(req: Map<String, Any?>): Map<String, Any?> =
        request(req).also { r -> r["Error"]?.let { throw LockdownException(it.toString()) } }

    /** Should return "com.apple.mobile.lockdown". */
    fun queryType(): String? = request(mapOf("Request" to "QueryType"))["Type"] as? String

    fun getValue(key: String? = null, domain: String? = null): Any? = checked(buildMap {
        put("Request", "GetValue")
        key?.let { put("Key", it) }
        domain?.let { put("Domain", it) }
    })["Value"]

    /** Returns the device's reply (may contain an EscrowBag on success). */
    fun pair(record: PairRecord): Map<String, Any?> = checked(mapOf(
        "Request" to "Pair",
        "PairRecord" to record.toPairRequest(),
        "ProtocolVersion" to "2",
    ))

    /** Pre-iOS 7 hosts validate the pairing before starting a session. */
    fun validatePair(record: PairRecord) {
        checked(mapOf(
            "Request" to "ValidatePair",
            "PairRecord" to record.toPairRequest(),
            "ProtocolVersion" to "2",
        ))
    }

    fun startSession(record: PairRecord): String {
        val r = checked(mapOf(
            "Request" to "StartSession",
            "HostID" to record.hostId,
            "SystemBUID" to record.systemBuid,
        ))
        val id = r["SessionID"] as? String ?: throw java.io.IOException("No SessionID in reply")
        sessionId = id
        if (r["EnableSessionSSL"] == true) {
            log("Starting session TLS…")
            val conn = LegacyTls.connect(stream.asInputStream(), stream.asOutputStream(), record) { if (tlsLogging) log(it) }
            tls = conn
            channel = conn
        }
        return id
    }

    data class ServiceInfo(val port: Int, val ssl: Boolean)

    fun startService(name: String): ServiceInfo {
        val r = checked(mapOf("Request" to "StartService", "Service" to name))
        val port = (r["Port"] as? Number)?.toInt() ?: throw java.io.IOException("No Port for $name")
        return ServiceInfo(port, r["EnableServiceSSL"] == true)
    }

    /** Ends the session. Safe to call when the iPod already closed the connection. */
    fun stopSession() {
        tlsLogging = false // close_notify / write-after-close alerts during teardown are expected noise
        val id = sessionId ?: return
        if (!stream.isClosed) runCatching { request(mapOf("Request" to "StopSession", "SessionID" to id)) }
        sessionId = null
        tls?.close()
        tls = null
        channel = stream
    }
}
