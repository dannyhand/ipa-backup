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

// Backing up apps (.ipa and data), uninstalling, and auto-backup on plug-in.
// These are MainActivity extension functions: they run on the activity and use its state and helpers.

// ---------------------------------------------------------------- app list + backup

/** Connects, pairs if needed, lists user apps, then offers to back them up. */
internal fun MainActivity.probe(device: UsbDevice) {
    log("\nConnecting…")
    var apps: List<InstallationProxy.App> = emptyList()
    runOnDevice(device, after = { runOnUiThread { showPicker(device, apps) } }) { mux, lockdown ->
        apps = withService(mux, lockdown, InstallationProxy.SERVICE) { InstallationProxy(it).browseUserApps() }
        log("${apps.size} user apps installed.")
        if (prefs.getBoolean("show_icons", false)) fetchIcons(mux, lockdown, apps)
    }
}

/** Downloads home-screen icons for apps that aren't in the icon cache yet. */
internal fun MainActivity.fetchIcons(mux: UsbMux, lockdown: DeviceSession, apps: List<InstallationProxy.App>) {
    val cache = IconCache(this)
    val missing = apps.filter { !cache.has(it) }
    if (missing.isEmpty()) return
    log("Loading ${missing.size} app icons…")
    try {
        withService(mux, lockdown, SpringBoardServices.SERVICE) { stream ->
            val sbs = SpringBoardServices(stream)
            var ok = 0
            for (app in missing) {
                val png = runCatching { sbs.iconPng(app.bundleId) }.getOrNull() ?: continue
                if (runCatching { cache.put(app, png) }.getOrDefault(false)) ok++
            }
            if (ok < missing.size) log("Loaded $ok of ${missing.size} icons.")
        }
    } catch (e: Exception) {
        log("Couldn't load icons: ${e.message}")
    }
}

internal fun MainActivity.showPicker(device: UsbDevice, apps: List<InstallationProxy.App>) {
    if (isFinishing || apps.isEmpty()) return
    val saved = IpaSaver(this).existingNames()
    val isSaved = apps.map { IpaSaver.ipaName(it.name, it.version, it.bundleId) in saved }
    val labels = apps.mapIndexed { i, a -> (if (isSaved[i]) "✓ " else "") + "${a.name} ${a.version}${sizeLabel(a.sizeBytes)}" }

    val pad = (16 * resources.displayMetrics.density).toInt()
    val skipBox = MaterialCheckBox(this).apply {
        text = "Skip apps already saved (✓ ${isSaved.count { it }} of ${apps.size})"
        isChecked = prefs.getBoolean("skip_saved", true)
    }
    val dataBox = MaterialCheckBox(this).apply {
        text = "Also back up app data (saves, settings) as a separate .zip"
        isChecked = prefs.getBoolean("include_data", false)
    }
    val autoBox = MaterialCheckBox(this).apply {
        text = "Back up new apps automatically when the device is plugged in"
        isChecked = prefs.getBoolean("auto_backup", false)
        setOnCheckedChangeListener { _, on ->
            prefs.edit().putBoolean("auto_backup", on).apply()
            if (on) Toast.makeText(this@showPicker, "When Android asks which app to open for this device, pick IPA Backup and tick Always.", Toast.LENGTH_LONG).show()
        }
    }
    val iconCache = IconCache(this)
    val iconPx = (40 * resources.displayMetrics.density).toInt()
    var showIcons = prefs.getBoolean("show_icons", false)
    val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_multiple_choice, labels) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val tv = super.getView(position, convertView, parent) as TextView
            val icon = if (showIcons) iconCache.bitmap(apps[position]) else null
            if (icon != null) {
                val d = android.graphics.drawable.BitmapDrawable(resources, icon).apply { setBounds(0, 0, iconPx, iconPx) }
                tv.setCompoundDrawablesRelative(d, null, null, null)
                tv.compoundDrawablePadding = pad / 2
            } else {
                tv.setCompoundDrawablesRelative(null, null, null, null)
            }
            return tv
        }
    }
    val list = ListView(this).apply {
        choiceMode = ListView.CHOICE_MODE_MULTIPLE
        this.adapter = adapter
        isFastScrollEnabled = true
        isFastScrollAlwaysVisible = apps.size > 20
    }
    var pickerDialog: AlertDialog? = null
    val iconBox = MaterialCheckBox(this).apply {
        text = "Show app icons"
        isChecked = showIcons
        setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("show_icons", checked).apply()
            showIcons = checked
            if (checked && apps.any { !iconCache.has(it) }) {
                // Icons come from the iPod: reconnect to fetch them, then this list reopens.
                pickerDialog?.dismiss()
                probe(device)
            } else {
                adapter.notifyDataSetChanged()
            }
        }
    }
    val view = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(pad, pad / 2, pad, 0)
        addView(skipBox)
        addView(dataBox)
        addView(iconBox)
        addView(autoBox)
        addView(list, LinearLayout.LayoutParams(-1, (resources.displayMetrics.heightPixels * 0.5).toInt()))
    }

    fun start(chosen: List<InstallationProxy.App>) {
        prefs.edit().putBoolean("skip_saved", skipBox.isChecked).putBoolean("include_data", dataBox.isChecked).apply()
        if (chosen.isEmpty()) { log("Nothing selected."); return }
        backup(device, chosen, skipBox.isChecked, dataBox.isChecked, saved)
    }

    MaterialAlertDialogBuilder(this)
        .setTitle("Back up which apps?")
        .setView(view)
        .setPositiveButton("Back up selected") { _, _ ->
            val checked = list.checkedItemPositions
            start(apps.filterIndexed { i, _ -> checked.get(i) })
        }
        .setNeutralButton("Back up all") { _, _ ->
            prefs.edit().putBoolean("skip_saved", skipBox.isChecked).putBoolean("include_data", dataBox.isChecked).apply()
            confirmBackupAll(device, apps, saved, skipBox.isChecked, dataBox.isChecked)
        }
        .setNegativeButton("Cancel", null)
        .show()
        .also { pickerDialog = it }
}

