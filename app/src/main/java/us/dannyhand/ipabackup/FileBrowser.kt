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

// The file browser: browse, save files and zipped folders, upload from the phone.
// These are MainActivity extension functions: they run on the activity and use its state and helpers.

// ---------------------------------------------------------------- file browser

/** Root filesystem via AFC2 when jailbroken, else the media folder via regular AFC. */
internal fun MainActivity.browseFiles(device: UsbDevice) {
    log("\nConnecting for file browsing…")
    val cmds = LinkedBlockingQueue<BrowserCmd>()
    runOnDevice(device) { mux, lockdown ->
        val afc2 = tryAfc2(mux, lockdown)
        val stream: MuxStream
        val label: String
        if (afc2 != null) {
            stream = afc2
            label = "iPod (root, AFC2)"
            log("AFC2 available: browsing the whole filesystem.")
        } else {
            val svc = lockdown.startService(AfcClient.SERVICE)
            stream = mux.connect(svc.port)
            label = "iPod Media"
            log("AFC2 not available (needs a jailbroken iPod with the AFC2 package). Browsing the media folder instead.")
        }
        stream.use {
            val afc = AfcClient(stream)
            val saver = IpaSaver(this)
            cmds.offer(BrowserCmd.Open("/"))
            while (true) {
                // Close quietly if left idle.
                val cmd = cmds.poll(15, TimeUnit.MINUTES) ?: break
                when (cmd) {
                    is BrowserCmd.Close -> break
                    is BrowserCmd.Open -> {
                        val entries = try {
                            afc.readDir(cmd.path).sortedBy { it.lowercase() }.map { name ->
                                BrowserEntry(name, runCatching { afc.stat(join(cmd.path, name)) }.getOrNull())
                            }
                        } catch (e: AfcException) {
                            // The device refused (e.g. no permission): go back up a level.
                            log("Can't open ${cmd.path}: ${e.message}")
                            if (cmd.path != "/") cmds.offer(BrowserCmd.Open(parent(cmd.path))) else break
                            continue
                        } catch (e: Exception) {
                            // Anything else means the connection itself is gone (cable out, iPod asleep).
                            log("Lost the connection to the iPod (${e.message}). Check the OTG adapter.")
                            break
                        }
                        runOnUiThread { showBrowser(label, cmd.path, entries, cmds) }
                    }
                    is BrowserCmd.Prepare -> prepareSave(afc, cmd, cmds)
                    is BrowserCmd.Upload -> {
                        if (!uploadFiles(afc, cmd, viaAfc2 = afc2 != null)) break
                        cmds.offer(BrowserCmd.Open(cmd.dir))
                    }
                    is BrowserCmd.Download -> {
                        val many = cmd.items.size > 1
                        var saved = 0
                        var lost = false
                        for ((i, item) in cmd.items.withIndex()) {
                            if (many) log("[${i + 1}/${cmd.items.size}] ${item.path}")
                            try {
                                downloadPath(afc, saver, item.path, item.isDir)
                                saved++
                            } catch (e: AfcException) {
                                log("Download failed: ${e.message}")
                            } catch (e: Exception) {
                                // Connection gone: the partial file on the phone was already discarded.
                                // Nothing is written to the iPod when saving, so nothing is left there.
                                log("Download failed: ${e.message}")
                                log("Lost the connection to the iPod. Check the OTG adapter.")
                                lost = true
                                break
                            }
                        }
                        if (many) log(if (saved == cmd.items.size) "Saved $saved items." else "Saved $saved of ${cmd.items.size} items.")
                        if (lost) break
                        cmds.offer(BrowserCmd.Open(cmd.returnTo))
                    }
                }
            }
        }
        log("File browser closed.")
    }
}

/** Worker thread: totals the selection (walking folders, with a cancellable progress dialog), then asks to confirm. */
internal fun MainActivity.prepareSave(afc: AfcClient, cmd: BrowserCmd.Prepare, cmds: LinkedBlockingQueue<BrowserCmd>) {
    val dirs = cmd.items.filter { it.isDir }
    val files = cmd.items.filter { !it.isDir }
    measureCancelled = false
    if (dirs.isNotEmpty()) showMeasuring()
    try {
        val folders = TreeSize()
        for (d in dirs) {
            val before = folders.copy()
            folders.add(afc.measureTree(
                d.path,
                zipPrefix = zipBaseName(d.path) + "/",
                onProgress = { t -> if (t.files % 20 == 0) updateMeasuring(before.files + t.files, before.bytes + t.bytes) },
                isCancelled = { measureCancelled },
            ))
        }
        dismissMeasuring()
        val msg = saveSummary(files, dirs, folders)
        runOnUiThread { confirmSave(msg, cmd, cmds) }
    } catch (e: CancelledException) {
        dismissMeasuring()
        cmds.offer(BrowserCmd.Open(cmd.returnTo))
    } catch (e: Exception) {
        dismissMeasuring()
        log("Couldn't measure: ${e.message}")
        cmds.offer(BrowserCmd.Open(cmd.returnTo))
    }
}

