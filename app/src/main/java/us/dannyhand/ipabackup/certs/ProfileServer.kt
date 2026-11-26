package us.dannyhand.ipabackup.certs

import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.SocketException
import kotlin.concurrent.thread

/**
 * Tiny plain-HTTP server that hands out one profile, for installing it from Safari on the iPod over
 * Wi-Fi (both on the same network). Plain HTTP on purpose: HTTPS is exactly what's broken on the iPod.
 * Safari on iOS 6 recognizes the content type and opens the Install Profile screen.
 */
class ProfileServer(private val profile: ByteArray, private val onRequest: (String) -> Unit) {
    private var server: ServerSocket? = null
    val port get() = server?.localPort ?: 0

    fun start(preferredPort: Int = 8080) {
        val s = runCatching { ServerSocket(preferredPort) }.getOrElse { ServerSocket(0) }
        server = s
        thread(isDaemon = true, name = "profile-server") {
            while (!s.isClosed) {
                val c = try { s.accept() } catch (e: SocketException) { break }
                thread(isDaemon = true) {
                    c.use {
                        runCatching {
                            c.soTimeout = 10_000
                            val req = c.getInputStream().bufferedReader().readLine() ?: return@runCatching
                            onRequest("${c.inetAddress.hostAddress}: $req")
                            val path = req.split(' ').getOrNull(1) ?: "/"
                            val out = c.getOutputStream()
                            if (path.startsWith("/roots")) {
                                out.write(("HTTP/1.0 200 OK\r\nContent-Type: application/x-apple-aspen-config\r\n" +
                                    "Content-Disposition: attachment; filename=\"roots.mobileconfig\"\r\n" +
                                    "Content-Length: ${profile.size}\r\nConnection: close\r\n\r\n").toByteArray())
                                out.write(profile)
                            } else {
                                val html = "<html><head><meta name=viewport content=\"width=device-width\"></head><body " +
                                    "style=\"font-family:Helvetica;font-size:20px;padding:20px\"><p>Updated root certificates " +
                                    "for old iOS.</p><p><a href=\"/roots.mobileconfig\">Install profile</a></p></body></html>"
                                val b = html.toByteArray()
                                out.write(("HTTP/1.0 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n" +
                                    "Content-Length: ${b.size}\r\nConnection: close\r\n\r\n").toByteArray())
                                out.write(b)
                            }
                            out.flush()
                        }
                    }
                }
            }
        }
    }

    fun stop() { runCatching { server?.close() } }

    companion object {
        /** The phone's IPv4 addresses on Wi-Fi (or any LAN interface), for building the URL. */
        fun localAddresses(): List<String> = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .sortedBy { if (it.name.startsWith("wlan")) 0 else 1 }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .map { it.hostAddress!! }
        }.getOrDefault(emptyList())
    }
}
