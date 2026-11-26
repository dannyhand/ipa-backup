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

// Saving books from iBooks and adding books to it.
// These are MainActivity extension functions: they run on the activity and use its state and helpers.

// ---------------------------------------------------------------- books

internal fun MainActivity.browseBooks(device: UsbDevice) = runPicker(
    device = device,
    what = "books",
    dialogTitle = "Books",
    subfolder = BOOKS_SUBFOLDER,
    load = { afc ->
        val metas = BooksLibrary.PLISTS.flatMap { p ->
            runCatching { BooksLibrary.parsePlist(Plist.parse(afc.readHead(p, 16 shl 20)), p.substringBeforeLast('/')) }
                .getOrDefault(emptyList())
        }.associateBy { it.relPath.lowercase() }
        val books = ArrayList<BooksLibrary.Book>()
        for (dir in listOf(BooksLibrary.DIR, "${BooksLibrary.DIR}/Purchases")) {
            val names = runCatching { afc.readDir(dir) }.getOrNull() ?: continue
            for (n in names) {
                if (n.substringAfterLast('.', "").lowercase() !in BooksLibrary.BOOK_EXT) continue
                val path = "$dir/$n"
                val e = runCatching { afc.stat(path) }.getOrNull() ?: continue
                val meta = metas[path.lowercase()]
                val drm = e.isDir && runCatching { afc.stat("$path/META-INF/sinf.xml") }.isSuccess
                books += BooksLibrary.Book(path, meta?.title ?: n.substringBeforeLast('.'), meta?.author, e.isDir,
                    if (e.isDir) 0 else e.size, drm)
            }
        }
        books.sortedWith(compareBy({ (it.author ?: "~").lowercase() }, { it.title.lowercase() }))
    },
    pick = { b ->
        val kind = b.extension.uppercase() + if (b.isFolder) "" else " · ${humanSize(b.size)}"
        Pick(b.devicePath, b.title + if (b.drm) "  🔒" else "", listOfNotNull(b.author, kind).joinToString(" · "), b.size)
    },
    saveOne = { afc, saver, b, used ->
        val name = MusicLibrary.uniqueName(b.saveName(), used)
        val target = saver.create(name, BOOKS_SUBFOLDER)
        try {
            if (b.isFolder) {
                // iTunes stores EPUBs unzipped; rebuild a valid .epub (stored "mimetype" first).
                val zos = AfcZipper.open(target.stream)
                val z = AfcZipper(afc, zos)
                if (runCatching { afc.stat("${b.devicePath}/mimetype") }.isSuccess) z.addStored("${b.devicePath}/mimetype", "mimetype")
                else z.addStoredBytes("application/epub+zip".toByteArray(), "mimetype")
                z.addTree(b.devicePath, "") {
                    it != "mimetype" && it != "iTunesMetadata.plist" && it != "iTunesArtwork" && it != "iTunesMetadata-original.plist"
                }
                zos.finish()
            } else {
                afc.copyFile(b.devicePath, target.stream)
            }
            target.commit()
            target.location
        } catch (e: Exception) {
            target.discard()
            throw e
        }
    },
    confirmNote = { chosen ->
        val folders = chosen.count { it.isFolder }
        val drm = chosen.count { it.drm }
        listOfNotNull(
            if (folders > 0) "$folders EPUB${if (folders == 1) " is" else "s are"} stored unzipped on the iPod and will be repackaged as .epub (size not included above)." else null,
            if (drm > 0) "🔒 $drm ${if (drm == 1) "is an iBookstore purchase" else "are iBookstore purchases"} with DRM; they'll only open in Apple Books." else null,
        ).joinToString("\n\n").ifEmpty { null }
    },
)

// ---------------------------------------------------------------- add books

/** Books works without a device too (adding over Wi-Fi). */
internal fun MainActivity.startBooks() {
    if (busy) { log("Busy, please wait for the current operation to finish."); return }
    val connected = usbManager.deviceList.values.any { AppleUsbTransport.isApple(it) && AppleUsbTransport.findMuxInterface(it) != null }
    if (connected) withDevice { showBooksMenu(it) } else showBooksMenu(null)
}

internal fun MainActivity.showBooksMenu(device: UsbDevice?) {
    val actions = if (device != null) arrayOf("Save books from the device", "Add books to the device…")
        else arrayOf("Add books to the device (over Wi-Fi)…")
    MaterialAlertDialogBuilder(this)
        .setTitle("Books")
        .setItems(actions) { _, which ->
            if (device != null && which == 0) browseBooks(device)
            else { pendingBooksDevice = device; pickBooks.launch(arrayOf("application/epub+zip", "application/pdf", "application/octet-stream", "*/*")) }
        }
        .setNegativeButton("Cancel", null)
        .show()
}

