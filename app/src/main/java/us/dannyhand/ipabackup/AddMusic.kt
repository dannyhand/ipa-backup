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
import us.dannyhand.ipabackup.music.SongImport
import us.dannyhand.ipabackup.util.AudioInfo

// Adding songs, part 1: pick files on the phone, read and check them, and plan where they'd go.
// Writing them into the device's library comes once MediaLibrary.sqlitedb's layout is confirmed.
// These are MainActivity extension functions: they run on the activity and use its state and helpers.

/** A song staged in the app cache, with what we learned about it. */
internal class StagedSong(val file: java.io.File, val candidate: SongImport.Candidate, val coverJpeg: java.io.File?) {
    var devicePath: String? = null
}

internal fun MainActivity.isDeviceConnected() =
    usbManager.deviceList.values.any { AppleUsbTransport.isApple(it) && AppleUsbTransport.findMuxInterface(it) != null }

/** Music works without a device for "Add songs" (to try picking and checking files). */
internal fun MainActivity.startMusic() {
    if (busy) { log("Busy, please wait for the current operation to finish."); return }
    if (isDeviceConnected()) withDevice { showMusicMenu(it) } else showMusicMenu(null)
}

internal fun MainActivity.launchSongPicker(device: UsbDevice?) {
    addSongsDevice = device
    addSongsPending = true
    pickSongs.launch(arrayOf("audio/*", "application/octet-stream"))
}

/** Copies the picked files into the app cache, reads their tags and cover, then checks them against the device. */
internal fun MainActivity.prepareSongs(device: UsbDevice?, uris: List<android.net.Uri>) {
    val dir = java.io.File(cacheDir, "add-music").apply { deleteRecursively(); mkdirs() }
    busy = true
    log("\nReading ${uris.size} file(s)…")
    kotlin.concurrent.thread(name = "add-songs") {
        val staged = ArrayList<StagedSong>()
        try {
            for ((i, uri) in uris.withIndex()) {
                val (name, size) = docInfo(uri)
                val ext = name.substringAfterLast('.', "").lowercase()
                val file = java.io.File(dir, "$i.$ext")
                try {
                    contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
                } catch (e: Exception) {
                    log("  $name: can't read it (${e.message})")
                    continue
                }
                val info = runCatching { AudioInfo.read(file) }.getOrNull()
                val c = SongImport.Candidate(name, ext, if (size > 0) size else file.length(), info, SongImport.classify(ext, info))
                val cover = info?.cover?.let { runCatching { coverJpeg(it, java.io.File(dir, "$i.jpg")) }.getOrNull() }
                staged += StagedSong(file, c, cover)
            }
        } finally {
            busy = false
        }
        if (staged.isEmpty()) { log("Nothing to add."); return@thread }
        if (device == null) {
            log("No device connected: checked the files only (not whether they're already on the device).")
            runOnUiThread { showSongReview(null, staged) }
            return@thread
        }
        runOnDevice(device, after = { runOnUiThread { showSongReview(device, staged) } }) { mux, lockdown ->
            withService(mux, lockdown, AfcClient.SERVICE) { s ->
                val afc = AfcClient(s)
                val library = loadMusicLibrary(afc) ?: emptyList()
                SongImport.markDuplicates(staged.map { it.candidate }, library)
                // Where each new file would go, avoiding names already used on the device.
                val folders = HashMap<String, MutableSet<String>>()
                for (f in runCatching { afc.readDir(SongImport.MUSIC_DIR) }.getOrDefault(emptyList()).filter(SongImport::isMusicFolder))
                    folders[f] = runCatching { afc.readDir("${SongImport.MUSIC_DIR}/$f") }.getOrDefault(emptyList()).toHashSet()
                for (st in staged) if (st.candidate.status.ready) {
                    val ext = if (st.candidate.ext == "aif") "aiff" else st.candidate.ext
                    st.devicePath = SongImport.planPath(ext, folders)
                }
                log("Checked against ${library.size} items in the library (${folders.size} music folders).")
            }
        }
    }
}