/** "Back up all" safety check: what will run, how big it'll be, and whether the phone has room. */
internal fun MainActivity.confirmBackupAll(
    device: UsbDevice,
    apps: List<InstallationProxy.App>,
    saved: Set<String>,
    skipSaved: Boolean,
    includeData: Boolean,
) {
    val toArchive = apps.filter { !(skipSaved && IpaSaver.ipaName(it.name, it.version, it.bundleId) in saved) }
    val alreadySaved = apps.size - toArchive.size
    if (toArchive.isEmpty() && !includeData) {
        MaterialAlertDialogBuilder(this)
            .setMessage("All ${apps.size} apps are already saved. Uncheck \"Skip apps already saved\" to back them up again.")
            .setPositiveButton("OK") { _, _ -> showPicker(device, apps) }
            .setOnCancelListener { showPicker(device, apps) }
            .show()
        return
    }

    val installed = toArchive.sumOf { it.sizeBytes }
    // .ipa archives come out at roughly 90-95% of the installed size (see test backups).
    val estimate = (installed * 0.95).toLong()
    val largest = toArchive.maxByOrNull { it.sizeBytes }
    val free = runCatching {
        android.os.StatFs(android.os.Environment.getExternalStorageDirectory().path).availableBytes
    }.getOrDefault(-1L)

    val msg = buildString {
        append("Back up ${toArchive.size} app${if (toArchive.size == 1) "" else "s"}")
        if (alreadySaved > 0) append(" (skipping $alreadySaved already saved)")
        append("?\n\n")
        append("Estimated size: about ${humanSize(estimate)}\n")
        append("(${humanSize(installed)} installed on the iPod)\n")
        if (includeData) append("Plus app data for ${apps.size} apps (size known only once it's copied)\n")
        if (free >= 0) {
            append("\nFree space on this phone: ${humanSize(free)}\n")
            if (estimate > free) append("⚠️ Not enough space for everything. The backup will fail partway.\n")
            else if (estimate > free * 0.8) append("⚠️ This will use most of your free space.\n")
        }
        if (largest != null && largest.sizeBytes > 0) {
            append("\nThe iPod needs free space for one archive at a time (largest: ${largest.name}, ${humanSize(largest.sizeBytes)}).\n")
        }
        append("\nKeep both devices connected. You can stop at any time.")
    }
    var handled = false
    MaterialAlertDialogBuilder(this)
        .setTitle("Back up all apps?")
        .setMessage(msg)
        .setPositiveButton("Back up") { _, _ ->
            handled = true
            backup(device, apps, skipSaved, includeData, saved)
        }
        .setNegativeButton("Back") { _, _ -> handled = true; showPicker(device, apps) }
        .setOnDismissListener { if (!handled) showPicker(device, apps) }
        .show()
}

