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

// The Camera Roll browser with thumbnails.
// These are MainActivity extension functions: they run on the activity and use its state and helpers.

// ---------------------------------------------------------------- photos & videos

internal fun MainActivity.browsePhotos(device: UsbDevice) {
    log("\nReading the camera roll…")
    val cmds = LinkedBlockingQueue<PhotoCmd>()
    photoSelected.clear()
    thumbDone.clear()
    photoFirstVisible = 0
    runOnDevice(device, after = {
        setStopButtonVisible(false); stopMode = StopMode.NONE; photoAdapter = null; dismissProgress()
    }) { mux, lockdown ->
        withService(mux, lockdown, AfcClient.SERVICE) { stream ->
            val afc = AfcClient(stream)
            val media = CameraRoll.list(afc) { n -> if (n % 200 == 0) log("  $n found…") }
            val photos = media.count { !it.isVideo }
            log("$photos photos and ${media.size - photos} videos (${humanSize(media.sumOf { it.size })}).")
            if (media.isEmpty()) return@withService
            java.io.File(cacheDir, "thumbs").mkdirs()
            cmds.offer(PhotoCmd.Show)
            var sinceNotify = 0
            while (true) {
                // Make thumbnails whenever there's no command waiting, starting where the user is looking.
                val next = nextThumb(media)
                val cmd = if (next != null) cmds.poll() else cmds.poll(15, TimeUnit.MINUTES)
                if (cmd == null) {
                    if (next == null) break // idle too long
                    runCatching { makeThumb(afc, next) }
                    thumbDone += next.path
                    if (++sinceNotify >= 6 || nextThumb(media) == null) {
                        sinceNotify = 0
                        runOnUiThread { photoAdapter?.notifyDataSetChanged() }
                    }
                    continue
                }
                when (cmd) {
                    is PhotoCmd.Close -> break
                    is PhotoCmd.Show -> runOnUiThread { showPhotos(media, cmds) }
                    is PhotoCmd.Preview -> previewMedia(afc, cmd.m, cmds)
                    is PhotoCmd.SavePreview -> { saveMediaFile(cmd.m, cmd.file); cmds.offer(PhotoCmd.Show) }
                    is PhotoCmd.Save -> { saveMedia(afc, cmd.items); cmds.offer(PhotoCmd.Show) }
                }
            }
        }
        log("Photo browser closed.")
    }
}

internal fun MainActivity.thumbFile(m: CameraRoll.Media) =
    java.io.File(java.io.File(cacheDir, "thumbs"), "%08x_%x_%x.jpg".format(m.path.hashCode(), m.size, m.mtimeMs))

internal fun MainActivity.nextThumb(media: List<CameraRoll.Media>): CameraRoll.Media? {
    val start = photoFirstVisible.coerceIn(0, media.size)
    for (i in (start until media.size) + (0 until start)) {
        val m = media[i]
        if (m.path in thumbDone) continue
        if (thumbFile(m).exists()) { thumbDone += m.path; continue }
        return m
    }
    return null
}

/** Worker thread: builds a square JPEG thumbnail in cache/thumbs. Returns false if there's no preview. */
internal fun MainActivity.makeThumb(afc: AfcClient, m: CameraRoll.Media): Boolean {
    val f = thumbFile(m)
    if (f.exists()) return true
    var bmp: android.graphics.Bitmap? = null
    if (!m.isVideo) {
        if (m.extension == "jpg" || m.extension == "jpeg") {
            // Fast path: the EXIF preview in the first few KB.
            val ex = ExifThumb.parse(afc.readHead(m.path, 128 * 1024))
            bmp = ex.thumbnail?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }
                ?.let { rotate(it, ExifThumb.rotationDegrees(ex.orientation)) }
        }
        if (bmp == null && m.size in 1..(20L shl 20)) {
            val all = afc.readHead(m.path, m.size.toInt())
            bmp = decodeSampled(all, THUMB_PX)?.let { rotate(it, exifRotation(all)) }
        }
    } else {
        for (p in CameraRoll.posterCandidates(m)) {
            val bytes = runCatching { afc.readHead(p, 4 shl 20) }.getOrNull() ?: continue
            bmp = decodeSampled(bytes, THUMB_PX)?.let { rotate(it, exifRotation(bytes)) } ?: continue
            break
        }
    }
    val src = bmp ?: return false
    val side = minOf(src.width, src.height)
    val square = android.graphics.Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
    val scaled = android.graphics.Bitmap.createScaledBitmap(square, THUMB_PX, THUMB_PX, true)
    java.io.FileOutputStream(f).use { scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it) }
    return true
}

