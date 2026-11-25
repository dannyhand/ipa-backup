package us.dannyhand.ipabackup.instproxy

import us.dannyhand.ipabackup.service.ByteConnection
import us.dannyhand.ipabackup.service.PlistService
import java.io.IOException

/**
 * com.apple.mobile.installation_proxy (see libimobiledevice src/installation_proxy.c).
 *
 * On iOS 6 it answers in binary plists even when asked in XML (that's why plist/BinaryPlist exists).
 * Browse lists installed apps; Archive packs one into ApplicationArchives/<bundleId>.zip, which already has
 * the .ipa layout (Payload/<App>.app, iTunesMetadata.plist, iTunesArtwork). Archive works on iOS 8 and older
 * only: Apple removed app extraction in iOS 9 because apps became thinned per device.
 * Archives come out at roughly 90-95% of the installed size reported by Browse (StaticDiskUsage).
 */
class InstallationProxy(private val conn: ByteConnection) {

    companion object { const val SERVICE = "com.apple.mobile.installation_proxy" }

    data class App(val bundleId: String, val name: String, val version: String, val raw: Map<String, Any?>) {
        /** e.g. /private/var/mobile/Applications/<UUID>/Foo.app */
        val path: String? get() = raw["Path"] as? String
        val container: String? get() = raw["Container"] as? String ?: path?.substringBeforeLast('/')
        val sizeBytes: Long get() = (raw["StaticDiskUsage"] as? Number)?.toLong() ?: 0L
    }

    fun browseUserApps(): List<App> {
        PlistService.send(conn, mapOf(
            "Command" to "Browse",
            "ClientOptions" to mapOf(
                "ApplicationType" to "User",
                "ReturnAttributes" to listOf(
                    "CFBundleIdentifier", "CFBundleDisplayName", "CFBundleName",
                    "CFBundleShortVersionString", "CFBundleVersion", "StaticDiskUsage",
                    "Path", "Container",
                ),
            ),
        ))
        val apps = mutableListOf<App>()
        while (true) {
            val r = PlistService.receive(conn, 30_000)
            r["Error"]?.let { throw IOException("installation_proxy: $it ${r["ErrorDescription"] ?: ""}".trim()) }
            (r["CurrentList"] as? List<*>)?.forEach { item ->
                @Suppress("UNCHECKED_CAST")
                val m = item as? Map<String, Any?> ?: return@forEach
                val id = m["CFBundleIdentifier"] as? String ?: return@forEach
                apps += App(
                    bundleId = id,
                    name = (m["CFBundleDisplayName"] ?: m["CFBundleName"] ?: id).toString(),
                    version = (m["CFBundleShortVersionString"] ?: m["CFBundleVersion"] ?: "?").toString(),
                    raw = m,
                )
            }
            if (r["Status"] == "Complete") break
        }
        return apps.sortedBy { it.name.lowercase() }
    }

    /**
     * Packs an installed app into ApplicationArchives/<bundleId>.zip on the device (iOS ≤ 8).
     *
     * ArchiveType "ApplicationOnly" leaves out the app's Documents (app data is backed up separately via
     * house_arrest). The device needs free space for the archive while it exists; it's removed right after copying.
     *
     * IMPORTANT: without SkipUninstall the device UNINSTALLS the app after archiving.
     * ideviceinstaller sends SkipUninstall=true unless asked to uninstall; so do we, always.
     */
    fun archive(bundleId: String, progress: (status: String, percent: Int?) -> Unit = { _, _ -> }) {
        runCommand(
            mapOf(
                "Command" to "Archive",
                "ApplicationIdentifier" to bundleId,
                "ClientOptions" to mapOf(
                    "ArchiveType" to "ApplicationOnly",
                    "SkipUninstall" to true,
                ),
            ),
            progress,
        )
    }

    /**
     * Installs an .ipa already uploaded over AFC to [packagePath] (relative to the media folder, e.g.
     * "PublicStaging/game.ipa"), as ideviceinstaller does. For App Store apps iTunes also passes the purchase
     * info and FairPlay license; the device still only accepts the app if it's signed into the Apple ID that
     * bought it, or is jailbroken with AppSync (otherwise: ApplicationVerificationFailed).
     */
    fun install(packagePath: String, iTunesMetadata: ByteArray?, sinf: ByteArray?, progress: (String, Int?) -> Unit = { _, _ -> }) {
        val options = LinkedHashMap<String, Any?>()
        iTunesMetadata?.let { options["iTunesMetadata"] = it }
        sinf?.let { options["ApplicationSINF"] = it }
        runCommand(mapOf("Command" to "Install", "PackagePath" to packagePath, "ClientOptions" to options), progress)
    }

    /** Deletes the device-side archive for [bundleId]. */
    fun removeArchive(bundleId: String) {
        runCommand(mapOf("Command" to "RemoveArchive", "ApplicationIdentifier" to bundleId)) { _, _ -> }
    }

    /** Sends a command and reads status updates until Complete; throws on Error. */
    private fun runCommand(cmd: Map<String, Any?>, progress: (String, Int?) -> Unit) {
        PlistService.send(conn, cmd)
        while (true) {
            // Archiving a large app can go quiet for a while between updates.
            val r = PlistService.receive(conn, 10 * 60_000)
            r["Error"]?.let {
                throw IOException("${cmd["Command"]} failed: $it ${r["ErrorDescription"] ?: ""}".trim())
            }
            val status = r["Status"] as? String ?: continue
            if (status == "Complete") return
            progress(status, (r["PercentComplete"] as? Number)?.toInt())
        }
    }
}
