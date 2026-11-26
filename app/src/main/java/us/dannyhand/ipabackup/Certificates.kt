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

// Installing root certificates, and sharing files to Safari over Wi-Fi.
// These are MainActivity extension functions: they run on the activity and use its state and helpers.

// ---------------------------------------------------------------- root certificates

internal fun MainActivity.loadRoots(): List<RootProfile.Root> =
    assets.list("roots").orEmpty().sorted().map { f -> RootProfile.Root(f.removeSuffix(".cer"), assets.open("roots/$f").use { it.readBytes() }) }

/**
 * Fixes "The certificate for this server is invalid" in Safari on old iOS by installing a profile of
 * current root certificates (see RootProfile). Checks the device clock first: a wrong date (an iPod
 * whose battery ran flat resets to 1970) makes every certificate look not-yet-valid, with the same error.
 */
internal fun MainActivity.startCertificates() {
    if (busy) { log("Busy, please wait for the current operation to finish."); return }
    val connected = usbManager.deviceList.values.any { AppleUsbTransport.isApple(it) && AppleUsbTransport.findMuxInterface(it) != null }
    if (!connected) { showCertificates(null, null, null); return }
    withDevice { device ->
        log("\nChecking the device clock and installed profiles…")
        var time: Long? = null
        var installed: Boolean? = null
        runOnDevice(device, after = { runOnUiThread { showCertificates(device, time, installed) } }) { mux, lockdown ->
            time = lockdown.deviceTime()
            installed = runCatching {
                withService(mux, lockdown, McInstall.SERVICE) { McInstall(it).installedIdentifiers() }
            }.getOrNull()?.contains(RootProfile.IDENTIFIER)
        }
    }
}

internal fun MainActivity.showCertificates(device: UsbDevice?, deviceTime: Long?, installed: Boolean?) {
    val roots = runCatching { loadRoots() }.getOrElse { log("Couldn't load the certificates: ${it.message}"); return }
    val profile = RootProfile.build(roots)
    val fmt = java.text.SimpleDateFormat("MMM d, yyyy h:mm a", java.util.Locale.US)
    val now = System.currentTimeMillis() / 1000
    val msg = buildString {
        if (deviceTime != null) {
            val skew = deviceTime - now
            log("Device clock: ${fmt.format(java.util.Date(deviceTime * 1000))}")
            if (kotlin.math.abs(skew) > 2 * 86400) {
                append("⚠ The device's date is ${fmt.format(java.util.Date(deviceTime * 1000))}. With a wrong date, every ")
                append("certificate looks invalid, whatever is installed. Fix it first in Settings › General › ")
                append("Date & Time (turn on Set Automatically, or set it by hand).\n\n")
            }
        }
        if (installed == true) append("These certificates are already installed on the device. Installing again replaces them.\n\n")
        append("Installs a profile with ${roots.size} current root certificates, so Safari and apps can open sites ")
        append("that use certificate authorities newer than iOS 6 (Let's Encrypt and others):\n\n")
        append(roots.joinToString("\n") { "• " + it.commonName })
        append("\n\nYou'll tap Install on the device to accept it. It can be removed later in Settings › General › Profiles.")
        if (device == null) append("\n\nNo device connected over USB, so it can only be shared over Wi-Fi.")
    }
    val b = MaterialAlertDialogBuilder(this)
        .setTitle("Root certificates")
        .setMessage(msg)
        .setNeutralButton("Share over Wi-Fi") { _, _ -> shareProfileOverWifi(profile) }
        .setNegativeButton("Cancel", null)
    if (device != null) b.setPositiveButton("Install over USB") { _, _ -> installProfileOverUsb(device, profile) }
    b.show()
}

internal fun MainActivity.installProfileOverUsb(device: UsbDevice, profile: ByteArray) {
    log("\nSending the certificate profile to the device…")
    var failed: String? = null
    runOnDevice(device, after = {
        failed?.let { err ->
            runOnUiThread {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Couldn't install over USB")
                    .setMessage("$err\n\nYou can install it from Safari on the device over Wi-Fi instead.")
                    .setPositiveButton("Share over Wi-Fi") { _, _ -> shareProfileOverWifi(profile) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }) { mux, lockdown ->
        try {
            withService(mux, lockdown, McInstall.SERVICE) { McInstall(it).installProfile(profile) }
            log("Sent ✓ On the device, tap Install (and Install again to confirm). If no screen appears, " +
                "unlock it and look in Settings › General › Profiles.")
        } catch (e: Exception) {
            failed = e.message ?: e.toString()
            log("Install over USB failed: $failed")
        }
    }
}

internal fun MainActivity.shareProfileOverWifi(profile: ByteArray) = shareOverWifi(
    "Updated root certificates for old iOS",
    listOf(FileServer.File("roots.mobileconfig", "application/x-apple-aspen-config", profile, "Install profile")),
    "Tap Install profile, then Install.",
)

/** Serves [files] over plain HTTP until the dialog is closed; the device opens the shown address in Safari. */
internal fun MainActivity.shareOverWifi(title: String, files: List<FileServer.File>, howTo: String) {
    fileServer?.stop()
    val server = FileServer(title, files) { line -> log("  request from $line") }
    try { server.start() } catch (e: Exception) { log("Couldn't start the Wi-Fi server: ${e.message}"); return }
    fileServer = server
    val addrs = FileServer.localAddresses()
    if (addrs.isEmpty()) {
        server.stop()
        log("No Wi-Fi network found on this phone.")
        MaterialAlertDialogBuilder(this).setTitle("No Wi-Fi").setMessage(
            "Connect this phone and the device to the same Wi-Fi network (or turn on this phone's hotspot and " +
            "join it from the device), then try again.").setPositiveButton("OK", null).show()
        return
    }
    val urls = addrs.map { "http://$it:${server.port}/" }
    log("\nSharing over Wi-Fi at ${urls.joinToString(" or ")}")
    val text = TextView(this).apply {
        val pad = (20 * resources.displayMetrics.density).toInt()
        setPadding(pad, pad / 2, pad, 0)
        setTextIsSelectable(true)
        textSize = 15f
        text = "On the device, open Safari and go to:\n\n" + urls.joinToString("\n") +
            "\n\n$howTo\n\nBoth need to be on the same Wi-Fi (this phone's hotspot works too). Keep this open until you're done."
    }
    MaterialAlertDialogBuilder(this)
        .setTitle("Share over Wi-Fi")
        .setView(text)
        .setCancelable(false)
        .setPositiveButton("Done") { _, _ -> server.stop(); fileServer = null; log("Stopped sharing.") }
        .show()
}