internal fun MainActivity.thumbBitmap(m: CameraRoll.Media): android.graphics.Bitmap? =
    thumbLru.get(m.path) ?: thumbFile(m).takeIf { it.exists() }
        ?.let { android.graphics.BitmapFactory.decodeFile(it.path) }
        ?.also { thumbLru.put(m.path, it) }

internal fun MainActivity.decodeSampled(bytes: ByteArray, minSide: Int): android.graphics.Bitmap? {
    val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
    if (o.outWidth <= 0) return null
    var sample = 1
    while (minOf(o.outWidth, o.outHeight) / (sample * 2) >= minSide) sample *= 2
    return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
        android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
}

internal fun MainActivity.exifRotation(bytes: ByteArray): Int = runCatching {
    ExifThumb.rotationDegrees(
        android.media.ExifInterface(bytes.inputStream())
            .getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)
    )
}.getOrDefault(0)

internal fun MainActivity.rotate(b: android.graphics.Bitmap, degrees: Int): android.graphics.Bitmap =
    if (degrees == 0) b else android.graphics.Bitmap.createBitmap(
        b, 0, 0, b.width, b.height, android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }, true
    )

/** Thumbnail grid: tap to preview; hold to start selecting, then tap to check/uncheck. */
internal fun MainActivity.showPhotos(media: List<CameraRoll.Media>, cmds: LinkedBlockingQueue<PhotoCmd>) {
    if (isFinishing) { cmds.offer(PhotoCmd.Close); return }
    val density = resources.displayMetrics.density
    val pad = (16 * density).toInt()
    val gap = (3 * density).toInt()
    var selecting = photoSelected.isNotEmpty()
    val photoCount = media.count { !it.isVideo }

    val header = TextView(this).apply {
        textSize = 12f
        alpha = 0.75f
        setPadding(pad, pad / 4, pad, pad / 3)
    }
    val adapter = object : BaseAdapter() {
        override fun getCount() = media.size
        override fun getItem(position: Int) = media[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val cell = convertView as? SquareFrame ?: SquareFrame(this@showPhotos).apply {
                setBackgroundColor(0x22888888)
                addView(android.widget.ImageView(this@showPhotos).apply {
                    scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                }, android.widget.FrameLayout.LayoutParams(-1, -1))
                addView(TextView(this@showPhotos).apply { // badge: video size / placeholder
                    textSize = 11f
                    setTextColor(0xFFFFFFFF.toInt())
                    setShadowLayer(3f, 0f, 0f, 0xFF000000.toInt())
                    setPadding(gap * 2, gap, gap * 2, gap)
                }, android.widget.FrameLayout.LayoutParams(-2, -2, android.view.Gravity.BOTTOM or android.view.Gravity.START))
                addView(TextView(this@showPhotos).apply { // selection check
                    textSize = 18f
                    setPadding(gap * 2, gap, gap * 2, gap)
                }, android.widget.FrameLayout.LayoutParams(-2, -2, android.view.Gravity.TOP or android.view.Gravity.END))
            }
            val m = media[position]
            val image = cell.getChildAt(0) as android.widget.ImageView
            val badge = cell.getChildAt(1) as TextView
            val check = cell.getChildAt(2) as TextView
            val bmp = thumbBitmap(m)
            image.setImageBitmap(bmp)
            val selected = m.path in photoSelected
            image.alpha = if (selected) 0.55f else 1f
            badge.text = when {
                m.isVideo -> "▶ ${humanSize(m.size)}"
                bmp == null && m.path in thumbDone -> m.name
                bmp == null -> "…"
                else -> ""
            }
            check.text = if (selecting) (if (selected) "☑" else "☐") else ""
            return cell
        }
    }
    photoAdapter = adapter
    val grid = android.widget.GridView(this).apply {
        numColumns = 3
        horizontalSpacing = gap
        verticalSpacing = gap
        stretchMode = android.widget.GridView.STRETCH_COLUMN_WIDTH
        this.adapter = adapter
        isFastScrollEnabled = true
        setPadding(pad / 2, 0, pad / 2, 0)
        setOnScrollListener(object : android.widget.AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: android.widget.AbsListView, scrollState: Int) {}
            override fun onScroll(view: android.widget.AbsListView, first: Int, visible: Int, total: Int) {
                photoFirstVisible = first // thumbnails load from here first
            }
        })
    }
    val container = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(header)
        addView(grid, LinearLayout.LayoutParams(-1, (resources.displayMetrics.heightPixels * 0.62).toInt()))
    }
    val dialog = MaterialAlertDialogBuilder(this)
        .setTitle("Camera roll")
        .setView(container)
        .setPositiveButton("Save", null)
        .setNeutralButton("Select all", null)
        .setNegativeButton("Close", null)
        .create()

    var handled = false
    fun go(cmd: PhotoCmd) {
        if (!handled) { handled = true; cmds.offer(cmd) }
        dialog.dismiss()
    }

    fun refresh() {
        adapter.notifyDataSetChanged()
        val chosen = media.filter { it.path in photoSelected }
        header.text = "$photoCount photos · ${media.size - photoCount} videos" +
            if (selecting) " · ${chosen.size} selected (${humanSize(chosen.sumOf { it.size })})" else " · tap to view, hold to select"
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)?.apply {
            visibility = if (selecting) View.VISIBLE else View.GONE
            isEnabled = chosen.isNotEmpty()
            text = "Save ${chosen.size}"
        }
        dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL)?.text = when {
            selecting && chosen.size == media.size -> "Select none"
            else -> "Select all"
        }
    }

    fun toggle(m: CameraRoll.Media) {
        if (!photoSelected.add(m.path)) photoSelected.remove(m.path)
        refresh()
    }

    grid.setOnItemClickListener { _, _, position, _ ->
        val m = media[position]
        if (selecting) toggle(m) else go(PhotoCmd.Preview(m))
    }
    grid.setOnItemLongClickListener { _, _, position, _ ->
        selecting = true
        toggle(media[position])
        true
    }
    dialog.setOnShowListener {
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val chosen = media.filter { it.path in photoSelected }
            if (chosen.isNotEmpty()) {
                handled = true
                dialog.dismiss()
                confirmSaveMedia(chosen, cmds)
            }
        }
        dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
            if (selecting && photoSelected.size == media.size) {
                photoSelected.clear()
                selecting = false
            } else {
                selecting = true
                media.forEach { photoSelected += it.path }
            }
            refresh()
        }
        dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).setOnClickListener { go(PhotoCmd.Close) }
        refresh()
        grid.setSelection(photoFirstVisible.coerceIn(0, maxOf(0, media.size - 1)))
    }
    dialog.setOnKeyListener { _, keyCode, event ->
        if (keyCode != KeyEvent.KEYCODE_BACK || !selecting) return@setOnKeyListener false
        if (event.action == KeyEvent.ACTION_UP) { photoSelected.clear(); selecting = false; refresh() }
        true
    }
    dialog.setOnCancelListener { if (!handled) { handled = true; cmds.offer(PhotoCmd.Close) } }
    dialog.setOnDismissListener { if (photoAdapter === adapter) photoAdapter = null }
    dialog.show()
}

