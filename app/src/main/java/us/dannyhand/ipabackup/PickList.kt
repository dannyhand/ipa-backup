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

// The generic pick-and-save list, and voice memos which use it.
// These are MainActivity extension functions: they run on the activity and use its state and helpers.

// ---------------------------------------------------------------- generic pick-and-save list

/**
 * Shared flow for Voice Memos and Books: load items on the worker thread, show a checklist,
 * confirm with size / free space, then save the selection with Stop support.
 */
internal fun <T> MainActivity.runPicker(
    device: UsbDevice,
    what: String,
    dialogTitle: String,
    subfolder: String,
    load: (AfcClient) -> List<T>,
    pick: (T) -> Pick,
    saveOne: (AfcClient, IpaSaver, T, MutableSet<String>) -> String,
    confirmNote: (List<T>) -> String? = { null },
    service: String = AfcClient.SERVICE,
    prepare: (UsbMux, DeviceSession) -> Unit = { _, _ -> },
) {
    log("\nReading $what…")
    val cmds = LinkedBlockingQueue<PickCmd>()
    val selected = HashSet<String>()
    runOnDevice(device, after = { setStopButtonVisible(false); stopMode = StopMode.NONE }) { mux, lockdown ->
        prepare(mux, lockdown)
        withService(mux, lockdown, service) { stream ->
            val afc = AfcClient(stream)
            val items = load(afc)
            if (items.isEmpty()) { log("No $what found on the iPod."); return@withService }
            val picks = items.map(pick)
            val byKey = items.associateBy { pick(it).key }
            log("${items.size} $what (${humanSize(picks.sumOf { it.size })}).")
            cmds.offer(PickCmd.Show)
            while (true) {
                val cmd = cmds.poll(15, TimeUnit.MINUTES) ?: break
                when (cmd) {
                    is PickCmd.Close -> break
                    is PickCmd.Show -> runOnUiThread {
                        showChecklist(dialogTitle, picks, selected, cmds) { chosen ->
                            confirmPicks(what, subfolder, chosen, chosen.mapNotNull { byKey[it.key] }.let(confirmNote), cmds)
                        }
                    }
                    is PickCmd.Save -> {
                        val chosen = cmd.keys.mapNotNull { byKey[it] }
                        savePicks(afc, what, subfolder, chosen, saveOne)
                        selected.clear()
                        cmds.offer(PickCmd.Show)
                    }
                }
            }
        }
    }
}

internal fun MainActivity.showChecklist(
    title: String,
    picks: List<Pick>,
    selected: MutableSet<String>,
    cmds: LinkedBlockingQueue<PickCmd>,
    onSave: (List<Pick>) -> Unit,
) {
    if (isFinishing) { cmds.offer(PickCmd.Close); return }
    val density = resources.displayMetrics.density
    val pad = (16 * density).toInt()
    val hint = TextView(this).apply { textSize = 12f; alpha = 0.7f; setPadding(pad, pad / 4, pad, pad / 4) }
    val adapter = object : BaseAdapter() {
        override fun getCount() = picks.size
        override fun getItem(position: Int) = picks[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val tv = convertView as? TextView ?: TextView(this@showChecklist).apply {
                val v = (10 * density).toInt()
                setPadding(pad, v, pad, v)
                textSize = 15f
            }
            val p = picks[position]
            tv.text = (if (p.key in selected) "☑  " else "☐  ") + p.title + "\n      " + p.subtitle
            tv.setBackgroundColor(if (p.key in selected) 0x331E88E5 else 0)
            return tv
        }
    }
    val list = ListView(this).apply {
        this.adapter = adapter
        isFastScrollEnabled = true
        isFastScrollAlwaysVisible = picks.size > 30
    }
    val container = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(hint)
        addView(list, LinearLayout.LayoutParams(-1, (resources.displayMetrics.heightPixels * 0.55).toInt()))
    }
    val dialog = MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .setView(container)
        .setPositiveButton("Save", null)
        .setNeutralButton("Select all", null)
        .setNegativeButton("Close", null)
        .create()
    var handled = false
    fun refresh() {
        adapter.notifyDataSetChanged()
        val chosen = picks.filter { it.key in selected }
        hint.text = "${picks.size} items · ${chosen.size} selected (${humanSize(chosen.sumOf { it.size })}) · tap to select"
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)?.apply {
            isEnabled = chosen.isNotEmpty()
            text = if (chosen.isEmpty()) "Save" else "Save ${chosen.size}"
        }
        dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL)?.text = if (chosen.size == picks.size) "Select none" else "Select all"
    }
    list.setOnItemClickListener { _, _, position, _ ->
        val k = picks[position].key
        if (!selected.add(k)) selected.remove(k)
        refresh()
    }
    dialog.setOnShowListener {
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val chosen = picks.filter { it.key in selected }
            if (chosen.isNotEmpty()) { handled = true; dialog.dismiss(); onSave(chosen) }
        }
        dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
            if (selected.size == picks.size) selected.clear() else picks.forEach { selected += it.key }
            refresh()
        }
        dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).setOnClickListener {
            if (!handled) { handled = true; cmds.offer(PickCmd.Close) }
            dialog.dismiss()
        }
        refresh()
    }
    dialog.setOnCancelListener { if (!handled) { handled = true; cmds.offer(PickCmd.Close) } }
    dialog.show()
}

