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

// Restoring apps (.ipa install) and app data.
// These are MainActivity extension functions: they run on the activity and use its state and helpers.

// ---------------------------------------------------------------- restore (install .ipa + app data)

internal fun MainActivity.bundleIdFromName(name: String) = Regex("\\(([^()]+)\\)(?: data\\.zip|\\.ipa)$").find(name)?.groupValues?.get(1)

internal fun MainActivity.showRestorePicker(device: UsbDevice) {
    val saver = IpaSaver(this)
    val ipas = saver.listSaved(".ipa").sortedBy { it.name.lowercase() }
    // Newest data backup per app.
    val dataZips = saver.listSaved(" data.zip").filter { bundleIdFromName(it.name) != null }
        .groupBy { bundleIdFromName(it.name)!! }.values.map { g -> g.maxByOrNull { it.modifiedMs }!! }
        .sortedBy { it.name.lowercase() }
    val dataIds = dataZips.mapNotNull { bundleIdFromName(it.name) }.toSet()
    val density = resources.displayMetrics.density
    val pad = (16 * density).toInt()
    val shortDay = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)

    val ipaLabels = ipas.map { s ->
        s.name.removeSuffix(".ipa") + "  (${humanSize(s.size)})" + if (bundleIdFromName(s.name) in dataIds) "  + data" else ""
    }
    val dataLabels = dataZips.map { s ->
        s.name.removeSuffix(" data.zip") + "  (${humanSize(s.size)}, ${shortDay.format(java.util.Date(s.modifiedMs))})"
    }

    var dataOnly = prefs.getBoolean("restore_data_only", false)
    val modes = android.widget.RadioGroup(this).apply { orientation = android.widget.RadioGroup.VERTICAL }
    val installMode = com.google.android.material.radiobutton.MaterialRadioButton(this).apply { text = "Install apps (.ipa)"; id = View.generateViewId() }
    val dataMode = com.google.android.material.radiobutton.MaterialRadioButton(this).apply { text = "Restore app data only (apps already on the device)"; id = View.generateViewId() }
    modes.addView(installMode)
    modes.addView(dataMode)
    val hint = TextView(this).apply { textSize = 13f; alpha = 0.8f; setPadding(0, pad / 3, 0, pad / 3) }
    val dataBox = MaterialCheckBox(this).apply {
        text = "Also restore app data (saves, settings) from a matching data .zip backup. " +
            "This replaces the saves currently on the device."
        isChecked = prefs.getBoolean("restore_data", false)
    }
    val list = ListView(this).apply {
        choiceMode = ListView.CHOICE_MODE_MULTIPLE
        isFastScrollEnabled = true
    }
    val view = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(pad, pad / 2, pad, 0)
        addView(modes)
        addView(hint)
        addView(dataBox)
        addView(list, LinearLayout.LayoutParams(-1, (resources.displayMetrics.heightPixels * 0.42).toInt()))
    }
    val dialog = MaterialAlertDialogBuilder(this)
        .setTitle("Restore to the device")
        .setView(view)
        .setPositiveButton("Restore selected", null)
        .setNeutralButton("Choose file…", null)
        .setNegativeButton("Cancel", null)
        .create()

    fun applyMode() {
        list.clearChoices()
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, if (dataOnly) dataLabels else ipaLabels)
        dataBox.visibility = if (dataOnly) View.GONE else View.VISIBLE
        hint.text = when {
            dataOnly && dataZips.isEmpty() -> "No app data backups yet. Back up apps with \"Also back up app data\" checked first."
            dataOnly -> "Writes the saved Documents/ and Library/ back into apps that are already installed, without " +
                "reinstalling them. Close the app on the device first (double-press Home and remove it), or it may " +
                "overwrite the restored saves when it quits."
            ipas.isEmpty() -> "No .ipa backups in Download/${IpaSaver.FOLDER}/ yet. Use \"Choose file…\" to pick one."
            else -> "Apps backed up to Download/${IpaSaver.FOLDER}/:"
        }
        dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL)?.visibility = if (dataOnly) View.GONE else View.VISIBLE
    }
    modes.check(if (dataOnly) dataMode.id else installMode.id)
    modes.setOnCheckedChangeListener { _, checkedId ->
        dataOnly = checkedId == dataMode.id
        applyMode()
    }
    fun remember() = prefs.edit().putBoolean("restore_data", dataBox.isChecked).putBoolean("restore_data_only", dataOnly).apply()

    dialog.setOnShowListener {
        applyMode()
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            remember()
            val checked = list.checkedItemPositions
            if (dataOnly) {
                val chosen = dataZips.filterIndexed { i, _ -> checked.get(i) }
                if (chosen.isEmpty()) { Toast.makeText(this, "Nothing selected", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                dialog.dismiss()
                confirmRestoreDataOnly(device, chosen)
            } else {
                val chosen = ipas.filterIndexed { i, _ -> checked.get(i) }.map { RestoreItem(it.name, it.uri, it.size) }
                if (chosen.isEmpty()) { Toast.makeText(this, "Nothing selected", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                dialog.dismiss()
                confirmRestore(device, chosen, dataBox.isChecked)
            }
        }
        dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
            remember()
            dialog.dismiss()
            pendingRestoreDevice = device
            pickIpas.launch(arrayOf("*/*"))
        }
    }
    dialog.show()
}

internal fun MainActivity.confirmRestoreDataOnly(device: UsbDevice, zips: List<IpaSaver.Saved>) {
    MaterialAlertDialogBuilder(this)
        .setTitle("Restore app data?")
        .setMessage("Write ${zips.size} data backup${if (zips.size == 1) "" else "s"} into the installed apps?\n\n" +
            "Saved files replace the matching files on the device; anything the backup doesn't have is left alone. " +
            "Apps that aren't installed are skipped.\n\nClose these apps on the device first.")
        .setPositiveButton("Restore") { _, _ -> runRestoreDataOnly(device, zips) }
        .setNegativeButton("Cancel", null)
        .show()
}

/** Writes a data backup into an installed app's sandbox (house_arrest). Returns files written. */
internal fun MainActivity.restoreDataZip(mux: UsbMux, lockdown: DeviceSession, bundleId: String, zip: IpaSaver.Saved): Int =
    withService(mux, lockdown, HouseArrest.SERVICE) { s ->
        HouseArrest.vendContainer(s, bundleId)
        val box = AfcClient(s).apply { cancelCheck = { checkStopNow() } }
        contentResolver.openInputStream(zip.uri)?.use { box.unzipInto(it, "/", setOf("Documents", "Library")) }
            ?: throw IOException("Can't open ${zip.name}")
    }

internal fun MainActivity.runRestoreDataOnly(device: UsbDevice, zips: List<IpaSaver.Saved>) {
    if (busy) { log("Busy, please wait for the current operation to finish."); return }
    stopMode = StopMode.NONE
    keepScreenOn(true)
    setStopButtonVisible(true)
    log("\nRestoring app data for ${zips.size} app(s)…")
    runOnDevice(device, after = { setStopButtonVisible(false); stopMode = StopMode.NONE }) { mux, lockdown ->
        val installed = withService(mux, lockdown, InstallationProxy.SERVICE) { InstallationProxy(it).browseUserApps() }
            .associateBy { it.bundleId }
        var ok = 0
        var done = 0
        val skipped = ArrayList<String>()
        val failed = ArrayList<String>()
        for ((i, zip) in zips.withIndex()) {
            if (stopMode != StopMode.NONE) break
            val id = bundleIdFromName(zip.name)!!
            val label = zip.name.removeSuffix(" data.zip")
            log("\n[${i + 1}/${zips.size}] $label")
            val app = installed[id]
            if (app == null) {
                log("  not installed on the device; use Install mode with \"Also restore app data\" instead")
                skipped += label
                done = i + 1
                continue
            }
            if (IpaSaver.dataName(app.name, app.version, app.bundleId) != zip.name) {
                log("  note: backup is from a different version than the installed ${app.version} (usually fine)")
            }
            try {
                val n = restoreDataZip(mux, lockdown, id, zip)
                log("  restored $n files ✓")
                ok++
            } catch (e: StopNowException) {
                log("  stopped partway; some files were already written")
                break
            } catch (e: Exception) {
                log("  FAILED: ${e.message}")
                failed += label
            }
            done = i + 1
        }
        val stopped = stopMode != StopMode.NONE
        log((if (stopped) "\nStopped" else "\nDone") + ": $ok restored" +
            (if (skipped.isNotEmpty()) ", ${skipped.size} not installed" else "") +
            (if (stopped && done < zips.size) ", ${zips.size - done} not restored" else "") +
            (if (failed.isEmpty()) "." else "\nFailed: ${failed.joinToString()}"))
    }
}

internal fun MainActivity.handlePickedIpas(uris: List<android.net.Uri>) {
    val device = pendingRestoreDevice ?: return
    pendingRestoreDevice = null
    if (uris.isEmpty()) return
    val items = uris.map { uri ->
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "app.ipa"
        var size = -1L
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { name = it }
                if (!c.isNull(1)) size = c.getLong(1)
            }
        }
        RestoreItem(name, uri, size)
    }
    val notIpa = items.filter { !it.name.endsWith(".ipa", true) }
    if (notIpa.isNotEmpty()) log("Skipping files that aren't .ipa: ${notIpa.joinToString { it.name }}")
    val ipas = items - notIpa.toSet()
    if (ipas.isNotEmpty()) confirmRestore(device, ipas, prefs.getBoolean("restore_data", false))
}