/** Worker thread: copies the full file to cache (cancellable for big videos), then shows the viewer. */
internal fun MainActivity.previewMedia(afc: AfcClient, m: CameraRoll.Media, cmds: LinkedBlockingQueue<PhotoCmd>) {
    val dir = java.io.File(cacheDir, "preview").apply { deleteRecursively(); mkdirs() }
    val f = java.io.File(dir, m.name)
    loadCancelled = false
    val showDialog = m.size > (2L shl 20)
    if (showDialog) showProgress("Loading ${m.name}") { loadCancelled = true }
    afc.cancelCheck = { if (loadCancelled) throw CancelledException() }
    try {
        var last = 0L
        java.io.FileOutputStream(f).use { out ->
            afc.copyFile(m.path, out) { c, t ->
                if (showDialog && (c - last >= (512L shl 10) || c == t)) { last = c; updateProgress("${humanSize(c)} of ${humanSize(t)}") }
            }
        }
        dismissProgress()
        runOnUiThread { showViewer(m, f, cmds) }
    } catch (e: CancelledException) {
        dismissProgress()
        f.delete()
        cmds.offer(PhotoCmd.Show)
    } catch (e: Exception) {
        dismissProgress()
        log("Couldn't load ${m.name}: ${e.message}")
        cmds.offer(PhotoCmd.Show)
    } finally {
        afc.cancelCheck = {}
    }
}

