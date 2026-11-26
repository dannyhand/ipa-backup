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

// Device info, power, system log, crash reports and the home screen.
// These are MainActivity extension functions: they run on the activity and use its state and helpers.

// ---------------------------------------------------------------- device tools

internal fun MainActivity.saveBytes(name: String, sub: String, bytes: ByteArray): String {
    val t = IpaSaver(this).create(name, sub)
    try {
        t.stream.write(bytes)
        t.commit()
        return t.location
    } catch (e: Exception) {
        t.discard()
        throw e
    }
}

internal fun MainActivity.today() = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

internal fun MainActivity.monoText(text: String) = TextView(this).apply {
    val pad = (16 * resources.displayMetrics.density).toInt()
    setPadding(pad, pad / 2, pad, pad / 2)
    typeface = Typeface.MONOSPACE
    textSize = 11.5f
    setTextIsSelectable(true)
    this.text = text
}

/** Model, iOS, serial numbers, storage, battery health and jailbreak status, shown and saveable as text. */
internal fun MainActivity.showDeviceInfo(device: UsbDevice) {
    log("\nReading device info…")
    var report: String? = null
    var owner = "device"
    runOnDevice(device, after = { report?.let { r -> runOnUiThread { showReport(r, owner) } } }) { mux, lockdown ->
        owner = lockdown.deviceName
        @Suppress("UNCHECKED_CAST")
        val info = (lockdown.value(null, null) as? Map<String, Any?>) ?: emptyMap()
        @Suppress("UNCHECKED_CAST")
        val disk = lockdown.value(null, "com.apple.disk_usage") as? Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val battery = lockdown.value(null, "com.apple.mobile.battery") as? Map<String, Any?>
        var gas: Map<String, Any?>? = null
        var power: Map<String, Any?>? = null
        runCatching {
            withService(mux, lockdown, DiagnosticsRelay.SERVICE) { s ->
                val d = DiagnosticsRelay(s)
                gas = d.gasGauge()
                power = d.ioRegistry("IOPMPowerSource")
                d.goodbye()
            }
        }.onFailure { log("(battery diagnostics not available: ${it.message})") }
        val afc2 = tryAfc2(mux, lockdown)
        val jb = if (afc2 == null) "not detected (no AFC2)" else afc2.use { s ->
            val afc = AfcClient(s)
            val cydia = runCatching { afc.stat("/Applications/Cydia.app") }.isSuccess
            "yes (AFC2 installed" + (if (cydia) ", Cydia present)" else ")")
        }
        report = DeviceReport.text(DeviceReport.Input(info, disk, battery, gas, power, jb, lockdown.deviceTime()))
        log("Done.")
    }
}

