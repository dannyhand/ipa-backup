package us.dannyhand.ipabackup
import android.app.Activity
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.checkbox.MaterialCheckBox
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import us.dannyhand.ipabackup.afc.AfcClient
import us.dannyhand.ipabackup.afc.AfcException
import us.dannyhand.ipabackup.certs.McInstall
import us.dannyhand.ipabackup.device.DeviceReport
import us.dannyhand.ipabackup.device.HomeScreen
import us.dannyhand.ipabackup.diag.DiagnosticsRelay
import us.dannyhand.ipabackup.jailbreak.Dpkg
import us.dannyhand.ipabackup.util.CpBitmap
import us.dannyhand.ipabackup.util.CgbiPng
import us.dannyhand.ipabackup.certs.FileServer
import us.dannyhand.ipabackup.media.BookImport
import us.dannyhand.ipabackup.springboard.NotificationProxy
import us.dannyhand.ipabackup.afc.unzipInto
import us.dannyhand.ipabackup.afc.AfcSafeRestore
import us.dannyhand.ipabackup.certs.RootProfile
import us.dannyhand.ipabackup.afc.AfcZipper
import us.dannyhand.ipabackup.afc.CancelledException
import us.dannyhand.ipabackup.afc.TreeSize
import us.dannyhand.ipabackup.afc.measureTree
import us.dannyhand.ipabackup.housearrest.HouseArrest
import us.dannyhand.ipabackup.instproxy.InstallationProxy
import us.dannyhand.ipabackup.lockdown.LockdownClient
import us.dannyhand.ipabackup.lockdown.LockdownException
import us.dannyhand.ipabackup.mux.MuxStream
import us.dannyhand.ipabackup.mux.UsbMux
import us.dannyhand.ipabackup.pairing.PairRecord
import us.dannyhand.ipabackup.pairing.PairRecordStore
import us.dannyhand.ipabackup.springboard.SpringBoardServices
import us.dannyhand.ipabackup.music.AndroidSqlDb
import us.dannyhand.ipabackup.music.ITunesDb
import us.dannyhand.ipabackup.music.ItlpLibrary
import us.dannyhand.ipabackup.music.MusicLibrary
import us.dannyhand.ipabackup.music.LibrarySample
import us.dannyhand.ipabackup.music.Playlist
import us.dannyhand.ipabackup.music.Playlists
import us.dannyhand.ipabackup.music.SongImport
import us.dannyhand.ipabackup.util.AudioTagger
import us.dannyhand.ipabackup.util.ExifThumb
import us.dannyhand.ipabackup.util.IpaInfo
import us.dannyhand.ipabackup.afc.unzipInto
import us.dannyhand.ipabackup.photos.CameraRoll
import us.dannyhand.ipabackup.media.BooksLibrary
import us.dannyhand.ipabackup.media.VoiceMemos
import us.dannyhand.ipabackup.personal.AddressBook
import us.dannyhand.ipabackup.personal.CalendarDb
import us.dannyhand.ipabackup.personal.ContactNames
import us.dannyhand.ipabackup.personal.Ics
import us.dannyhand.ipabackup.personal.MessagesDb
import us.dannyhand.ipabackup.personal.MessagesHtml
import us.dannyhand.ipabackup.personal.BookmarksHtml
import us.dannyhand.ipabackup.personal.NotesDb
import us.dannyhand.ipabackup.personal.NotesExport
import us.dannyhand.ipabackup.personal.SafariBookmarks
import us.dannyhand.ipabackup.personal.VCard
import us.dannyhand.ipabackup.personal.SafariHistory
import us.dannyhand.ipabackup.personal.CallHistory
import us.dannyhand.ipabackup.personal.CallHistoryHtml
import us.dannyhand.ipabackup.plist.Plist
import us.dannyhand.ipabackup.usb.AppleUsbTransport
import us.dannyhand.ipabackup.ui.Feature
import us.dannyhand.ipabackup.ui.IpaBackupTheme
import us.dannyhand.ipabackup.ui.MainScreen
import us.dannyhand.ipabackup.ui.UiState
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Podcasts
import java.io.IOException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import us.dannyhand.ipabackup.MainActivity.DeviceSession
import us.dannyhand.ipabackup.MainActivity.StopMode
import us.dannyhand.ipabackup.MainActivity.StopNowException
import us.dannyhand.ipabackup.MainActivity.BrowserItem
import us.dannyhand.ipabackup.MainActivity.BrowserCmd
import us.dannyhand.ipabackup.MainActivity.BrowserEntry
import us.dannyhand.ipabackup.MainActivity.MusicCmd
import us.dannyhand.ipabackup.MainActivity.PhotoCmd
import us.dannyhand.ipabackup.MainActivity.SquareFrame
import us.dannyhand.ipabackup.MainActivity.Pick
import us.dannyhand.ipabackup.MainActivity.PickCmd
import us.dannyhand.ipabackup.MainActivity.RestoreItem
import us.dannyhand.ipabackup.backup.BackupInfo
import us.dannyhand.ipabackup.backup.DeviceLink
import us.dannyhand.ipabackup.backup.MobileBackup2