internal fun MainActivity.confirmRestore(device: UsbDevice, items: List<RestoreItem>, restoreData: Boolean) {
    val total = items.sumOf { maxOf(it.size, 0) }
    val msg = buildString {
        append("Install ${items.size} app${if (items.size == 1) "" else "s"} (${humanSize(total)}) onto the device?\n\n")
        append("An app that's already installed is replaced by this version, keeping its data")
        append(if (restoreData) ", except that saves from a matching data backup are written over it." else ".")
        append("\n\nApp Store apps only install on a device signed into the Apple ID that bought them, ")
        append("or on a jailbroken device with AppSync.")
    }
    MaterialAlertDialogBuilder(this)
        .setTitle("Install apps?")
        .setMessage(msg)
        .setPositiveButton("Install") { _, _ -> runRestore(device, items, restoreData) }
        .setNegativeButton("Cancel", null)
        .show()
}

/** Turns installation_proxy error codes into something actionable. */
internal fun MainActivity.explainInstallError(message: String?): String {
    val m = message ?: "unknown error"
    return m + when {
        "ApplicationVerificationFailed" in m -> " (the device won't accept this app's signature/DRM: it must be signed into " +
            "the Apple ID that bought it, or be jailbroken with AppSync)"
        "DeviceOSVersionTooLow" in m -> " (the app needs a newer iOS)"
        "PackageExtractionFailed" in m || "PackageInspectionFailed" in m -> " (the .ipa looks damaged or isn't an app)"
        "InstallProhibited" in m -> " (installs are restricted on the device: check Settings → General → Restrictions)"
        else -> ""
    }
}