internal fun MainActivity.showReport(report: String, owner: String) {
    MaterialAlertDialogBuilder(this)
        .setTitle("Device info")
        .setView(android.widget.ScrollView(this).apply { addView(monoText(report)) })
        .setPositiveButton("Save") { _, _ ->
            runCatching { saveBytes(IpaSaver.clean("Device report ($owner) ${today()}") + ".txt", PERSONAL_SUBFOLDER, report.toByteArray()) }
                .onSuccess { log("Saved $it") }.onFailure { log("Save FAILED: ${it.message}") }
        }
        .setNeutralButton("Copy") { _, _ ->
            (getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                .setPrimaryClip(android.content.ClipData.newPlainText("Device report", report))
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
        }
        .setNegativeButton("Close", null)
        .show()
}

/** Restart, shut down or sleep the device through diagnostics_relay. */
internal fun MainActivity.showPower(device: UsbDevice) {
    val actions = arrayOf("Restart", "Shut down", "Sleep (turn the screen off)")
    MaterialAlertDialogBuilder(this)
        .setTitle("Power")
        .setItems(actions) { _, which ->
            log("\n${actions[which].substringBefore(" (")}…")
            runOnDevice(device) { mux, lockdown ->
                withService(mux, lockdown, DiagnosticsRelay.SERVICE) { s ->
                    val d = DiagnosticsRelay(s)
                    when (which) {
                        0 -> { d.restart(); log("Restarting. It'll reconnect once it's back on the home screen.") }
                        1 -> { d.shutdown(); log("Shutting down.") }
                        else -> { d.sleep(); log("Asleep ✓") }
                    }
                }
            }
        }
        .setNegativeButton("Cancel", null)
        .show()
}

// ---------------------------------------------------------------- system log

/**
 * Live console (com.apple.syslog_relay): the same messages Xcode's console shows. Each message ends
 * with a NUL byte. Useful for seeing why an app crashes or a profile or install is refused.
 */
internal fun MainActivity.showSyslog(device: UsbDevice) {
    log("\nStarting the live system log…")
    val all = ArrayList<String>()
    val pending = StringBuilder()
    val runningFlag = java.util.concurrent.atomic.AtomicBoolean(true)
    var paused = false
    var filter = ""
    val handler = android.os.Handler(mainLooper)
    val density = resources.displayMetrics.density
    val pad = (12 * density).toInt()
    val text = monoText("").apply { textSize = 10.5f; setTextIsSelectable(false) }
    val scroll = android.widget.ScrollView(this).apply { addView(text) }
    val filterBox = android.widget.EditText(this).apply {
        hint = "Filter (e.g. an app name)"
        isSingleLine = true
        textSize = 14f
    }
    val status = TextView(this).apply { textSize = 12f; alpha = 0.7f; setPadding(pad, 0, pad, 0) }
    fun render() {
        val shown = if (filter.isEmpty()) all.takeLast(1500) else all.filter { it.contains(filter, true) }.takeLast(1500)
        text.text = shown.joinToString("\n")
        status.text = "${all.size} lines" + (if (filter.isNotEmpty()) " · ${shown.size} match" else "") + if (paused) " · paused" else ""
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }
    filterBox.addTextChangedListener(object : android.text.TextWatcher {
        override fun afterTextChanged(s: android.text.Editable?) { filter = s?.toString()?.trim().orEmpty(); render() }
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
    })
    val body = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(pad, 0, pad, 0)
        addView(filterBox)
        addView(status)
        addView(scroll, LinearLayout.LayoutParams(-1, (resources.displayMetrics.heightPixels * 0.55).toInt()))
    }
    val tick = object : Runnable {
        override fun run() {
            if (!runningFlag.get()) return
            val chunk = synchronized(pending) { pending.toString().also { pending.setLength(0) } }
            if (chunk.isNotEmpty()) {
                synchronized(all) {
                    all += chunk.split('\u0000', '\n').map { it.trimEnd() }.filter { it.isNotEmpty() }
                    if (all.size > 20_000) all.subList(0, all.size - 20_000).clear()
                }
                if (!paused) render()
            }
            handler.postDelayed(this, 400)
        }
    }
    val dialog = MaterialAlertDialogBuilder(this)
        .setTitle("System log")
        .setView(body)
        .setPositiveButton("Save", null)
        .setNeutralButton("Pause", null)
        .setNegativeButton("Stop", null)
        .setCancelable(false)
        .create()
    dialog.setOnShowListener {
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val lines = synchronized(all) { all.joinToString("\n") }
            runCatching {
                saveBytes("System log (${ui.device?.substringBefore(" ·") ?: "device"}) " +
                    java.text.SimpleDateFormat("yyyy-MM-dd HH.mm", java.util.Locale.US).format(java.util.Date()) + ".txt",
                    PERSONAL_SUBFOLDER, (lines + "\n").toByteArray())
            }.onSuccess { Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show(); log("Saved $it") }
                .onFailure { log("Save FAILED: ${it.message}") }
        }
        dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL).setOnClickListener { b ->
            paused = !paused
            (b as android.widget.Button).text = if (paused) "Resume" else "Pause"
            render()
        }
        dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).setOnClickListener { runningFlag.set(false); dialog.dismiss() }
    }
    runOnDevice(device, after = { runningFlag.set(false); runOnUiThread { if (dialog.isShowing) status.text = "Disconnected · ${all.size} lines" } }) { mux, lockdown ->
        withService(mux, lockdown, "com.apple.syslog_relay") { s ->
            runOnUiThread { dialog.show(); handler.post(tick) }
            val buf = ByteArray(16384)
            while (runningFlag.get()) {
                val n = try {
                    s.readSome(buf, 0, buf.size, 1000)
                } catch (e: IOException) {
                    if (e.message?.startsWith("Read timed out") == true) continue
                    throw e
                }
                if (n < 0) break
                synchronized(pending) { pending.append(String(buf, 0, n, Charsets.UTF_8)) }
            }
        }
        log("System log stopped.")
    }
}

