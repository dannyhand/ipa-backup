package us.dannyhand.ipabackup.backup

import java.util.Date
import java.util.UUID

/**
 * Info.plist for an iTunes-style backup folder. The device writes Manifest.mbdb, Manifest.plist and
 * Status.plist itself; Info.plist is the host's job (iTunes, and libimobiledevice's
 * mobilebackup_factory_info_plist_new), and it's what iTunes and iMazing read to list a backup.
 * Besides the device's identity it carries the "iTunes Files" (small prefs files from
 * /iTunes_Control/iTunes), "iTunes Settings" (lockdown's com.apple.iTunes domain) and the installed apps.
 */
object BackupInfo {
    /** Files iTunes copies from /iTunes_Control/iTunes into Info.plist. */
    val ITUNES_FILES = listOf(
        "ApertureAlbumPrefs", "IC-Info.sidb", "IC-Info.sidv", "PhotosFolderAlbums", "PhotosFolderName",
        "PhotosFolderPrefs", "VoiceMemos.plist", "iPhotoAlbumPrefs", "iTunesApplicationIDs", "iTunesPrefs", "iTunesPrefs.plist",
    )

    fun build(
        udid: String,
        info: Map<String, Any?>,
        itunesSettings: Map<String, Any?>?,
        itunesFiles: Map<String, ByteArray>,
        installedApps: List<String>,
        booksPlist: ByteArray?,
        now: Date = Date(),
    ): Map<String, Any?> {
        val m = linkedMapOf<String, Any?>()
        fun put(key: String, v: Any?) { if (v != null) m[key] = v }
        put("Build Version", info["BuildVersion"])
        put("Device Name", info["DeviceName"])
        put("Display Name", info["DeviceName"])
        put("GUID", UUID.randomUUID().toString().replace("-", "").uppercase())
        put("ICCID", info["IntegratedCircuitCardIdentity"])
        put("IMEI", info["InternationalMobileEquipmentIdentity"])
        put("Installed Applications", installedApps)
        put("Last Backup Date", now)
        put("MEID", info["MobileEquipmentIdentifier"])
        put("Phone Number", info["PhoneNumber"])
        put("Product Name", info["ProductName"] ?: info["DeviceClass"])
        put("Product Type", info["ProductType"])
        put("Product Version", info["ProductVersion"])
        put("Serial Number", info["SerialNumber"])
        put("Target Identifier", udid.lowercase())
        put("Target Type", "Device")
        put("Unique Identifier", udid.uppercase())
        put("iBooks Data 2", booksPlist)
        put("iTunes Files", itunesFiles)
        put("iTunes Settings", itunesSettings ?: emptyMap<String, Any?>())
        put("iTunes Version", "10.0.1")
        return m
    }
}
