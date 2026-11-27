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

// Adding songs, podcasts, audiobooks, videos and ringtones, part 1: pick files on the phone, read and check them,
// and plan where they'd go.
// Writing them into the device's library comes once MediaLibrary.sqlitedb's layout is confirmed.
// These are MainActivity extension functions: they run on the activity and use its state and helpers.

/** A song staged in the app cache, with what we learned about it. */
internal class StagedSong(val file: java.io.File, val candidate: SongImport.Candidate, val coverJpeg: java.io.File?) {
    var devicePath: String? = null
}

internal fun MainActivity.isDeviceConnected() =
    usbManager.deviceList.values.any { AppleUsbTransport.isApple(it) && AppleUsbTransport.findMuxInterface(it) != null }

/** Music, Synced Videos and Other Audio work without a device for adding (to try picking and checking files). */
internal fun MainActivity.startMedia(view: MediaView) {
    if (busy) { log("Busy, please wait for the current operation to finish."); return }
    if (isDeviceConnected()) withDevice { showMediaMenu(it, view) } else showMediaMenu(null, view)
}

internal fun MainActivity.launchSongPicker(device: UsbDevice?, kind: SongImport.AddKind = SongImport.AddKind.MUSIC) {
    addSongsDevice = device
    addSongsPending = true
    addKind = kind
    // .m4r and some .m4a/.m4b files are labeled "application/octet-stream" by Android's file pickers.
    pickSongs.launch(if (kind.isVideo) arrayOf("video/*", "application/octet-stream") else arrayOf("audio/*", "application/octet-stream"))
}