internal fun MainActivity.saveSummary(files: List<BrowserItem>, dirs: List<BrowserItem>, folders: TreeSize): String = buildString {
    val fileBytes = files.sumOf { it.size }
    when {
        dirs.isEmpty() && files.size == 1 ->
            append("Save ${files[0].path.substringAfterLast('/')} (${humanSize(fileBytes)})?")
        files.isEmpty() && dirs.size == 1 -> {
            val name = zipBaseName(dirs[0].path)
            append("Save the folder “$name” as $name.zip?\n\n")
            append("${folders.files} files, ${humanSize(folders.bytes)} uncompressed\n")
            append("Estimated .zip size: about ${humanSize(folders.estimatedZipBytes)}")
        }
        else -> {
            append("Save ${files.size + dirs.size} items?\n\n")
            if (files.isNotEmpty()) append("• ${files.size} file(s): ${humanSize(fileBytes)}\n")
            if (dirs.isNotEmpty()) {
                append("• ${dirs.size} folder(s) as .zip: ${folders.files} files, ${humanSize(folders.bytes)} uncompressed, ")
                append("about ${humanSize(folders.estimatedZipBytes)} zipped\n")
            }
            append("\nTotal: about ${humanSize(fileBytes + folders.estimatedZipBytes)}")
        }
    }
    append("\n\nSaves to Download/${IpaSaver.FOLDER}/$FILES_SUBFOLDER/")
}

internal fun MainActivity.confirmSave(msg: String, cmd: BrowserCmd.Prepare, cmds: LinkedBlockingQueue<BrowserCmd>) {
    var handled = false
    MaterialAlertDialogBuilder(this)
        .setMessage(msg)
        .setPositiveButton("Save") { _, _ -> handled = true; cmds.offer(BrowserCmd.Download(cmd.items, cmd.returnTo)) }
        .setNegativeButton("Back") { _, _ -> handled = true; cmds.offer(BrowserCmd.Open(cmd.returnTo)) }
        .setOnDismissListener { if (!handled) { handled = true; cmds.offer(BrowserCmd.Open(cmd.returnTo)) } }
        .show()
}

internal fun MainActivity.showMeasuring() = runOnUiThread {
    val pad = (20 * resources.displayMetrics.density).toInt()
    val tv = TextView(this).apply { text = "Counting files…"; setPadding(pad, pad / 2, pad, 0) }
    measureText = tv
    measureDialog = MaterialAlertDialogBuilder(this)
        .setTitle("Measuring folder")
        .setView(tv)
        .setNegativeButton("Cancel") { _, _ -> measureCancelled = true }
        .setCancelable(false)
        .show()
}

internal fun MainActivity.updateMeasuring(files: Int, bytes: Long) = runOnUiThread {
    measureText?.text = "Counting files…\n$files files, ${humanSize(bytes)} so far"
}

internal fun MainActivity.dismissMeasuring() = runOnUiThread {
    measureDialog?.dismiss()
    measureDialog = null
    measureText = null
}

/**
 * Folder view: tap opens folders / saves a file; hold starts multi-select (tap then toggles).
 * The current path is shown as selectable text. Back goes up a folder (or clears the selection).
 */