internal fun MainActivity.showViewer(m: CameraRoll.Media, f: java.io.File, cmds: LinkedBlockingQueue<PhotoCmd>) {
    if (isFinishing) { cmds.offer(PhotoCmd.Close); return }
    val h = (resources.displayMetrics.heightPixels * 0.62).toInt()
    val content: View = if (m.isVideo) {
        android.widget.VideoView(this).apply {
            setVideoPath(f.path)
            val controller = android.widget.MediaController(this@showViewer)
            controller.setAnchorView(this)
            setMediaController(controller)
            setOnPreparedListener { start() }
            setOnErrorListener { _, _, _ ->
                Toast.makeText(this@showViewer, "This phone can't play this video, but it can still be saved.", Toast.LENGTH_LONG).show()
                true
            }
        }
    } else {
        android.widget.ImageView(this).apply {
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            setImageBitmap(decodeForViewer(f))
        }
    }
    val frame = android.widget.FrameLayout(this).apply {
        addView(content, android.widget.FrameLayout.LayoutParams(-1, h, android.view.Gravity.CENTER))
    }
    val date = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
        .format(java.util.Date(m.mtimeMs))
    var handled = false
    MaterialAlertDialogBuilder(this)
        .setTitle(m.name)
        .setMessage("$date · ${humanSize(m.size)}")
        .setView(frame)
        .setPositiveButton("Save") { _, _ -> handled = true; cmds.offer(PhotoCmd.SavePreview(m, f)) }
        .setNegativeButton("Back") { _, _ -> handled = true; cmds.offer(PhotoCmd.Show) }
        .setOnDismissListener {
            (content as? android.widget.VideoView)?.stopPlayback()
            if (!handled) { handled = true; cmds.offer(PhotoCmd.Show) }
        }
        .show()
}

/** Full photo scaled to about screen size, rotated per EXIF. */
internal fun MainActivity.decodeForViewer(f: java.io.File): android.graphics.Bitmap? {
    val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeFile(f.path, o)
    if (o.outWidth <= 0) return null
    val target = maxOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
    var sample = 1
    while (maxOf(o.outWidth, o.outHeight) / (sample * 2) >= target) sample *= 2
    val bmp = android.graphics.BitmapFactory.decodeFile(f.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
        ?: return null
    val deg = runCatching {
        ExifThumb.rotationDegrees(android.media.ExifInterface(f.path).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1))
    }.getOrDefault(0)
    return rotate(bmp, deg)
}