internal fun MainActivity.prepareBooks(device: UsbDevice?, uris: List<android.net.Uri>) {
    val books = ArrayList<NewBook>()
    for (uri in uris) {
        val (name, _) = docInfo(uri)
        val bytes = runCatching { contentResolver.openInputStream(uri)!!.use { it.readBytes() } }.getOrElse {
            log("Can't read $name: ${it.message}"); null
        } ?: continue
        val info = BookImport.inspect(bytes, name)
        if (!info.isPdf && !BookImport.isEpubZip(bytes)) { log("Skipped $name: not an EPUB or PDF."); continue }
        books += NewBook(name, bytes, info)
    }
    if (books.isEmpty()) return
    val list = books.joinToString("\n") { "• " + it.info.title + (it.info.author?.let { a -> " — $a" } ?: "") + (if (it.info.isPdf) " (PDF)" else "") }
    val b = MaterialAlertDialogBuilder(this)
        .setTitle("Add ${books.size} book${if (books.size == 1) "" else "s"}")
        .setMessage("$list\n\nOver USB: copied into iBooks' library the way iTunes syncs. Close iBooks fully and reopen it to see them." +
            "\n\nOver Wi-Fi: open the link in Safari on the device and tap \"Open in iBooks\".")
        .setNeutralButton("Over Wi-Fi") { _, _ ->
            shareOverWifi("Books", books.map { nb ->
                val ext = if (nb.info.isPdf) "pdf" else "epub"
                FileServer.File(IpaSaver.clean(nb.info.title) + ".$ext", if (nb.info.isPdf) "application/pdf" else "application/epub+zip",
                    nb.bytes, nb.info.title)
            }, "Tap a book, then \"Open in iBooks\".")
        }
        .setNegativeButton("Cancel", null)
    if (device != null) b.setPositiveButton("Over USB") { _, _ -> addBooksOverUsb(device, books) }
    b.show()
}

internal fun MainActivity.addBooksOverUsb(device: UsbDevice, books: List<NewBook>) {
    log("\nAdding ${books.size} book(s) to iBooks…")
    runOnDevice(device) { mux, lockdown ->
        var added = 0
        withService(mux, lockdown, AfcClient.SERVICE) { s ->
            val afc = AfcClient(s)
            afc.makeDir("/Books")
            val original = runCatching { java.io.ByteArrayOutputStream().also { afc.copyFile(BookImport.PLIST, it) }.toByteArray() }.getOrNull()
            var plist: Any? = original?.let { runCatching { Plist.parse(it) }.getOrNull() }
            // Keep the original list once, in case iBooks dislikes the edit.
            if (original != null && runCatching { afc.stat(BookImport.BACKUP) }.isFailure)
                afc.writeFile(BookImport.BACKUP, original.inputStream(), original.size.toLong())
            for ((i, nb) in books.withIndex()) {
                val label = "[${i + 1}/${books.size}] ${nb.info.title}"
                if (BookImport.alreadyThere(plist, nb.info)) { log("$label: already on the device, skipped"); continue }
                val pid = BookImport.newPersistentId()
                val path = "/Books/$pid." + if (nb.info.isPdf) "pdf" else "epub"
                try {
                    if (nb.info.isPdf) {
                        afc.writeFile(path, nb.bytes.inputStream(), nb.bytes.size.toLong())
                    } else {
                        val n = afc.unzipInto(nb.bytes.inputStream(), path, allowedTop = null)
                        if (n == 0) throw IOException("the EPUB has no files")
                    }
                    val updated = BookImport.withBook(plist, nb.info, pid, nb.bytes)
                    val xml = Plist.toXml(updated).toByteArray()
                    afc.writeFile(BookImport.PLIST, xml.inputStream(), xml.size.toLong())
                    plist = updated
                    added++
                    log("$label ✓")
                } catch (e: AfcException) {
                    log("$label FAILED: ${e.message}")
                    runCatching { afc.removeTree(path) }
                } catch (e: IOException) {
                    if (e.message == "the EPUB has no files") { log("$label FAILED: ${e.message}"); runCatching { afc.removeTree(path) } } else throw e
                }
            }
        }
        if (added > 0) {
            runCatching { withService(mux, lockdown, NotificationProxy.SERVICE) { NotificationProxy(it).post(NotificationProxy.SYNC_DID_FINISH) } }
            log("Done: $added added. Close iBooks fully (double-press Home, hold its icon, tap ⊖) and reopen it.")
        } else log("Nothing added.")
    }
}
