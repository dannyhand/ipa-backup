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
import us.dannyhand.ipabackup.MainActivity.NewBook
import us.dannyhand.ipabackup.MainActivity.Leftover

// Finding and deleting leftovers from interrupted transfers.
// These are MainActivity extension functions: they run on the activity and use its state and helpers.

// ---------------------------------------------------------------- clean up

/**
 * Finds what an interrupted run can leave behind (e.g. the OTG cable coming out mid-copy):
 *  - device: .zip files in ApplicationArchives (made by Archive, normally deleted right after copying) and
 *    PublicStaging contents (an .ipa uploaded by Restore, normally deleted after installing)
 *  - phone: unfinished (still pending) saves, and temporary working files in the app cache
 * Without a device connected, only the phone side is checked.
 */
internal fun MainActivity.startCleanup() {
    if (busy) { log("Busy, please wait for the current operation to finish."); return }
    val connected = usbManager.deviceList.values.any { AppleUsbTransport.isApple(it) && AppleUsbTransport.findMuxInterface(it) != null }
    if (!connected) {
        log("\nNo device connected: checking this phone only.")
        showCleanup(null, emptyList(), emptyList(), emptyList())
        return
    }
    withDevice { device ->
        log("\nLooking for leftovers on the device…")
        var archives = emptyList<Leftover>()
        var staged = emptyList<Leftover>()
        var orphanBooks = emptyList<Leftover>()
        var partial = emptyList<Leftover>()
        runOnDevice(device, after = { runOnUiThread { showCleanup(device, archives, staged + orphanBooks, partial) } }) { mux, lockdown ->
            withService(mux, lockdown, AfcClient.SERVICE) { s ->
                val afc = AfcClient(s)
                fun scan(dir: String, filter: (String) -> Boolean) =
                    runCatching { afc.readDir(dir) }.getOrDefault(emptyList()).filter(filter).map { n ->
                        val path = "$dir/$n"
                        Leftover(n, runCatching {
                            val e = afc.stat(path)
                            if (e.isDir) afc.measureTree(path).bytes else e.size
                        }.getOrDefault(0L))
                    }
                archives = scan("/ApplicationArchives") { it.endsWith(".zip") }
                staged = scan("/PublicStaging") { true }
                // Book copies cut off before their Books.plist entry was written (Books › Add over USB).
                val listed = runCatching {
                    BookImport.entries(Plist.parse(java.io.ByteArrayOutputStream().also { afc.copyFile(BookImport.PLIST, it) }.toByteArray()))
                        .mapNotNull { (it["Path"] as? String)?.substringAfterLast('/') }.toSet()
                }.getOrNull()
                if (listed != null) orphanBooks = scan("/Books") { it.matches(BOOK_COPY) && it !in listed }
                    .map { Leftover("../Books/" + it.name, it.size) }
                // Library files from a music library restore that was cut off before the swap.
                orphanBooks = orphanBooks + listOf(LIB_DIR, "$LIB_DIR/iTunes Library.itlp", "$ART_DIR").flatMap { dir ->
                    scan(dir) { it.endsWith(AfcSafeRestore.NEW_SUFFIX) }.map { Leftover("..$dir/" + it.name, it.size) }
                }
            }
            // An upload cut off mid-copy. It may have been written through AFC2 (whole filesystem).
            prefs.getString(PREF_PARTIAL_UPLOAD, null)?.let { saved ->
                val path = saved.substringAfter(':')
                val afc2 = if (saved.startsWith("afc2:")) tryAfc2(mux, lockdown) else null
                val found = if (afc2 != null) afc2.use { s -> runCatching { AfcClient(s).stat(path).size }.getOrNull() }
                    else withService(mux, lockdown, AfcClient.SERVICE) { s -> runCatching { AfcClient(s).stat(path).size }.getOrNull() }
                if (found != null) partial = listOf(Leftover(saved, found)) else prefs.edit().remove(PREF_PARTIAL_UPLOAD).apply()
            }
        }
    }
}

/** Temporary working files (database copies, previews, staging). Thumbnails are a useful cache, so they stay. */
internal fun MainActivity.tempFiles(): List<java.io.File> = cacheDir.listFiles()?.filter { it.name != "thumbs" } ?: emptyList()

internal fun MainActivity.dirSize(f: java.io.File): Long = if (f.isDirectory) f.listFiles()?.sumOf { dirSize(it) } ?: 0L else f.length()