/** Copies the picked files into the app cache, reads their tags and cover, then checks them against the device. */
internal fun MainActivity.prepareSongs(device: UsbDevice?, uris: List<android.net.Uri>, kind: SongImport.AddKind = SongImport.AddKind.MUSIC) {
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
                val c = SongImport.Candidate(name, ext, if (size > 0) size else file.length(), info, SongImport.classify(ext, info, kind), kind)
                // A file tagged as a different kind (e.g. an audiobook added as a podcast) is still added as chosen.
                info?.stik?.let { s -> if (s != kind.stik && s in setOf(1, 2, 6, 9, 10, 14, 21)) c.note = "tagged as ${stikName(s)}; adding as ${kind.plural.lowercase().removeSuffix("s")}" }
                val cover = info?.cover?.let { runCatching { coverJpeg(it, java.io.File(dir, "$i.jpg")) }.getOrNull() }
                staged += StagedSong(file, c, cover)
            }
        } finally {
            busy = false
        }
        if (staged.isEmpty()) { log("Nothing to add."); return@thread }
        if (device == null) {
            log("No device connected: checked the files only (not whether they're already on the device" +
                (if (kind.isVideo) " or can play them" else "") + ").")
            runOnUiThread { showSongReview(null, staged, kind) }
            return@thread
        }
        runOnDevice(device, after = { runOnUiThread { showSongReview(device, staged, kind) } }) { mux, lockdown ->
            if (kind.isVideo) {
                val model = lockdown.value("ProductType", null) as? String
                val limit = SongImport.videoLimit(model)
                if (limit != null) for (st in staged) {
                    val v = st.candidate.info?.video ?: continue
                    if (!st.candidate.status.ready) continue
                    SongImport.videoProblem(v, limit)?.let { st.candidate.status = SongImport.Status.WONT_PLAY; st.candidate.note = it }
                }
            }
            withService(mux, lockdown, AfcClient.SERVICE) { s ->
                val afc = AfcClient(s)
                if (kind == SongImport.AddKind.RINGTONE) {
                    val tones = runCatching { afc.readDir(SongImport.RINGTONES_DIR) }.getOrDefault(emptyList()).toHashSet()
                    val plist = runCatching { Plist.parse(afc.readHead(MusicLibrary.RINGTONES_PLIST, 4 shl 20)) }.getOrNull()
                    val existing = MusicLibrary.ringtones(plist, tones.map { it to 0L })
                    // Ringtones have names only, so match on those.
                    val names = existing.map { SongImport.matchKey(it.title, "") }.toHashSet()
                    for (st in staged) if (st.candidate.status.ready && !names.add(SongImport.matchKey(st.candidate.title, "")))
                        st.candidate.status = SongImport.Status.DUPLICATE
                    for (st in staged) if (st.candidate.status.ready) st.devicePath = SongImport.planRingtonePath(tones)
                    log("Checked against ${existing.size} ringtones on the device.")
                    return@withService
                }
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

/** iTunes' stik values, as words. */
internal fun stikName(s: Int) = when (s) { 1 -> "music"; 2 -> "an audiobook"; 6 -> "a music video"; 9 -> "a movie"; 10 -> "a TV show"; 14 -> "a ringtone"; 21 -> "a podcast"; else -> "kind $s" }

internal fun MainActivity.showSongReview(device: UsbDevice?, staged: List<StagedSong>, kind: SongImport.AddKind = SongImport.AddKind.MUSIC) {
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
            val v = i?.video
            val detail = listOfNotNull(
                i?.takeIf { it.season > 0 || it.episode > 0 }?.let { "S${it.season} E${it.episode}" },
                i?.durationMs?.takeIf { it > 0 }?.let(SongImport::duration),
                v?.let { "${it.width}×${it.height} " + (if (it.codec.startsWith("avc")) "H.264 ${it.profileName} ${if (it.level > 0) "%.1f".format(it.level / 10.0) else ""}".trim() else it.codec) }
                    ?: (i?.format?.kind?.removeSuffix(" audio file") ?: c.ext.uppercase()),
                i?.bitrateKbps?.takeIf { it > 0 && v == null }?.let { "$it kbps" },
                humanSize(c.size),
            ).joinToString(" · ")
            texts.addView(TextView(this@showSongReview).apply { text = detail; textSize = 12f; alpha = 0.75f })
            texts.addView(TextView(this@showSongReview).apply {
                text = (if (c.status.ready && c.status != SongImport.Status.DRM) "✓ " else "⚠ ") + c.status.label +
                    (c.note?.let { "\n$it" } ?: "")
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
            if (device == null) "\n(No device connected, so not checked against what's on it" + (if (kind.isVideo) " or what it can play" else "") + ".)" else ""
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
        .setTitle("Add ${kind.plural.lowercase()}")
        .setView(body)
        .setPositiveButton("Add to device") { _, _ ->
            val toAdd = staged.filter { it.candidate.status == SongImport.Status.READY && it.candidate.info != null }
            if (device != null && kind == SongImport.AddKind.MUSIC && toAdd.isNotEmpty()) {
                confirmAddSongs(device, toAdd)
                return@setPositiveButton
            }
            MaterialAlertDialogBuilder(this)
                .setTitle("Almost there")
                .setMessage(if (kind == SongImport.AddKind.RINGTONE)
                    "Picking and checking ringtones is done. The last step, adding them to the device's ringtone list, comes " +
                        "next, once the Ringtones.plist from your library sample confirms its format."
                    else "Picking, reading and checking is done. The last step, writing them into the device's library, comes " +
                        "next, once your library sample confirms how iOS 6 stores ${kind.plural.lowercase()}.")
                .setPositiveButton("OK", null)
                .show()
        }
        .setNegativeButton("Close", null)
        .show()
}


// ------------------------------------------------------------------ writing songs into the library

/** The library rows for a staged song, or null if something essential is unknown (e.g. no duration). */
internal fun songRow(st: StagedSong, devicePath: String): us.dannyhand.ipabackup.music.LibraryWriter.Song? {
    val i = st.candidate.info ?: return null
    val format = when (i.format) {
        AudioInfo.Format.MP3 -> us.dannyhand.ipabackup.music.LibraryWriter.Format.MP3
        AudioInfo.Format.AAC -> us.dannyhand.ipabackup.music.LibraryWriter.Format.AAC
        AudioInfo.Format.ALAC -> us.dannyhand.ipabackup.music.LibraryWriter.Format.ALAC
        else -> return null
    }
    if (i.durationMs <= 0) return null
    val folder = devicePath.substringBeforeLast('/').substringAfterLast('/')
    return us.dannyhand.ipabackup.music.LibraryWriter.Song(
        // Missing tags are stored empty (the Music app shows "Unknown Artist" / "Unknown Album"), titles fall back to the file name.
        title = st.candidate.title,
        artist = i.artist ?: i.albumArtist ?: "",
        albumArtist = i.albumArtist,
        album = i.album ?: "",
        genre = i.genre, composer = i.composer, year = i.year,
        track = i.track, trackCount = i.trackCount, disc = i.disc, discCount = i.discCount,
        durationMs = i.durationMs, sampleRate = if (i.sampleRate > 0) i.sampleRate else 44100, bitRateKbps = i.bitrateKbps,
        fileSize = st.file.length(), folder = folder, fileName = devicePath.substringAfterLast('/'), format = format,
    )
}

private fun MainActivity.confirmAddSongs(device: UsbDevice, songs: List<StagedSong>) {
    val pad = (20 * resources.displayMetrics.density).toInt()
    val backup = MaterialCheckBox(this).apply {
        text = "Back up the music library first (recommended)"
        isChecked = true
    }
    val restart = MaterialCheckBox(this).apply { text = "Restart the device afterwards (so Music sees them)"; isChecked = true }
    val body = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(pad, pad / 2, pad, 0)
        addView(TextView(this@confirmAddSongs).apply {
            textSize = 14f
            text = "Adds ${songs.size} song${if (songs.size == 1) "" else "s"} (${humanSize(songs.sumOf { it.file.length() })}) to the device's Music library: " +
                "copies the files into iTunes_Control/Music and writes them into MediaLibrary.sqlitedb the way iTunes does.\n\n" +
                "This is new and only tested on a copy of a real library so far. Start with one or two songs. If anything goes wrong, " +
                "Tools › Music library › Restore puts the library back from the backup.\n\nClose the Music app on the device first."
        })
        addView(backup)
        addView(restart)
    }
    MaterialAlertDialogBuilder(this)
        .setTitle("Add to the Music library?")
        .setView(android.widget.ScrollView(this).apply { addView(body) })
        .setPositiveButton("Add") { _, _ -> runAddSongs(device, songs, backup.isChecked, restart.isChecked) }
        .setNegativeButton("Cancel", null)
        .show()
}

private fun MainActivity.runAddSongs(device: UsbDevice, songs: List<StagedSong>, backupFirst: Boolean, restart: Boolean) {
    log("\nAdding ${songs.size} song(s) to the music library…")
    runOnDevice(device) { mux, lockdown ->
        var addedCount = 0
        withService(mux, lockdown, AfcClient.SERVICE) { s ->
            val afc = AfcClient(s)
            if (backupFirst) {
                log("Backing up the music library database first…")
                zipMusicLibrary(afc, lockdown.deviceName, artwork = false, label = " (before adding songs)")
            }
            val dbPath = "$LIB_DIR/MediaLibrary.sqlitedb"
            val size = runCatching { afc.stat(dbPath).size }.getOrDefault(0L)
            log("Copying the library database (${humanSize(size)})…")
            val dbFile = copySqlite(afc, dbPath, "addsongs") ?: throw IOException("Can't read MediaLibrary.sqlitedb")

            // Where each file goes, checked against what's on the device right now.
            val folders = HashMap<String, MutableSet<String>>()
            for (f in runCatching { afc.readDir(SongImport.MUSIC_DIR) }.getOrDefault(emptyList()).filter(SongImport::isMusicFolder))
                folders[f] = runCatching { afc.readDir("${SongImport.MUSIC_DIR}/$f") }.getOrDefault(emptyList()).toHashSet()
            val plan = songs.mapNotNull { st ->
                val ext = st.candidate.ext.let { if (it == "aif") "aiff" else it }
                val path = SongImport.planPath(ext, folders)
                val row = songRow(st, path)
                if (row == null) { log("  skipped ${st.candidate.title}: no duration or unsupported format"); null } else Triple(st, path, row)
            }
            if (plan.isEmpty()) { log("Nothing to add."); return@withService }

            // Write the rows into our copy, all or nothing.
            log("Writing ${plan.size} song(s) into the library…")
            val flags = android.database.sqlite.SQLiteDatabase.OPEN_READWRITE or android.database.sqlite.SQLiteDatabase.NO_LOCALIZED_COLLATORS or
                android.database.sqlite.SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING
            android.database.sqlite.SQLiteDatabase.openDatabase(dbFile.path, null, flags).use { sdb ->
                sdb.beginTransaction()
                try {
                    val w = us.dannyhand.ipabackup.music.LibraryWriter(us.dannyhand.ipabackup.music.AndroidSqlDb(sdb))
                    for ((st, path, row) in plan) {
                        w.addSong(row)
                        log("  ${row.artist.ifEmpty { "Unknown Artist" }} - ${row.title} → ${path.removePrefix("/iTunes_Control/Music/")}")
                    }
                    if (w.created.isNotEmpty()) log("  new: " + w.created.joinToString("; "))
                    sdb.setTransactionSuccessful()
                } finally {
                    sdb.endTransaction()
                }
                sdb.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
                val check = sdb.rawQuery("PRAGMA integrity_check", null).use { c -> if (c.moveToFirst()) c.getString(0) else "?" }
                if (check != "ok") throw IOException("The edited library failed SQLite's integrity check ($check); nothing was changed on the device")
            }
            // The edit is fully in the main file now; side files from the copy aren't needed.
            java.io.File(dbFile.path + "-wal").delete(); java.io.File(dbFile.path + "-shm").delete()

            // Songs first, then the database, each written under a temporary name and swapped in only when complete.
            log("Copying the song files…")
            AfcSafeRestore.replaceFiles(afc, plan.map { (st, path, _) -> path to { st.file.inputStream() } }) { log("  ${it.substringAfterLast('/')} ✓") }
            log("Swapping in the updated library (the old one is kept on the device as MediaLibrary.sqlitedb.backup)…")
            AfcSafeRestore.replaceFiles(afc, listOf(dbPath to { dbFile.inputStream() }), retire = listOf("$dbPath-wal", "$dbPath-shm"))
            dbFile.parentFile?.deleteRecursively()
            addedCount = plan.size
        }
        if (addedCount == 0) return@runOnDevice
        log("Added $addedCount song(s) ✓")
        if (restart) {
            runCatching { withService(mux, lockdown, us.dannyhand.ipabackup.diag.DiagnosticsRelay.SERVICE) { us.dannyhand.ipabackup.diag.DiagnosticsRelay(it).restart() } }
                .onSuccess { log("Restarting the device. Open Music once it's back and look for the new songs.") }
                .onFailure { log("Couldn't restart (${it.message}); restart the device before opening Music.") }
        } else log("Restart the device before opening Music.")
    }
}