internal fun MainActivity.confirmPicks(what: String, subfolder: String, chosen: List<Pick>, note: String?, cmds: LinkedBlockingQueue<PickCmd>) {
    val total = chosen.sumOf { it.size }
    val free = runCatching {
        android.os.StatFs(android.os.Environment.getExternalStorageDirectory().path).availableBytes
    }.getOrDefault(-1L)
    val msg = buildString {
        append("Save ${chosen.size} ${if (chosen.size == 1) what.removeSuffix("s") else what} (${humanSize(total)}) to ")
        append("Download/${IpaSaver.FOLDER}/$subfolder/?")
        note?.let { append("\n\n").append(it) }
        if (free >= 0) {
            append("\n\nFree space on this phone: ${humanSize(free)}")
            if (total > free) append("\n⚠️ Not enough space for all of them.")
        }
    }
    var handled = false
    MaterialAlertDialogBuilder(this)
        .setMessage(msg)
        .setPositiveButton("Save") { _, _ -> handled = true; cmds.offer(PickCmd.Save(chosen.map { it.key })) }
        .setNegativeButton("Back") { _, _ -> handled = true; cmds.offer(PickCmd.Show) }
        .setOnDismissListener { if (!handled) { handled = true; cmds.offer(PickCmd.Show) } }
        .show()
}

internal fun <T> MainActivity.savePicks(
    afc: AfcClient,
    what: String,
    subfolder: String,
    items: List<T>,
    saveOne: (AfcClient, IpaSaver, T, MutableSet<String>) -> String,
) {
    val saver = IpaSaver(this)
    val used = HashSet<String>()
    var ok = 0
    var failed = 0
    var done = 0
    stopMode = StopMode.NONE
    keepScreenOn(true)
    setStopButtonVisible(true)
    afc.cancelCheck = { checkStopNow() }
    log("\nSaving ${items.size} $what to Download/${IpaSaver.FOLDER}/$subfolder/")
    try {
        for ((i, item) in items.withIndex()) {
            if (stopMode != StopMode.NONE) break
            try {
                val where = saveOne(afc, saver, item, used)
                log("[${i + 1}/${items.size}] ${where.substringAfterLast('/')}")
                ok++
            } catch (e: StopNowException) {
                log("  stopped; the partial file was discarded")
                break
            } catch (e: Exception) {
                log("[${i + 1}/${items.size}] FAILED: ${e.message}")
                failed++
            }
            done = i + 1
        }
    } finally {
        afc.cancelCheck = {}
        setStopButtonVisible(false)
        keepScreenOn(false)
    }
    val stopped = stopMode != StopMode.NONE
    stopMode = StopMode.NONE
    log((if (stopped) "Stopped" else "Done") + ": $ok saved" +
        (if (failed > 0) ", $failed failed" else "") +
        (if (stopped && done < items.size) ", ${items.size - done} not saved" else "") + ".")
}

