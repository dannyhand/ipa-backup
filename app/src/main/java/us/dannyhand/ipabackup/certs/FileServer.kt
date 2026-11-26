package us.dannyhand.ipabackup.certs

import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.SocketException
import java.net.URLEncoder
import kotlin.concurrent.thread

/**
 * Tiny plain-HTTP server for handing files to the device's Safari over Wi-Fi (both on the same
 * network). Plain HTTP on purpose: HTTPS is what's broken on old iOS. Safari on iOS 6 opens a
 * .mobileconfig in the Install Profile screen, and offers "Open in iBooks" for an EPUB or PDF.
 */
class FileServer(private val title: String, private val files: List<File>, private val onRequest: (String) -> Unit) {
    class File(val name: String, val mime: String, val bytes: ByteArray, val linkText: String = name)

    private var server: ServerSocket? = null
    val port get() = server?.localPort ?: 0

    private fun path(i: Int) = "/f/$i/" + URLEncoder.encode(files[i].name, "UTF-8").replace("+", "%20")

    fun start(preferredPort: Int = 8080) {
        val s = runCatching { ServerSocket(preferredPort) }.getOrElse { ServerSocket(0) }
        server = s
        thread(isDaemon = true, name = "file-server") {
            while (!s.isClosed) {
                val c = try { s.accept() } catch (e: SocketException) { break }
                thread(isDaemon = true) {
                    c.use {
                        runCatching {
                            c.soTimeout = 10_000
                            val req = c.getInputStream().bufferedReader().readLine() ?: return@runCatching
                            onRequest("${c.inetAddress.hostAddress}: $req")
                            val p = req.split(' ').getOrNull(1) ?: "/"
                            val out = c.getOutputStream()
                            val idx = Regex("^/f/(\\d+)/").find(p)?.groupValues?.get(1)?.toIntOrNull()
                            if (idx != null && idx in files.indices) {
                                val f = files[idx]
                                out.write(("HTTP/1.0 200 OK\r\nContent-Type: ${f.mime}\r\n" +
                                    "Content-Length: ${f.bytes.size}\r\nConnection: close\r\n\r\n").toByteArray())
                                out.write(f.bytes)
                            } else {
                                fun esc(t: String) = t.replace("&", "&amp;").replace("<", "&lt;")
                                val links = files.indices.joinToString("") { "<p><a href=\"${path(it)}\">${esc(files[it].linkText)}</a></p>" }
                                val html = "<html><head><meta name=viewport content=\"width=device-width\"><title>${esc(title)}</title></head><body " +
                                    "style=\"font-family:Helvetica;font-size:20px;padding:20px\"><p><b>${esc(title)}</b></p>$links</body></html>"
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
