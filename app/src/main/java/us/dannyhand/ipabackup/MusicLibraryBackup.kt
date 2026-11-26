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

// Backing up and safely restoring the music library database.
// These are MainActivity extension functions: they run on the activity and use its state and helpers.

// ---------------------------------------------------------------- music library database backup

/**
 * Backs up the music library database (not the songs) before anything edits it. Which files exist
 * depends on the iOS version, all inside /iTunes_Control/iTunes:
 *  - iPhone OS 1–2: iTunesDB / iTunesCDB (binary; CDB is zlib-compressed)
 *  - iPhone OS 3–4: iTunes Library.itlp (a folder of SQLite files)
 *  - iOS 5 and later: MediaLibrary.sqlitedb, with its -wal and -shm files (SQLite's write-ahead log:
 *    recent changes may only be in the -wal file, so the three must be copied and restored together)
 * The whole folder is zipped, so whichever format is there (plus play counts and iTunes' prefs) is kept.
 * Artwork (/iTunes_Control/Artwork, the cover thumbnails) is optional because it can be large.
 */
internal fun MainActivity.startMusicLibraryBackup(device: UsbDevice) {
    log("\nLooking at the music library database…")
    var found = emptyList<String>()
    var libSize = 0L
    var artSize = 0L
    var ok = false
    runOnDevice(device, after = { if (ok) runOnUiThread { showMusicLibraryDialog(device, found, libSize, artSize) } }) { mux, lockdown ->
        withService(mux, lockdown, AfcClient.SERVICE) { s ->
            val afc = AfcClient(s)
            val names = runCatching { afc.readDir(LIB_DIR) }.getOrDefault(emptyList()).toSet()
            found = buildList {
                if ("MediaLibrary.sqlitedb" in names) add("MediaLibrary.sqlitedb (iOS 5 and later)")
                if ("iTunes Library.itlp" in names) add("iTunes Library.itlp (iPhone OS 3–4)")
                if ("iTunesCDB" in names) add("iTunesCDB (iPhone OS 1–2)")
                if ("iTunesDB" in names) add("iTunesDB (iPhone OS 1–2)")
            }
            libSize = runCatching { afc.measureTree(LIB_DIR).bytes }.getOrDefault(0L)
            artSize = runCatching { afc.measureTree(ART_DIR).bytes }.getOrDefault(0L)
            ok = true
        }
    }
}

internal fun MainActivity.showMusicLibraryDialog(device: UsbDevice, found: List<String>, libSize: Long, artSize: Long) {
    val pad = (20 * resources.displayMetrics.density).toInt()
    val art = MaterialCheckBox(this).apply {
        text = "Include artwork (${humanSize(artSize)})"
        isChecked = prefs.getBoolean("musiclib_artwork", true)
        isEnabled = artSize > 0
    }
    val body = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(pad, pad / 2, pad, 0)
        addView(TextView(this@showMusicLibraryDialog).apply {
            text = (if (found.isEmpty()) "No music library database found." else "Found:\n" + found.joinToString("\n") { "• $it" }) +
                "\n\nLibrary folder: ${humanSize(libSize)}. Only the database is saved, not the songs themselves." +
                "\n\nSaved to Download/IPA Backup/$MUSICLIB_SUBFOLDER."
            textSize = 14f
        })
        addView(art)
        if (found.any { it.startsWith("MediaLibrary") }) addView(com.google.android.material.button.MaterialButton(
            this@showMusicLibraryDialog, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = "Export a small sample (for development)"
            setOnClickListener { exportLibrarySample(device) }
        })
    }
    MaterialAlertDialogBuilder(this)
        .setTitle("Music library")
        .setView(body)
        .setPositiveButton("Back up") { _, _ ->
            prefs.edit().putBoolean("musiclib_artwork", art.isChecked).apply()
            log("\nBacking up the music library database…")
            runOnDevice(device) { mux, lockdown ->
                withService(mux, lockdown, AfcClient.SERVICE) { s -> zipMusicLibrary(AfcClient(s), lockdown.deviceName, art.isChecked, "") }
            }
        }
        .setNeutralButton("Restore…") { _, _ -> pendingLibDevice = device; pickLibZip.launch(arrayOf("application/zip", "*/*")) }
        .setNegativeButton("Cancel", null)
        .show()
}

/**
 * Saves a compact SQL sample of MediaLibrary.sqlitedb (schema, small tables, ~25 songs' rows) for
 * working out how to add songs; the full database is too big to pass around.
 */
internal fun MainActivity.exportLibrarySample(device: UsbDevice) {
    log("\nExporting a music library sample…")
    runOnDevice(device) { mux, lockdown ->
        withService(mux, lockdown, AfcClient.SERVICE) { s ->
            val file = copySqlite(AfcClient(s), "$LIB_DIR/MediaLibrary.sqlitedb", "libsample") ?: throw IOException("Can't read MediaLibrary.sqlitedb")
            val t = IpaSaver(this).create(IpaSaver.clean("Library sample (${lockdown.deviceName}) ${today()}") + ".sql.gz", MUSICLIB_SUBFOLDER)
            try {
                val gz = java.util.zip.GZIPOutputStream(t.stream)
                val w = gz.bufferedWriter()
                withSqlite(file) { LibrarySample.write(it, w) }
                w.flush()
                gz.finish()
                t.commit()
            } catch (e: Exception) {
                t.discard()
                throw e
            } finally {
                file.parentFile?.deleteRecursively()
            }
            log("Saved ${t.location} ✓ Send me this file.")
        }
    }
}