internal fun MainActivity.showBrowser(label: String, path: String, entries: List<BrowserEntry>, cmds: LinkedBlockingQueue<BrowserCmd>) {
    if (isFinishing) { cmds.offer(BrowserCmd.Close); return }
    val atRoot = path == "/"
    val density = resources.displayMetrics.density
    val pad = (16 * density).toInt()
    val selected = linkedSetOf<Int>() // indices into entries
    var selecting = false
    // Row codes: -1 = "..", -2 = "(empty)", otherwise an index into entries.
    val rows: List<Int> = (if (atRoot) emptyList() else listOf(-1)) + entries.indices +
        (if (entries.isEmpty()) listOf(-2) else emptyList())

    fun selectable(i: Int) = i >= 0 && entries[i].entry?.let { !it.isLink } == true

    val pathView = TextView(this).apply {
        text = path
        setTextIsSelectable(true)
        typeface = Typeface.MONOSPACE
        textSize = 13f
        setPadding(pad, pad / 2, pad, pad / 4)
    }
    val hintView = TextView(this).apply {
        textSize = 12f
        alpha = 0.7f
        setPadding(pad, 0, pad, pad / 4)
    }
    val adapter = object : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(position: Int) = rows[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val tv = convertView as? TextView ?: TextView(this@showBrowser).apply {
                val v = (12 * density).toInt()
                setPadding(pad, v, pad, v)
                textSize = 15f
            }
            val i = rows[position]
            tv.text = when (i) {
                -1 -> "⬆  .."
                -2 -> "(empty)"
                else -> {
                    val (name, e) = entries[i]
                    val box = if (selecting && selectable(i)) (if (i in selected) "☑ " else "☐ ") else ""
                    val icon = when { e == null -> "?"; e.isDir -> "📁"; e.isLink -> "↪"; else -> "📄" }
                    val size = if (e != null && !e.isDir && !e.isLink) "  (${humanSize(e.size)})" else ""
                    "$box$icon  $name$size"
                }
            }
            tv.setBackgroundColor(if (i >= 0 && i in selected) 0x331E88E5 else 0)
            return tv
        }
    }
    val list = ListView(this).apply {
        this.adapter = adapter
        isFastScrollEnabled = true
        isFastScrollAlwaysVisible = rows.size > 30
    }
    val container = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(pathView)
        addView(hintView)
        addView(list, LinearLayout.LayoutParams(-1, (resources.displayMetrics.heightPixels * 0.55).toInt()))
    }

    val dialog = MaterialAlertDialogBuilder(this)
        .setTitle(label)
        .setView(container)
        .setPositiveButton("Save", null)
        .setNeutralButton("Save folder (.zip)", null)
        .setNegativeButton("Close", null)
        .create()

    var handled = false // exactly one command per dialog
    fun go(cmd: BrowserCmd) {
        if (!handled) { handled = true; cmds.offer(cmd) }
        dialog.dismiss()
    }

    fun refresh() {
        adapter.notifyDataSetChanged()
        val pos = dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
        val neu = dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL)
        if (selecting) {
            hintView.text = "${selected.size} selected · tap to check or uncheck"
            pos.visibility = View.VISIBLE
            pos.isEnabled = selected.isNotEmpty()
            pos.text = "Save ${selected.size}"
            neu.visibility = View.VISIBLE
            neu.text = "Cancel selection"
        } else {
            hintView.text = "Tap to open · hold to select several"
            pos.visibility = View.VISIBLE
            pos.isEnabled = true
            pos.text = "Upload here"
            neu.visibility = if (atRoot) View.GONE else View.VISIBLE
            neu.text = "Save folder (.zip)"
        }
    }

    fun clearSelection() {
        selecting = false
        selected.clear()
        refresh()
    }

    list.setOnItemClickListener { _, _, position, _ ->
        val i = rows[position]
        when {
            i == -2 -> {}
            i == -1 -> if (!selecting) go(BrowserCmd.Open(parent(path)))
            selecting -> if (selectable(i)) {
                if (!selected.add(i)) selected.remove(i)
                refresh()
            }
            else -> {
                val (name, e) = entries[i]
                val full = join(path, name)
                if (e == null || e.isDir || e.isLink) go(BrowserCmd.Open(full)) // links: try to open as folder
                else go(BrowserCmd.Prepare(listOf(BrowserItem(full, false, e.size)), path))
            }
        }
    }
    list.setOnItemLongClickListener { _, _, position, _ ->
        val i = rows[position]
        if (selectable(i)) {
            selecting = true
            if (!selected.add(i)) selected.remove(i)
            refresh()
        } else if (i >= 0) {
            Toast.makeText(this, "Links can't be selected", Toast.LENGTH_SHORT).show()
        }
        true
    }

    dialog.setOnShowListener {
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            if (!selecting) {
                // Hand the queue to the picker callback, which sends Upload (or reopens this folder if cancelled).
                if (!handled) {
                    handled = true
                    pendingUpload = cmds to path
                    dialog.dismiss()
                    pickUploads.launch(arrayOf("*/*"))
                }
                return@setOnClickListener
            }
            val items = selected.sorted().mapNotNull { i ->
                val (name, e) = entries[i]
                e?.let { BrowserItem(join(path, name), it.isDir, it.size) }
            }
            if (items.isNotEmpty()) go(BrowserCmd.Prepare(items, path))
        }
        dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
            if (selecting) clearSelection() else go(BrowserCmd.Prepare(listOf(BrowserItem(path, true, 0)), path))
        }
        dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).setOnClickListener { go(BrowserCmd.Close) }
        refresh()
    }
    dialog.setOnKeyListener { _, keyCode, event ->
        if (keyCode != KeyEvent.KEYCODE_BACK) return@setOnKeyListener false
        if (!selecting && atRoot) return@setOnKeyListener false // normal back: closes the browser
        if (event.action == KeyEvent.ACTION_UP) {
            if (selecting) clearSelection() else go(BrowserCmd.Open(parent(path)))
        }
        true
    }
    dialog.setOnCancelListener { if (!handled) { handled = true; cmds.offer(BrowserCmd.Close) } }
    dialog.show()
}