internal fun MainActivity.backup(
    device: UsbDevice,
    apps: List<InstallationProxy.App>,
    skipSaved: Boolean,
    includeData: Boolean,
    savedNames: Set<String>,
) {
    val todo = apps.filter { app ->
        includeData || !(skipSaved && IpaSaver.ipaName(app.name, app.version, app.bundleId) in savedNames)
    }
    if (todo.isEmpty()) { log("\nEverything selected is already saved. Nothing to do."); return }
    if (busy) { log("Busy, please wait for the current operation to finish."); return }
    keepScreenOn(true)
    stopMode = StopMode.NONE
    setStopButtonVisible(true)
    val saver = IpaSaver(this)
    log("\nBacking up ${todo.size} app(s) to Download/${IpaSaver.FOLDER}/" + if (includeData) " (with app data)" else "")

    runOnDevice(device, after = { setStopButtonVisible(false); stopMode = StopMode.NONE }) { mux, lockdown ->
        val afc2 = tryAfc2(mux, lockdown)
        if (afc2 != null) log("AFC2 available (jailbroken): will copy the app bundle directly if archiving fails.")
        try {
            withService(mux, lockdown, InstallationProxy.SERVICE) { ipStream ->
                withService(mux, lockdown, AfcClient.SERVICE) { afcStream ->
                    val ip = InstallationProxy(ipStream)
                    val afc = AfcClient(afcStream).apply { cancelCheck = { checkStopNow() } }
                    val afc2Client = afc2?.let { AfcClient(it).apply { cancelCheck = { checkStopNow() } } }
                    removeLeftoverArchives(ip, afc)

                    var ipas = 0
                    var skipped = 0
                    var datas = 0
                    var done = 0
                    val failed = mutableListOf<String>()

                    for ((i, app) in todo.withIndex()) {
                        if (stopMode != StopMode.NONE) break
                        log("\n[${i + 1}/${todo.size}] ${app.name} ${app.version}")
                        val ipaName = IpaSaver.ipaName(app.name, app.version, app.bundleId)
                        if (skipSaved && ipaName in savedNames) {
                            log("  .ipa already saved, skipping")
                            skipped++
                        } else {
                            try {
                                backupIpa(ip, afc, afc2Client, saver, app)
                                ipas++
                            } catch (e: StopNowException) {
                                log("  stopped; the partial file was discarded")
                                break
                            } catch (e: Exception) {
                                log("  FAILED: ${e.message}")
                                failed += app.name
                            }
                        }
                        if (includeData) {
                            try {
                                if (backupData(mux, lockdown, saver, app)) datas++
                            } catch (e: StopNowException) {
                                log("  stopped; the partial data backup was discarded")
                                break
                            } catch (e: Exception) {
                                log("  data FAILED: ${e.message}")
                                failed += "${app.name} (data)"
                            }
                        }
                        done = i + 1
                    }

                    val remaining = todo.size - done
                    val header = if (stopMode != StopMode.NONE) "\nBackup stopped" else "\nBackup finished"
                    log("$header: $ipas .ipa saved" +
                        (if (skipped > 0) ", $skipped already saved" else "") +
                        (if (includeData) ", $datas data backups" else "") +
                        (if (stopMode != StopMode.NONE && remaining > 0) ", $remaining not backed up" else "") +
                        (if (failed.isEmpty()) "." else "\nFailed: ${failed.joinToString()}"))
                }
            }
        } finally {
            afc2?.close()
        }
    }
}

/**
 * Deletes archives left in ApplicationArchives/ by an interrupted run (e.g. Stop now while
 * the iPod was still archiving), so they don't eat the iPod's free space.
 */
internal fun MainActivity.removeLeftoverArchives(ip: InstallationProxy, afc: AfcClient) {
    val leftovers = runCatching { afc.readDir("ApplicationArchives") }.getOrDefault(emptyList())
        .filter { it.endsWith(".zip") }
    if (leftovers.isEmpty()) return
    var removed = 0
    for (f in leftovers) {
        if (runCatching { ip.removeArchive(f.removeSuffix(".zip")) }.isSuccess) removed++
    }
    log("Cleaned up $removed leftover archive(s) on the iPod.")
}

internal fun MainActivity.backupIpa(ip: InstallationProxy, afc: AfcClient, afc2: AfcClient?, saver: IpaSaver, app: InstallationProxy.App) {
    try {
        backupIpaViaArchive(ip, afc, saver, app)
    } catch (e: StopNowException) {
        throw e
    } catch (e: Exception) {
        if (afc2 == null) throw e
        log("  archive failed (${e.message}); copying the app bundle over AFC2 instead…")
        backupIpaViaAfc2(afc2, saver, app)
    }
}

/** installation_proxy Archive (works without jailbreak on iOS ≤ 8). */
internal fun MainActivity.backupIpaViaArchive(ip: InstallationProxy, afc: AfcClient, saver: IpaSaver, app: InstallationProxy.App) {
    runCatching { ip.removeArchive(app.bundleId) } // clear any leftover archive; fails harmlessly if none

    var lastPct = -1
    try {
        ip.archive(app.bundleId) { status, pct ->
            checkStopNow()
            if (pct != null && pct / 25 > lastPct / 25) { lastPct = pct; log("  archiving… $pct% ($status)") }
        }
    } catch (e: StopNowException) {
        // The iPod keeps archiving in the background and this connection is mid-command, so don't
        // send RemoveArchive now; the leftover is cleaned up at the start of the next backup.
        log("  (the iPod may finish this archive in the background; it's deleted next time you back up)")
        throw e
    }

    val target = saver.create(IpaSaver.ipaName(app.name, app.version, app.bundleId))
    try {
        var lastDecile = -1L
        val n = afc.copyFile("ApplicationArchives/${app.bundleId}.zip", target.stream) { copied, total ->
            val decile = if (total > 0) copied * 10 / total else -1L
            if (decile > lastDecile && decile % 2 == 0L) { lastDecile = decile; log("  copying ${mb(copied)} / ${mb(total)}") }
        }
        target.commit()
        log("  saved ${target.location} (${mb(n)})")
    } catch (e: Exception) {
        target.discard()
        throw e
    } finally {
        runCatching { ip.removeArchive(app.bundleId) }
            .onFailure { log("  (couldn't delete the archive on the iPod: ${it.message})") }
    }
}