// ---------------------------------------------------------------- crash reports

/**
 * Crash logs: com.apple.crashreportmover first moves new reports out of the system's private
 * folders (it answers "ping" when done), then com.apple.crashreportcopymobile is an AFC service
 * rooted at the crash report folder.
 */
internal fun MainActivity.browseCrashReports(device: UsbDevice) {
    runPicker(
        device,
        what = "crash reports",
        dialogTitle = "Crash reports",
        subfolder = CRASH_SUBFOLDER,
        service = "com.apple.crashreportcopymobile",
        prepare = { mux, lockdown ->
            runCatching {
                withService(mux, lockdown, "com.apple.crashreportmover") { s -> runCatching { s.readFully(4, 30_000) } }
            }.onFailure { log("(couldn't run the crash report mover: ${it.message})") }
        },
        load = { afc ->
            val out = ArrayList<AfcClient.Entry>()
            fun walk(dir: String, depth: Int) {
                for (name in runCatching { afc.readDir(dir) }.getOrDefault(emptyList())) {
                    val e = runCatching { afc.stat(join(dir, name)) }.getOrNull() ?: continue
                    if (e.isDir) { if (depth < 3) walk(e.path, depth + 1) } else if (!e.isLink) out += e
                }
            }
            walk("/", 0)
            out.sortedByDescending { it.mtimeMs }
        },
        pick = { e ->
            val folder = parent(e.path).trim('/')
            Pick(e.path, e.path.substringAfterLast('/'),
                (if (e.mtimeMs > 0) shortDate(e.mtimeMs) + " · " else "") + humanSize(e.size) + if (folder.isNotEmpty()) " · $folder" else "",
                e.size)
        },
        saveOne = { afc, saver, e, used ->
            val name = MusicLibrary.uniqueName(IpaSaver.clean(e.path.trim('/').replace('/', ' ')), used)
            val t = saver.create(name, CRASH_SUBFOLDER)
            try {
                afc.copyFile(e.path, t.stream)
                t.commit()
                t.location
            } catch (ex: Exception) {
                t.discard()
                throw ex
            }
        },
    )
}

// ---------------------------------------------------------------- home screen

internal fun MainActivity.showHomeScreen(device: UsbDevice) {
    val actions = arrayOf("Save layout (icons and folders)", "Restore a saved layout…", "Save wallpapers")
    MaterialAlertDialogBuilder(this)
        .setTitle("Home screen")
        .setItems(actions) { _, which ->
            when (which) {
                0 -> saveLayout(device)
                1 -> { pendingLayoutDevice = device; pickLayout.launch(arrayOf("*/*")) }
                else -> saveWallpapers(device)
            }
        }
        .setNegativeButton("Cancel", null)
        .show()
}