internal fun MainActivity.showCleanup(device: UsbDevice?, archives: List<Leftover>, staged: List<Leftover>, partial: List<Leftover>) {
    val saver = IpaSaver(this)
    val pending = saver.listPending()
    val temp = tempFiles()
    val tempSize = temp.sumOf { dirSize(it) }
    val pad = (20 * resources.displayMetrics.density).toInt()

    class Group(val box: MaterialCheckBox, val detail: String)
    fun group(title: String, count: Int, size: Long, detail: String) =
        Group(MaterialCheckBox(this).apply { text = "$title: $count (${humanSize(size)})"; isChecked = true }, detail)

    val groups = LinkedHashMap<String, Group>()
    if (archives.isNotEmpty()) groups["archives"] = group("Leftover app archives on the device", archives.size, archives.sumOf { it.size },
        archives.joinToString("\n") { "• " + it.name })
    if (staged.isNotEmpty()) groups["staged"] = group("Unfinished installs and copies on the device", staged.size, staged.sumOf { it.size },
        staged.joinToString("\n") { "• " + if (it.name.startsWith("../")) (if (it.name.startsWith("../Books/")) "Books/" + it.name.substringAfterLast('/') + " (not in iBooks' list)" else it.name.removePrefix("../")) else it.name })
    if (partial.isNotEmpty()) groups["partial"] = group("Partly uploaded file on the device", 1, partial[0].size, "• " + partial[0].name.substringAfter(':') +
        if (partial[0].name.startsWith("afc:")) "  (in the Media folder)" else "")
    if (pending.isNotEmpty()) groups["pending"] = group("Unfinished saves on this phone", pending.size, pending.sumOf { it.size },
        pending.joinToString("\n") { "• " + it.name })
    if (tempSize > 0) groups["temp"] = group("Temporary files on this phone", temp.size, tempSize,
        "Database copies, previews and staging files. Thumbnails are kept.")

    if (groups.isEmpty()) {
        log("Nothing to clean up" + if (device == null) " on this phone." else ".")
        MaterialAlertDialogBuilder(this)
            .setTitle("All clean")
            .setMessage(if (device == null) "Nothing left over on this phone. Connect the device to check it too."
                else "Nothing left over on the device or this phone.")
            .setPositiveButton("OK", null)
            .show()
        return
    }
    val body = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(pad, pad / 2, pad, 0)
        if (device == null) addView(TextView(this@showCleanup).apply {
            text = "No device connected, so only this phone was checked."
            textSize = 13f
            alpha = 0.8f
        })
        for (g in groups.values) {
            addView(g.box)
            addView(TextView(this@showCleanup).apply {
                text = g.detail
                textSize = 12f
                alpha = 0.7f
                maxLines = 6
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(pad * 2, 0, 0, pad / 2)
            })
        }
    }
    MaterialAlertDialogBuilder(this)
        .setTitle("Clean up")
        .setView(android.widget.ScrollView(this).apply { addView(body) })
        .setPositiveButton("Delete selected") { _, _ ->
            fun on(key: String) = groups[key]?.box?.isChecked == true
            runCleanup(device, if (on("archives")) archives else emptyList(), if (on("staged")) staged else emptyList(),
                if (on("partial")) partial else emptyList(),
                if (on("pending")) pending else emptyList(), on("temp"))
        }
        .setNegativeButton("Cancel", null)
        .show()
}

internal fun MainActivity.runCleanup(device: UsbDevice?, archives: List<Leftover>, staged: List<Leftover>, partial: List<Leftover>, pending: List<IpaSaver.Saved>, temp: Boolean) {
    // Phone side first: quick and needs no connection.
    if (pending.isNotEmpty()) {
        val saver = IpaSaver(this)
        val n = pending.count { saver.delete(it.uri) }
        log("Deleted $n unfinished save${if (n == 1) "" else "s"} on this phone.")
    }
    if (temp) {
        val freed = tempFiles().sumOf { f -> dirSize(f).also { f.deleteRecursively() } }
        log("Deleted temporary files (${humanSize(freed)}).")
    }
    if (device == null || (archives.isEmpty() && staged.isEmpty() && partial.isEmpty())) return
    log("\nCleaning up the device…")
    runOnDevice(device) { mux, lockdown ->
        for (p in partial) {
            val path = p.name.substringAfter(':')
            val afc2 = if (p.name.startsWith("afc2:")) tryAfc2(mux, lockdown) else null
            val ok = if (afc2 != null) afc2.use { s -> runCatching { AfcClient(s).remove(path) }.isSuccess }
                else withService(mux, lockdown, AfcClient.SERVICE) { s -> runCatching { AfcClient(s).remove(path) }.isSuccess }
            if (ok) { prefs.edit().remove(PREF_PARTIAL_UPLOAD).apply(); log("Deleted the partly uploaded $path ✓") }
            else log("  couldn't delete $path")
        }
        withService(mux, lockdown, AfcClient.SERVICE) { s ->
            val afc = AfcClient(s)
            if (archives.isNotEmpty()) {
                // Prefer installation_proxy's RemoveArchive (it owns ApplicationArchives); AFC delete as a fallback.
                withService(mux, lockdown, InstallationProxy.SERVICE) { ipStream ->
                    val ip = InstallationProxy(ipStream)
                    var n = 0
                    for (a in archives) {
                        val ok = runCatching { ip.removeArchive(a.name.removeSuffix(".zip")) }.isSuccess ||
                            runCatching { afc.removeTree("/ApplicationArchives/${a.name}") }.isSuccess
                        if (ok) n++ else log("  couldn't delete ${a.name}")
                    }
                    log("Deleted $n leftover app archive${if (n == 1) "" else "s"} (${humanSize(archives.sumOf { it.size })}) ✓")
                }
            }
            if (staged.isNotEmpty()) {
                var n = 0
                for (st in staged) {
                    if (runCatching { afc.removeTree(if (st.name.startsWith("../")) st.name.removePrefix("..") else "/PublicStaging/${st.name}") }.isSuccess) n++ else log("  couldn't delete ${st.name}")
                }
                log("Deleted $n unfinished install${if (n == 1) "" else "s"} (${humanSize(staged.sumOf { it.size })}) ✓")
            }
        }
    }
}