// Full iTunes-style backup (mobilebackup2) into Download/IPA Backup/Backups/<UDID>, a folder iTunes and
// iMazing on a computer can restore from. These are MainActivity extension functions.

/** Where full backups go: a plain folder (not MediaStore), because the device renames and deletes files in it. */
internal fun backupRoot() = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "${IpaSaver.FOLDER}/Backups")

/**
 * Writing a normal folder in Downloads needs "All files access" on Android 11+ (MediaStore can't rename
 * or list files another way), or the storage permission on Android 10 and older.
 */
internal fun MainActivity.hasBackupStorage(): Boolean =
    if (android.os.Build.VERSION.SDK_INT >= 30) android.os.Environment.isExternalStorageManager()
    else checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED

internal fun MainActivity.askBackupStorage() {
    MaterialAlertDialogBuilder(this)
        .setTitle("Allow access to Downloads")
        .setMessage("A full backup is a folder of thousands of files that the device creates, renames and deletes as it " +
            "goes, saved to Download/IPA Backup/Backups so you can copy it to a computer for iTunes or iMazing.\n\n" +
            "Android only allows that with " + (if (android.os.Build.VERSION.SDK_INT >= 30) "\"All files access\". Turn it on for IPA Backup on the next screen, then tap Full backup again."
                else "the storage permission."))
        .setPositiveButton("Continue") { _, _ ->
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                runCatching {
                    startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        android.net.Uri.parse("package:$packageName")))
                }.onFailure { startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
            } else requestStorage.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        .setNegativeButton("Cancel", null)
        .show()
}

private fun folderSize(f: java.io.File): Long = f.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

internal fun MainActivity.startFullBackup(device: UsbDevice) {
    if (!hasBackupStorage()) { askBackupStorage(); return }
    log("\nPreparing a full backup…")
    var udid: String? = null
    var name = "device"
    var encrypted = false
    var used = -1L
    runOnDevice(device, after = { udid?.let { u -> runOnUiThread { confirmFullBackup(device, u, name, encrypted, used) } } }) { _, lockdown ->
        udid = lockdown.value("UniqueDeviceID", null) as? String
        name = lockdown.deviceName
        encrypted = lockdown.value("WillEncrypt", "com.apple.mobile.backup") == true
        val disk = lockdown.value(null, "com.apple.disk_usage") as? Map<*, *>
        // Backups leave out apps, music and videos (iTunes re-syncs those), so count what's left of the data partition.
        fun n(k: String) = (disk?.get(k) as? Number)?.toLong() ?: 0L
        if (disk != null) used = (n("TotalDataCapacity") - n("TotalDataAvailable") - n("MobileApplicationUsage") -
            n("MusicUsage").coerceAtLeast(0)).coerceAtLeast(0)
        if (udid == null) log("Couldn't read the device's ID.")
    }
}

private fun MainActivity.confirmFullBackup(device: UsbDevice, udid: String, name: String, encrypted: Boolean, used: Long) {
    val dir = java.io.File(backupRoot(), udid)
    val previous = java.io.File(dir, "Info.plist").takeIf { it.isFile }?.let { f ->
        runCatching { Plist.parse(f.readBytes()) as? Map<*, *> }.getOrNull()?.get("Last Backup Date") as? java.util.Date
    }
    val prevSize = if (previous != null) folderSize(dir) else 0L
    val free = backupRoot().apply { mkdirs() }.usableSpace
    val fmt = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
    val full = MaterialCheckBox(this).apply {
        text = "Start fresh (full backup instead of updating the last one)"
        isChecked = previous == null
        isEnabled = previous != null
    }
    val pad = (20 * resources.displayMetrics.density).toInt()
    val body = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(pad, pad / 2, pad, 0)
        addView(TextView(this@confirmFullBackup).apply {
            textSize = 14f
            text = buildString {
                append("Backs up $name the way iTunes does: settings, app data, Camera Roll, messages, contacts and more. ")
                append("Apps themselves, music and videos aren't included (iTunes doesn't back those up either; use Back up apps and Music).\n\n")
                append("Saved to Download/${IpaSaver.FOLDER}/Backups/$udid, a folder iTunes or iMazing on a computer can restore from.\n\n")
                if (previous != null) append("Last backup: ${fmt.format(previous)} (${humanSize(prevSize)}). Only changes are copied this time.\n")
                else append("No previous backup on this phone.\n")
                if (used > 0) append("About ${humanSize(used)} of data on the device (the backup is usually smaller).\n")
                append("Free space on this phone: ${humanSize(free)}\n")
                if (encrypted) append("\n🔒 Backup encryption is turned on for this device, so the backup will be encrypted with the password set " +
                    "in iTunes. You'll need that password to restore it.\n")
                append("\nKeep the device plugged in and unlocked; it can take a while.")
            }
        })
        addView(full)
    }
    MaterialAlertDialogBuilder(this)
        .setTitle("Full backup")
        .setView(android.widget.ScrollView(this).apply { addView(body) })
        .setPositiveButton("Back up") { _, _ -> runFullBackup(device, udid, full.isChecked) }
        .setNegativeButton("Cancel", null)
        .show()
}