/** Copies a SQLite database (plus -wal/-shm/-journal companions) into app cache. Null if it isn't there. */
internal fun MainActivity.copySqlite(afc: AfcClient, devicePath: String, tag: String): java.io.File? {
    val dir = java.io.File(cacheDir, "db/$tag").apply { deleteRecursively(); mkdirs() }
    val name = devicePath.substringAfterLast('/')
    for (suffix in listOf("", "-wal", "-shm", "-journal")) {
        val src = devicePath + suffix
        if (suffix.isNotEmpty() && runCatching { afc.stat(src) }.isFailure) continue
        try {
            java.io.FileOutputStream(java.io.File(dir, name + suffix)).use { afc.copyFile(src, it) }
        } catch (e: Exception) {
            if (suffix.isEmpty()) return null
        }
    }
    return java.io.File(dir, name)
}

internal fun <R> MainActivity.withSqlite(file: java.io.File, block: (us.dannyhand.ipabackup.music.SqlDb) -> R): R =
    android.database.sqlite.SQLiteDatabase.openDatabase(
        file.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE, // our private copy
    ).use { block(AndroidSqlDb(it)) }

internal fun MainActivity.shortDate(ms: Long): String =
    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(ms))

/** Saves [devicePath] as [name]; stages through cache and writes tags when [tags] is given (m4a/mp3). */
internal fun MainActivity.saveAudio(afc: AfcClient, saver: IpaSaver, devicePath: String, name: String, sub: String, dateMs: Long?, tags: AudioTagger.Tags?): String {
    val ext = name.substringAfterLast('.', "").lowercase()
    val target = saver.create(name, sub, dateMs)
    try {
        if (tags != null && ext in TAGGABLE) {
            val tmp = java.io.File(cacheDir, "audio.tmp")
            try {
                java.io.FileOutputStream(tmp).use { afc.copyFile(devicePath, it) }
                try {
                    AudioTagger.writeTagged(tmp, ext, tags, target.stream)
                } catch (e: Exception) {
                    target.discard()
                    val plain = saver.create(name, sub, dateMs)
                    tmp.inputStream().use { it.copyTo(plain.stream) }
                    plain.commit()
                    return plain.location
                }
            } finally {
                tmp.delete()
            }
        } else {
            afc.copyFile(devicePath, target.stream)
        }
        target.commit()
        return target.location
    } catch (e: Exception) {
        target.discard()
        throw e
    }
}

// ---------------------------------------------------------------- voice memos

internal fun MainActivity.browseVoiceMemos(device: UsbDevice) = runPicker(
    device = device,
    what = "voice memos",
    dialogTitle = "Voice Memos",
    subfolder = MEMOS_SUBFOLDER,
    load = { afc ->
        val files = runCatching { afc.readDir(VoiceMemos.DIR) }.getOrDefault(emptyList()).mapNotNull { n ->
            val e = runCatching { afc.stat("${VoiceMemos.DIR}/$n") }.getOrNull() ?: return@mapNotNull null
            if (e.isDir) null else VoiceMemos.FileEntry(n, e.size, e.mtimeMs)
        }
        val meta = runCatching {
            copySqlite(afc, VoiceMemos.DB_PATH, "recordings")?.let { f -> withSqlite(f) { VoiceMemos.readDb(it) } }
        }.onFailure { log("(Couldn't read Recordings.db: ${it.message}; using file dates)") }.getOrNull() ?: emptyMap()
        VoiceMemos.combine(files, meta)
    },
    pick = { m ->
        val dur = m.durationSec?.let { s -> " · %d:%02d".format(s.toInt() / 60, s.toInt() % 60) } ?: ""
        Pick(m.devicePath, m.label ?: "Voice Memo", "${shortDate(m.dateMs)}$dur · ${humanSize(m.size)}", m.size)
    },
    saveOne = { afc, saver, m, used ->
        val title = (m.label ?: "Voice Memo") + " " + shortDate(m.dateMs)
        val year = java.util.Calendar.getInstance().apply { timeInMillis = m.dateMs }.get(java.util.Calendar.YEAR)
        saveAudio(afc, saver, m.devicePath, MusicLibrary.uniqueName(m.saveName(), used), MEMOS_SUBFOLDER, m.dateMs,
            AudioTagger.Tags(title = title, album = "Voice Memos", year = year))
    },
)