/** Name and size of a picked document. */
internal fun MainActivity.docInfo(uri: android.net.Uri): Pair<String, Long> {
    var name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
    var size = -1L
    contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            c.getString(0)?.let { name = it }
            if (!c.isNull(1)) size = c.getLong(1)
        }
    }
    return name.replace('/', '_') to size
}

/** "name.ext", or "name (2).ext", "name (3).ext"… so an upload never overwrites anything on the device. */
internal fun MainActivity.freeName(afc: AfcClient, dir: String, name: String): String {
    fun exists(n: String) = runCatching { afc.fileInfo(join(dir, n)) }.isSuccess
    if (!exists(name)) return name
    val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
    var k = 2
    while (exists("${name.substring(0, dot)} ($k)${name.substring(dot)}")) k++
    return "${name.substring(0, dot)} ($k)${name.substring(dot)}"
}

/**
 * Worker thread: copies phone files into a device folder. AFC can write anywhere its root allows
 * (all of /var/mobile/Media on regular AFC, the whole filesystem with AFC2). Files show up in the
 * file browser, but the Music, Photos and Videos apps only list what's in their databases (which
 * iTunes writes when syncing), so music or photos copied this way won't appear in those apps.
 * Returns false if the connection was lost.
 */
internal fun MainActivity.uploadFiles(afc: AfcClient, cmd: BrowserCmd.Upload, viaAfc2: Boolean): Boolean {
    val items = cmd.uris.map { it to docInfo(it) }
    log("\nUploading ${items.size} file${if (items.size == 1) "" else "s"} (${humanSize(items.sumOf { maxOf(it.second.second, 0) })}) to ${cmd.dir}")
    var ok = 0
    for ((i, item) in items.withIndex()) {
        val (uri, info) = item
        val (name, size) = info
        try {
            val target = freeName(afc, cmd.dir, name)
            log("[${i + 1}/${items.size}] $target" + if (target != name) "  (a file named $name is already there)" else "")
            var lastPct = -1L
            // Remembered until the copy finishes, so Clean up can delete it if the cable comes out.
            prefs.edit().putString(PREF_PARTIAL_UPLOAD, (if (viaAfc2) "afc2:" else "afc:") + join(cmd.dir, target)).commit()
            val n = contentResolver.openInputStream(uri)?.use { input ->
                afc.writeFile(join(cmd.dir, target), input, size) { done, total ->
                    if (total > 8L shl 20) { // progress only for big files
                        val pct = done * 100 / total / 25 * 25
                        if (pct != lastPct && pct in 1..99) { lastPct = pct; log("  $pct%") }
                    }
                }
            } ?: throw IOException("Can't open $name on the phone")
            prefs.edit().remove(PREF_PARTIAL_UPLOAD).apply()
            ok++
            log("  ✓ ${humanSize(n)}")
        } catch (e: AfcException) {
            log("  FAILED: ${e.message}")
            prefs.getString(PREF_PARTIAL_UPLOAD, null)?.let { runCatching { afc.remove(it.substringAfter(':')) } }
            prefs.edit().remove(PREF_PARTIAL_UPLOAD).apply()
        } catch (e: Exception) {
            if (e is IOException && e.message?.startsWith("Can't open") == true) {
                log("  FAILED: ${e.message}")
                prefs.edit().remove(PREF_PARTIAL_UPLOAD).apply()
                continue
            }
            log("  FAILED: ${e.message}")
            log("Lost the connection to the iPod. Check the OTG adapter. Tools › Clean up can delete the partly copied file.")
            return false
        }
    }
    log(if (ok == items.size) "Done: $ok uploaded." else "Done: $ok of ${items.size} uploaded.")
    return true
}

internal fun MainActivity.zipBaseName(path: String) = IpaSaver.clean(path.trimEnd('/').substringAfterLast('/').ifEmpty { "root" })

internal fun MainActivity.downloadPath(afc: AfcClient, saver: IpaSaver, path: String, isDir: Boolean) {
    val base = zipBaseName(path)
    if (isDir) {
        val target = saver.create("$base.zip", FILES_SUBFOLDER)
        try {
            log("Zipping $path …")
            val zos = AfcZipper.open(target.stream)
            val z = AfcZipper(afc, zos)
            z.addTree(path, "$base/")
            zos.finish()
            target.commit()
            log("Saved ${target.location} (${z.files} files, ${humanSize(z.bytes)} uncompressed)")
        } catch (e: Exception) {
            target.discard()
            throw e
        }
    } else {
        val target = saver.create(base, FILES_SUBFOLDER)
        try {
            val n = afc.copyFile(path, target.stream)
            target.commit()
            log("Saved ${target.location} (${humanSize(n)})")
        } catch (e: Exception) {
            target.discard()
            throw e
        }
    }
}