internal fun MainActivity.saveLayout(device: UsbDevice) {
    log("\nSaving the home screen layout…")
    runOnDevice(device) { mux, lockdown ->
        val state = withService(mux, lockdown, SpringBoardServices.SERVICE) { SpringBoardServices(it).iconState() }
        val (pages, folders, apps) = HomeScreen.counts(state)
        val base = IpaSaver.clean("Home Screen (${lockdown.deviceName}) ${today()}")
        val where = saveBytes("$base.plist", PERSONAL_SUBFOLDER, Plist.toXml(state).toByteArray())
        saveBytes("$base.txt", PERSONAL_SUBFOLDER, HomeScreen.summary(state).toByteArray())
        log("Saved $where (and a .txt outline): $pages pages, $folders folders, $apps icons.")
    }
}

internal fun MainActivity.confirmRestoreLayout(device: UsbDevice, uri: android.net.Uri) {
    val state = runCatching { contentResolver.openInputStream(uri)!!.use { Plist.parse(it.readBytes()) } }.getOrNull()
    if (!HomeScreen.looksValid(state)) {
        log("That file isn't a saved home screen layout (pick a \"Home Screen … .plist\").")
        return
    }
    state as List<*>
    val (pages, folders, apps) = HomeScreen.counts(state)
    MaterialAlertDialogBuilder(this)
        .setTitle("Restore home screen?")
        .setMessage("Rearranges the home screen to this layout: $pages pages, $folders folders, $apps icons.\n\n" +
            "Apps that aren't installed are skipped, and apps not in the layout are added at the end.")
        .setPositiveButton("Restore") { _, _ ->
            log("\nRestoring the home screen layout…")
            runOnDevice(device) { mux, lockdown ->
                withService(mux, lockdown, SpringBoardServices.SERVICE) { s ->
                    SpringBoardServices(s).setIconState(state)
                    Thread.sleep(1500) // let SpringBoard read it before the connection closes
                }
                log("Sent ✓ The icons rearrange in a moment.")
            }
        }
        .setNegativeButton("Cancel", null)
        .show()
}

/** Home wallpaper from SpringBoard (no jailbreak needed); lock (and full-size home) wallpaper via AFC2. */
internal fun MainActivity.saveWallpapers(device: UsbDevice) {
    log("\nSaving wallpapers…")
    runOnDevice(device) { mux, lockdown ->
        val owner = IpaSaver.clean(lockdown.deviceName)
        var saved = 0
        runCatching {
            withService(mux, lockdown, SpringBoardServices.SERVICE) { SpringBoardServices(it).homeWallpaperPng() }
        }.getOrNull()?.let { png ->
            log("Saved " + saveBytes("Home Screen Wallpaper ($owner).png", PHOTOS_SUBFOLDER, (if (CgbiPng.isCgbi(png)) CgbiPng.toStandard(png) ?: png else png)))
            saved++
        }
        val afc2 = tryAfc2(mux, lockdown)
        if (afc2 == null) {
            log("The lock screen wallpaper needs a jailbroken device with AFC2.")
        } else afc2.use { s ->
            val afc = AfcClient(s)
            val dir = "/var/mobile/Library/SpringBoard"
            for ((file, label) in listOf("LockBackground" to "Lock Screen Wallpaper", "HomeBackground" to "Home Screen Wallpaper (full size)")) {
                try {
                    val bytes = java.io.ByteArrayOutputStream().also { afc.copyFile("$dir/$file.cpbitmap", it) }.toByteArray()
                    val img = CpBitmap.decode(bytes) ?: throw IOException("unrecognized .cpbitmap")
                    val bmp = android.graphics.Bitmap.createBitmap(img.argb, img.width, img.height, android.graphics.Bitmap.Config.ARGB_8888)
                    val png = java.io.ByteArrayOutputStream().also { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
                    log("Saved " + saveBytes("$label ($owner).png", PHOTOS_SUBFOLDER, png) + " (${img.width}×${img.height})")
                    saved++
                } catch (e: AfcException) {
                    if (file == "LockBackground") log("No custom lock screen wallpaper (the default one isn't stored as a file).")
                } catch (e: Exception) {
                    log("$label FAILED: ${e.message}")
                }
            }
        }
        log("Done: $saved saved.")
    }
}