/** Zips the library folder (and artwork) to the phone. Returns where it was saved. */
internal fun MainActivity.zipMusicLibrary(afc: AfcClient, owner: String, artwork: Boolean, label: String): String {
    val stamp = java.text.SimpleDateFormat("yyyy-MM-dd HH.mm.ss", java.util.Locale.US).format(java.util.Date())
    val t = IpaSaver(this).create(IpaSaver.clean("Music Library$label ($owner) $stamp") + ".zip", MUSICLIB_SUBFOLDER)
    try {
        val zos = java.util.zip.ZipOutputStream(java.io.BufferedOutputStream(t.stream, 1 shl 16))
        val z = AfcZipper(afc, zos)
        z.addStoredBytes(("Music library database backup made by IPA Backup on $stamp from $owner.\n" +
            "iTunes_Control/iTunes holds the library database; iTunes_Control/Artwork the cover art.\n" +
            "The songs themselves (iTunes_Control/Music) are not included.\n" +
            "Restore it with Tools › Music library › Restore.\n").toByteArray(), "README.txt")
        z.addTree(LIB_DIR, LIB_DIR.trimStart('/') + "/")
        if (artwork && runCatching { afc.stat(ART_DIR) }.isSuccess) z.addTree(ART_DIR, ART_DIR.trimStart('/') + "/")
        zos.finish()
        zos.flush()
        t.commit()
        log("Saved ${t.location} (${z.files} files, ${humanSize(z.bytes)}) ✓")
        return t.location
    } catch (e: Exception) {
        t.discard()
        throw e
    }
}

internal fun MainActivity.confirmMusicLibraryRestore(device: UsbDevice, uri: android.net.Uri) {
    val names = runCatching {
        contentResolver.openInputStream(uri)!!.use { java.util.zip.ZipInputStream(it).use { z ->
            generateSequence { z.nextEntry }.filter { !it.isDirectory }.map { it.name }.toList()
        } }
    }.getOrDefault(emptyList())
    val libFiles = names.filter { it.startsWith(LIB_DIR.trimStart('/') + "/") }
    if (libFiles.isEmpty()) {
        log("That zip isn't a music library backup (no iTunes_Control/iTunes inside).")
        return
    }
    val restart = MaterialCheckBox(this).apply { text = "Restart the device afterwards (recommended)"; isChecked = true }
    val pad = (20 * resources.displayMetrics.density).toInt()
    val body = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(pad, pad / 2, pad, 0)
        addView(TextView(this@confirmMusicLibraryRestore).apply {
            textSize = 14f
            text = "Puts back the library database from this backup (${libFiles.size} files" +
                (if (names.any { it.startsWith(ART_DIR.trimStart('/') + "/") }) ", with artwork" else "") + ").\n\n" +
                "Songs added since the backup will be missing from the Music app (their files stay on the device), " +
                "and play counts go back to the backup's.\n\nThe current library is backed up first, so this can be undone. " +
                "Close the Music app before restoring."
        })
        addView(restart)
    }
    MaterialAlertDialogBuilder(this)
        .setTitle("Restore music library?")
        .setView(body)
        .setPositiveButton("Restore") { _, _ -> runMusicLibraryRestore(device, uri, names, restart.isChecked) }
        .setNegativeButton("Cancel", null)
        .show()
}

internal fun MainActivity.runMusicLibraryRestore(device: UsbDevice, uri: android.net.Uri, zipNames: List<String>, restart: Boolean) {
    log("\nRestoring the music library database…")
    runOnDevice(device) { mux, lockdown ->
        withService(mux, lockdown, AfcClient.SERVICE) { s ->
            val afc = AfcClient(s)
            log("Backing up the current library first…")
            zipMusicLibrary(afc, lockdown.deviceName, artwork = zipNames.any { it.startsWith(ART_DIR.trimStart('/') + "/") }, label = " (before restore)")
            // SQLite side files that aren't in the backup belong to the current database: left in place they'd be
            // replayed onto the restored one and corrupt it, so they're moved aside (to .backup) too.
            val inZip = zipNames.map { "/$it" }.toSet()
            val stale = listOf(LIB_DIR, "$LIB_DIR/iTunes Library.itlp").flatMap { dir ->
                runCatching { afc.readDir(dir) }.getOrDefault(emptyList())
                    .filter { it.endsWith("-wal") || it.endsWith("-shm") || it.endsWith("-journal") }
                    .map { "$dir/$it" }.filter { it !in inZip }
            }
            log("Copying the backup to the device under temporary names…")
            val r = contentResolver.openInputStream(uri)!!.use {
                AfcSafeRestore.restore(afc, it, allowedTop = setOf("iTunes_Control"), retire = stale)
            }
            log("Restored ${r.files} files ✓ (${r.replaced} replaced; the previous versions are kept on the device as .backup files)")
        }
        if (restart) {
            runCatching { withService(mux, lockdown, DiagnosticsRelay.SERVICE) { DiagnosticsRelay(it).restart() } }
                .onSuccess { log("Restarting the device so the Music app reloads the library.") }
                .onFailure { log("Couldn't restart (${it.message}); restart the device yourself before opening Music.") }
        } else log("Restart the device before opening the Music app.")
    }
}