/** Embedded cover art as a JPEG no bigger than 600×600 (what the library's artwork needs at most). */
internal fun coverJpeg(bytes: ByteArray, out: java.io.File): java.io.File? {
    val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val scale = minOf(1f, 600f / maxOf(bmp.width, bmp.height))
    val scaled = if (scale < 1f) android.graphics.Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
    out.outputStream().use { scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
    return out
}

internal fun MainActivity.showSongReview(device: UsbDevice?, staged: List<StagedSong>) {
    if (isFinishing) return
    val density = resources.displayMetrics.density
    val pad = (12 * density).toInt()
    val thumb = (52 * density).toInt()
    val ready = staged.count { it.candidate.status.ready }
    val dupes = staged.count { it.candidate.status == SongImport.Status.DUPLICATE }
    val bad = staged.size - ready - dupes

    val adapter = object : android.widget.BaseAdapter() {
        override fun getCount() = staged.size
        override fun getItem(p: Int) = staged[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, convert: View?, parent: android.view.ViewGroup?): View {
            val st = staged[p]
            val c = st.candidate
            val row = LinearLayout(this@showSongReview).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(pad, pad / 2, pad, pad / 2)
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            row.addView(android.widget.ImageView(this@showSongReview).apply {
                scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                val bmp = st.coverJpeg?.let { android.graphics.BitmapFactory.decodeFile(it.path) }
                if (bmp != null) setImageBitmap(bmp) else {
                    setImageResource(android.R.drawable.ic_media_play)
                    alpha = 0.4f
                }
            }, LinearLayout.LayoutParams(thumb, thumb).apply { marginEnd = pad })
            val texts = LinearLayout(this@showSongReview).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(TextView(this@showSongReview).apply {
                text = c.title
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            texts.addView(TextView(this@showSongReview).apply {
                text = "${c.artist} · ${c.album}"
                textSize = 13f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            val i = c.info
            val detail = listOfNotNull(
                i?.durationMs?.takeIf { it > 0 }?.let(SongImport::duration),
                i?.format?.kind?.removeSuffix(" audio file") ?: c.ext.uppercase(),
                i?.bitrateKbps?.takeIf { it > 0 }?.let { "$it kbps" },
                humanSize(c.size),
            ).joinToString(" · ")
            texts.addView(TextView(this@showSongReview).apply { text = detail; textSize = 12f; alpha = 0.75f })
            texts.addView(TextView(this@showSongReview).apply {
                text = (if (c.status.ready && c.status != SongImport.Status.DRM) "✓ " else "⚠ ") + c.status.label
                textSize = 12f
                setTextColor(when {
                    c.status == SongImport.Status.READY -> 0xFF2E7D32.toInt()
                    c.status.ready -> 0xFFB26A00.toInt()
                    else -> 0xFFC62828.toInt()
                })
            })
            row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
            return row
        }
    }
    val summary = TextView(this).apply {
        setPadding(pad * 2, pad, pad * 2, 0)
        textSize = 13f
        text = "$ready ready" + (if (dupes > 0) ", $dupes already on the device" else "") +
            (if (bad > 0) ", $bad can't be added" else "") +
            if (device == null) "\n(No device connected, so not checked against its library.)" else ""
    }
    val body = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(summary)
        addView(android.widget.ListView(this@showSongReview).apply { this.adapter = adapter },
            LinearLayout.LayoutParams(-1, (resources.displayMetrics.heightPixels * 0.55).toInt()))
    }
    for (st in staged) st.devicePath?.let { log("  ${st.candidate.title} → $it") }
    stagedSongs = staged
    MaterialAlertDialogBuilder(this)
        .setTitle("Add songs")
        .setView(body)
        .setPositiveButton("Add to device") { _, _ ->
            MaterialAlertDialogBuilder(this)
                .setTitle("Almost there")
                .setMessage("Picking, reading and checking songs is done. The last step, writing them into the Music " +
                    "app's library, comes next, once your library sample confirms how iOS 6 stores songs.")
                .setPositiveButton("OK", null)
                .show()
        }
        .setNegativeButton("Close", null)
        .show()
}