private fun MainActivity.post(mux: UsbMux, lockdown: MainActivity.DeviceSession, name: String) {
    runCatching { withService(mux, lockdown, NotificationProxy.SERVICE) { NotificationProxy(it).post(name) } }
}

private fun MainActivity.runFullBackup(device: UsbDevice, udid: String, full: Boolean) {
    val root = backupRoot()
    log("\nStarting the full backup…")
    stopMode = StopMode.NONE
    setStopButtonVisible(true)
    runOnDevice(device, after = { setStopButtonVisible(false); stopMode = StopMode.NONE }) { mux, lockdown ->
        val started = System.currentTimeMillis()
        // Info.plist: the device's identity, iTunes' prefs files and settings, the installed apps.
        @Suppress("UNCHECKED_CAST")
        val info = lockdown.value(null, null) as? Map<String, Any?> ?: emptyMap()
        @Suppress("UNCHECKED_CAST")
        val settings = lockdown.value(null, "com.apple.iTunes") as? Map<String, Any?>
        val apps = runCatching { withService(mux, lockdown, InstallationProxy.SERVICE) { InstallationProxy(it).browseUserApps().map { a -> a.bundleId } } }
            .getOrDefault(emptyList())

        post(mux, lockdown, "com.apple.itunes-mobdev.syncWillStart")
        withService(mux, lockdown, AfcClient.SERVICE) { s ->
            val afc = AfcClient(s)
            fun read(p: String) = runCatching { java.io.ByteArrayOutputStream().also { afc.copyFile(p, it) }.toByteArray() }.getOrNull()
            val files = LinkedHashMap<String, ByteArray>()
            for (f in BackupInfo.ITUNES_FILES) read("/iTunes_Control/iTunes/$f")?.let { files[f] = it }
            val plist = BackupInfo.build(udid, info, settings, files, apps, read("/Books/Books.plist"))
            java.io.File(root, udid).mkdirs()
            java.io.File(root, "$udid/Info.plist").writeText(Plist.toXml(plist))

            // The sync lock iTunes takes, so nothing else syncs at the same time.
            val lock = runCatching { afc.open("/com.apple.itunes.lock_sync", 2L) }
                .recoverCatching { afc.open("/com.apple.itunes.lock_sync", 4L) }.getOrNull()
            post(mux, lockdown, "com.apple.itunes-mobdev.syncLockRequest")
            var locked = false
            if (lock != null) for (attempt in 1..50) {
                if (runCatching { afc.lock(lock, AfcClient.LOCK_EX) }.isSuccess) { locked = true; break }
                Thread.sleep(200)
            }
            if (!locked) log("(couldn't take the sync lock; continuing anyway)")
            post(mux, lockdown, "com.apple.itunes-mobdev.syncDidStart")

            var mb: MobileBackup2? = null
            val ticker = kotlin.concurrent.thread(isDaemon = true, name = "backup-progress") {
                var lastFiles = -1
                try {
                    while (true) {
                        Thread.sleep(5000)
                        val st = mb?.stats ?: continue
                        if (st.filesReceived != lastFiles) {
                            lastFiles = st.filesReceived
                            log("  ${st.filesReceived} files, ${humanSize(st.bytesReceived)}" +
                                if (st.progress >= 0) " (%.0f%%)".format(st.progress) else "")
                        }
                    }
                } catch (_: InterruptedException) {}
            }
            try {
                withService(mux, lockdown, MobileBackup2.SERVICE) { bs ->
                    val link = DeviceLink(bs)
                    val client = MobileBackup2(link, root)
                    mb = client
                    client.cancelCheck = { checkStopNow() }
                    client.onEvent = { log(it) }
                    val v = client.connect()
                    log("Backup protocol $v. The device is preparing (this can take a minute)…")
                    try {
                        client.backup(udid, full)
                    } catch (e: Exception) {
                        link.disconnect()
                        throw e
                    }
                    link.disconnect()
                    val took = (System.currentTimeMillis() - started) / 1000
                    log("Backup complete ✓ ${client.stats.filesReceived} files, ${humanSize(client.stats.bytesReceived)} received " +
                        "in ${took / 60}m ${took % 60}s. Folder: Download/${IpaSaver.FOLDER}/Backups/$udid (${humanSize(folderSize(java.io.File(root, udid)))})")
                }
            } finally {
                ticker.interrupt()
                if (lock != null) {
                    if (locked) runCatching { afc.lock(lock, AfcClient.LOCK_UN) }
                    runCatching { afc.close(lock) }
                }
                post(mux, lockdown, NotificationProxy.SYNC_DID_FINISH)
            }
        }
    }
}
