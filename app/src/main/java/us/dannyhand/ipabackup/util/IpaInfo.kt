package us.dannyhand.ipabackup.util

import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * What's inside an .ipa, read in one streaming pass: the app's Info.plist (bundle ID, name, version,
 * minimum iOS), plus the two files iTunes hands to installation_proxy when installing a purchased app:
 * iTunesMetadata.plist (purchase info) and Payload/<App>.app/SC_Info/<App>.sinf (the FairPlay license).
 */
data class IpaInfo(
    val bundleId: String?,
    val name: String?,
    val version: String?,
    val minimumOs: String?,
    val iTunesMetadata: ByteArray?,
    val sinf: ByteArray?,
) {
    companion object {
        private val INFO = Regex("^Payload/[^/]+\\.app/Info\\.plist$")
        private val SINF = Regex("^Payload/[^/]+\\.app/SC_Info/[^/]+\\.sinf$")
        private const val MAX_SMALL_FILE = 4 shl 20

        /** [parsePlist] turns plist bytes (XML or binary) into maps; injected so tests can run on the JVM. */
        fun read(input: InputStream, parsePlist: (ByteArray) -> Any?): IpaInfo {
            var info: Map<*, *>? = null
            var meta: ByteArray? = null
            var sinf: ByteArray? = null
            ZipInputStream(input).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    when {
                        e.name.matches(INFO) && info == null -> info = runCatching { parsePlist(readSmall(z)) as? Map<*, *> }.getOrNull()
                        e.name == "iTunesMetadata.plist" -> meta = readSmall(z)
                        e.name.matches(SINF) && sinf == null -> sinf = readSmall(z)
                    }
                    if (info != null && meta != null && sinf != null) break
                }
            }
            val i = info
            fun s(vararg keys: String) = keys.firstNotNullOfOrNull { (i?.get(it) as? String)?.trim()?.ifEmpty { null } }
            return IpaInfo(
                bundleId = s("CFBundleIdentifier"),
                name = s("CFBundleDisplayName", "CFBundleName"),
                version = s("CFBundleShortVersionString", "CFBundleVersion"),
                minimumOs = s("MinimumOSVersion"),
                iTunesMetadata = meta,
                sinf = sinf,
            )
        }

        private fun readSmall(z: ZipInputStream): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (true) {
                val n = z.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > MAX_SMALL_FILE) throw java.io.IOException("Unexpectedly large plist in .ipa")
            }
            return out.toByteArray()
        }

        /** True if [required] (e.g. "4.3") is newer than [device] (e.g. "6.1.6"). */
        fun needsNewerOs(required: String?, device: String?): Boolean {
            if (required == null || device == null) return false
            val r = required.split('.').map { it.toIntOrNull() ?: 0 }
            val d = device.split('.').map { it.toIntOrNull() ?: 0 }
            for (k in 0 until maxOf(r.size, d.size)) {
                val a = r.getOrElse(k) { 0 }
                val b = d.getOrElse(k) { 0 }
                if (a != b) return a > b
            }
            return false
        }
    }
}