internal fun MainActivity.confirmSaveMedia(chosen: List<CameraRoll.Media>, cmds: LinkedBlockingQueue<PhotoCmd>) {
    val total = chosen.sumOf { it.size }
    val photos = chosen.count { !it.isVideo }
    val free = runCatching {
        android.os.StatFs(android.os.Environment.getExternalStorageDirectory().path).availableBytes
    }.getOrDefault(-1L)
    val msg = buildString {
        val parts = listOfNotNull(
            if (photos > 0) "$photos photo${if (photos == 1) "" else "s"}" else null,
            if (chosen.size - photos > 0) "${chosen.size - photos} video${if (chosen.size - photos == 1) "" else "s"}" else null,
        )
        append("Save ${parts.joinToString(" and ")} (${humanSize(total)}) to Download/${IpaSaver.FOLDER}/$PHOTOS_SUBFOLDER/?")
        append("\n\nOriginal files and names are kept; gallery apps will date them when they were taken.")
        if (free >= 0) {
            append("\n\nFree space on this phone: ${humanSize(free)}")
            if (total > free) append("\n⚠️ Not enough space for all of them.")
        }
    }
    var handled = false
    MaterialAlertDialogBuilder(this)
        .setMessage(msg)
        .setPositiveButton("Save") { _, _ -> handled = true; cmds.offer(PhotoCmd.Save(chosen)) }
        .setNegativeButton("Back") { _, _ -> handled = true; cmds.offer(PhotoCmd.Show) }
        .setOnDismissListener { if (!handled) { handled = true; cmds.offer(PhotoCmd.Show) } }
        .show()
}

internal fun MainActivity.saveMediaFile(m: CameraRoll.Media, f: java.io.File) {
    val t = IpaSaver(this).create(m.name, PHOTOS_SUBFOLDER, m.mtimeMs)
    try {
        f.inputStream().use { it.copyTo(t.stream, 1 shl 16) }
        t.commit()
        log("Saved ${t.location}")
    } catch (e: Exception) {
        t.discard()
        log("Couldn't save ${m.name}: ${e.message}")
    }
}

/** Worker thread: saves the selection with Stop support. */
internal fun MainActivity.saveMedia(afc: AfcClient, items: List<CameraRoll.Media>) {
    val saver = IpaSaver(this)
    val used = HashSet<String>()
    var ok = 0
    var failed = 0
    var done = 0
    stopMode = StopMode.NONE
    keepScreenOn(true)
    setStopButtonVisible(true)
    afc.cancelCheck = { checkStopNow() }
    log("\nSaving ${items.size} items to Download/${IpaSaver.FOLDER}/$PHOTOS_SUBFOLDER/")
    try {
        for ((i, m) in items.withIndex()) {
            if (stopMode != StopMode.NONE) break
            val name = MusicLibrary.uniqueName(m.name, used)
            if (items.size <= 20 || m.size > (5L shl 20) || i % 10 == 0 || i == items.size - 1) log("[${i + 1}/${items.size}] $name (${humanSize(m.size)})")
            val target = saver.create(name, PHOTOS_SUBFOLDER, m.mtimeMs)
            try {
                afc.copyFile(m.path, target.stream)
                target.commit()
                ok++
            } catch (e: StopNowException) {
                target.discard()
                log("  stopped; the partial file was discarded")
                break
            } catch (e: Exception) {
                target.discard()
                log("  $name FAILED: ${e.message}")
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
    photoSelected.clear()
}

internal fun MainActivity.showProgress(title: String, onCancel: () -> Unit) = runOnUiThread {
    val pad = (20 * resources.displayMetrics.density).toInt()
    val tv = TextView(this).apply { text = "Starting…"; setPadding(pad, pad / 2, pad, 0) }
    progressText = tv
    progressDialog = MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .setView(tv)
        .setNegativeButton("Cancel") { _, _ -> onCancel() }
        .setCancelable(false)
        .show()
}

internal fun MainActivity.updateProgress(text: String) = runOnUiThread { progressText?.text = text }

internal fun MainActivity.dismissProgress() = runOnUiThread {
    progressDialog?.dismiss()
    progressDialog = null
    progressText = null
}