// ---------------------------------------------------------------- uninstall

internal fun MainActivity.startUninstall(device: UsbDevice) {
    log("\nLoading installed apps…")
    var apps: List<InstallationProxy.App> = emptyList()
    runOnDevice(device, after = { runOnUiThread { showUninstallPicker(device, apps) } }) { mux, lockdown ->
        apps = withService(mux, lockdown, InstallationProxy.SERVICE) { InstallationProxy(it).browseUserApps() }
        log("${apps.size} user apps installed.")
    }
}

internal fun MainActivity.showUninstallPicker(device: UsbDevice, apps: List<InstallationProxy.App>) {
    if (isFinishing || apps.isEmpty()) return
    val saved = IpaSaver(this).existingNames()
    val isSaved = apps.map { IpaSaver.ipaName(it.name, it.version, it.bundleId) in saved }
    val labels = apps.mapIndexed { i, a -> (if (isSaved[i]) "✓ " else "") + "${a.name} ${a.version}${sizeLabel(a.sizeBytes)}" }
    val checked = BooleanArray(apps.size)
    MaterialAlertDialogBuilder(this)
        .setTitle("Uninstall which apps? (✓ = backed up)")
        .setView(checkList(labels, checked, 0.6))
        .setPositiveButton("Uninstall") { _, _ ->
            val chosen = apps.filterIndexed { i, _ -> checked[i] }
            if (chosen.isEmpty()) return@setPositiveButton
            val notSaved = chosen.filterIndexed { _, a -> !isSaved[apps.indexOf(a)] }
            MaterialAlertDialogBuilder(this)
                .setTitle("Uninstall ${chosen.size} app${if (chosen.size == 1) "" else "s"}?")
                .setMessage("Deletes ${chosen.joinToString { it.name }} and all their data (saves, settings) from the device." +
                    if (notSaved.isEmpty()) "" else "\n\n⚠ Not backed up: ${notSaved.joinToString { it.name }}. " +
                        "Apps no longer in the App Store can't be downloaded again.")
                .setPositiveButton("Uninstall") { _, _ -> runUninstall(device, chosen) }
                .setNegativeButton("Cancel", null)
                .show()
        }
        .setNegativeButton("Cancel", null)
        .show()
}

internal fun MainActivity.runUninstall(device: UsbDevice, apps: List<InstallationProxy.App>) {
    log("\nUninstalling ${apps.size} app(s)…")
    runOnDevice(device) { mux, lockdown ->
        withService(mux, lockdown, InstallationProxy.SERVICE) { s ->
            val ip = InstallationProxy(s)
            var ok = 0
            for ((i, a) in apps.withIndex()) {
                try {
                    ip.uninstall(a.bundleId)
                    ok++
                    log("[${i + 1}/${apps.size}] ${a.name} ✓")
                } catch (e: Exception) {
                    log("[${i + 1}/${apps.size}] ${a.name} FAILED: ${e.message}")
                }
            }
            log("Done: $ok uninstalled.")
        }
    }
}

// ---------------------------------------------------------------- auto-backup

/** On plug-in (when enabled): back up apps that aren't saved yet, without asking. */
internal fun MainActivity.autoBackup(device: UsbDevice) {
    log("\nAuto-backup: checking for new apps…")
    var todo: List<InstallationProxy.App> = emptyList()
    val saved = IpaSaver(this).existingNames()
    runOnDevice(device, after = {
        runOnUiThread {
            if (todo.isNotEmpty()) backup(device, todo, skipSaved = true, includeData = prefs.getBoolean("include_data", false), savedNames = saved)
        }
    }) { mux, lockdown ->
        val apps = withService(mux, lockdown, InstallationProxy.SERVICE) { InstallationProxy(it).browseUserApps() }
        todo = apps.filter { IpaSaver.ipaName(it.name, it.version, it.bundleId) !in saved }
        log(if (todo.isEmpty()) "All ${apps.size} apps are already backed up." else "${todo.size} new or updated app(s) to back up.")
    }
}