/**
 * Uploads each .ipa to /PublicStaging over AFC, installs it with installation_proxy, removes the staged
 * copy, then (optionally) writes the matching data backup into the app's sandbox via house_arrest.
 */
internal fun MainActivity.runRestore(device: UsbDevice, items: List<RestoreItem>, restoreData: Boolean) {
    if (busy) { log("Busy, please wait for the current operation to finish."); return }
    val dataZips = if (restoreData) IpaSaver(this).listSaved(" data.zip") else emptyList()
    stopMode = StopMode.NONE
    keepScreenOn(true)
    setStopButtonVisible(true)
    log("\nInstalling ${items.size} app(s)…")
    runOnDevice(device, after = { setStopButtonVisible(false); stopMode = StopMode.NONE }) { mux, lockdown ->
        withService(mux, lockdown, AfcClient.SERVICE) { afcStream ->
            withService(mux, lockdown, InstallationProxy.SERVICE) { ipStream ->
                val afc = AfcClient(afcStream).apply { cancelCheck = { checkStopNow() } }
                val ip = InstallationProxy(ipStream)
                afc.makeDir("/PublicStaging")
                var ok = 0
                var datas = 0
                var done = 0
                val failed = ArrayList<String>()
                for ((i, item) in items.withIndex()) {
                    if (stopMode != StopMode.NONE) break
                    try {
                        val info = contentResolver.openInputStream(item.uri)?.use { IpaInfo.read(it) { b -> Plist.parse(b) } }
                            ?: throw IOException("Can't open ${item.name}")
                        val label = listOfNotNull(info.name ?: item.name.removeSuffix(".ipa"), info.version).joinToString(" ")
                        log("\n[${i + 1}/${items.size}] $label" + (info.bundleId?.let { " ($it)" } ?: ""))
                        if (IpaInfo.needsNewerOs(info.minimumOs, lockdown.productVersion)) {
                            log("  needs iOS ${info.minimumOs}, but the device has iOS ${lockdown.productVersion}. Skipped.")
                            failed += label
                            done = i + 1
                            continue
                        }
                        val staged = "PublicStaging/" + IpaSaver.clean(info.bundleId ?: item.name.removeSuffix(".ipa")) + ".ipa"
                        try {
                            var lastDecile = -1L
                            contentResolver.openInputStream(item.uri)?.use { input ->
                                afc.writeFile("/$staged", input.buffered(1 shl 16), item.size) { w, t ->
                                    val decile = if (t > 0) w * 10 / t else -1L
                                    if (decile > lastDecile && decile % 2 == 0L) { lastDecile = decile; log("  uploading ${mb(w)} / ${mb(t)}") }
                                }
                            } ?: throw IOException("Can't open ${item.name}")
                            var lastPct = -1
                            // Not stoppable mid-install: interrupting installd could leave the app half-installed.
                            ip.install(staged, info.iTunesMetadata, info.sinf) { status, pct ->
                                if (pct != null && pct / 25 > lastPct / 25) { lastPct = pct; log("  installing… $pct% ($status)") }
                            }
                            log("  installed ✓")
                            ok++
                        } finally {
                            runCatching { afc.remove("/$staged") }
                        }
                        if (restoreData) {
                            val id = info.bundleId
                            val dz = dataZips.filter { id != null && bundleIdFromName(it.name) == id }.maxByOrNull { it.modifiedMs }
                            if (dz == null) {
                                log("  no data backup for this app")
                            } else {
                                val n = restoreDataZip(mux, lockdown, id!!, dz)
                                log("  restored $n data files from ${dz.name}")
                                datas++
                            }
                        }
                    } catch (e: StopNowException) {
                        log("  stopped")
                        break
                    } catch (e: Exception) {
                        log("  FAILED: ${explainInstallError(e.message)}")
                        failed += item.name
                    }
                    done = i + 1
                }
                val stopped = stopMode != StopMode.NONE
                log((if (stopped) "\nStopped" else "\nDone") + ": $ok installed" +
                    (if (restoreData) ", $datas data restores" else "") +
                    (if (stopped && done < items.size) ", ${items.size - done} not installed" else "") +
                    (if (failed.isEmpty()) "." else "\nFailed: ${failed.joinToString()}"))
            }
        }
    }
}
