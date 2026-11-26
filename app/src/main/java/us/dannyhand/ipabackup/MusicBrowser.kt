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

// The music browser (songs and other media) and playlist export.
// These are MainActivity extension functions: they run on the activity and use its state and helpers.

// ---------------------------------------------------------------- music browser

/** Music, or (otherMedia) podcasts, audiobooks, iTunes U, synced videos and ringtones. */
internal fun MainActivity.browseMusic(device: UsbDevice, otherMedia: Boolean) {
    log(if (otherMedia) "\nLoading podcasts, audiobooks, videos and ringtones…" else "\nLoading the music library…")
    val cmds = LinkedBlockingQueue<MusicCmd>()
    musicSelected.clear()
    musicQuery = ""
    musicOther = otherMedia
    runOnDevice(device, after = { setStopButtonVisible(false); stopMode = StopMode.NONE }) { mux, lockdown ->
        withService(mux, lockdown, AfcClient.SERVICE) { stream ->
            val afc = AfcClient(stream)
            val library = loadMusicLibrary(afc) ?: if (otherMedia) emptyList() else return@withService
            val tracks = if (!otherMedia) library.filter { it.kind == MusicLibrary.Kind.MUSIC } else {
                val tones = runCatching {
                    val files = afc.readDir(MusicLibrary.RINGTONES_DIR).mapNotNull { n ->
                        runCatching { afc.stat("${MusicLibrary.RINGTONES_DIR}/$n") }.getOrNull()?.takeIf { !it.isDir }?.let { n to it.size }
                    }
                    val plist = runCatching { Plist.parse(afc.readHead(MusicLibrary.RINGTONES_PLIST, 4 shl 20)) }.getOrNull()
                    MusicLibrary.ringtones(plist, files)
                }.getOrDefault(emptyList())
                library.filter { it.kind != MusicLibrary.Kind.MUSIC } + tones
            }
            if (otherMedia) {
                val counts = tracks.groupingBy { it.kind }.eachCount()
                log(if (counts.isEmpty()) "No podcasts, audiobooks, videos or ringtones on the iPod."
                    else counts.entries.joinToString(", ") { (k, n) -> "$n ${k.folder.lowercase()}" } +
                        " (${humanSize(tracks.sumOf { it.size })}).")
            } else {
                log("${tracks.size} songs (${humanSize(tracks.sumOf { it.size })}) in the library.")
            }
            if (tracks.isEmpty()) return@withService
            cmds.offer(MusicCmd.Show)
            while (true) {
                val cmd = cmds.poll(15, TimeUnit.MINUTES) ?: break
                when (cmd) {
                    is MusicCmd.Close -> break
                    is MusicCmd.Show -> runOnUiThread { showMusic(tracks, cmds) }
                    is MusicCmd.Copy -> {
                        copySongs(afc, cmd.tracks, cmd.writeTags)
                        cmds.offer(MusicCmd.Show)
                    }
                }
            }
        }
        log("Music browser closed.")
    }
}

/**
 * Reads the iPod's music library: MediaLibrary.sqlitedb (iOS 5+), or the iTunes Library.itlp
 * folder (iPhone OS 3 / iOS 4). Returns null (after logging why) if neither can be read.
 */
internal fun MainActivity.loadMusicLibrary(afc: AfcClient, playlists: MutableList<Playlist>? = null): List<MusicLibrary.Track>? {
    copySqlite(afc, MusicLibrary.DB_PATH, "medialib")?.let { file ->
        return try {
            withSqlite(file) { db ->
                val lib = MusicLibrary(db)
                lib.load().also {
                    log("(library: ${lib.diagnostics})")
                    playlists?.addAll(runCatching { Playlists.fromSql(db) }.onFailure { e -> log("(couldn't read playlists: ${e.message})") }.getOrDefault(emptyList()))
                }
            }
        } catch (e: Exception) {
            log("Couldn't read the music library: ${e.message}")
            null
        }
    }
    // iPhone OS 3 / iOS 4: two SQLite files that belong together.
    val library = copySqlite(afc, ItlpLibrary.LIBRARY, "itlp-library")
    val locations = copySqlite(afc, ItlpLibrary.LOCATIONS, "itlp-locations")
    if (library != null && locations != null) {
        return try {
            withSqlite(library) { db ->
                db.exec("ATTACH DATABASE '${locations.path.replace("'", "''")}' AS loc")
                ItlpLibrary(db).load().also {
                    log("(library: iOS 3/4 iTunes Library.itlp, ${it.size} items)")
                    playlists?.addAll(runCatching { Playlists.fromSql(db) }.onFailure { e -> log("(couldn't read playlists: ${e.message})") }.getOrDefault(emptyList()))
                }
            }
        } catch (e: Exception) {
            log("Couldn't read the iOS 3/4 music library: ${e.message}")
            null
        }
    }
    // iPhone OS 1–2: binary iTunesDB (iTunesCDB = zlib-compressed variant).
    for (path in listOf(ITunesDb.CDB_PATH, ITunesDb.DB_PATH)) {
        val size = runCatching { afc.stat(path).size }.getOrNull() ?: continue
        return try {
            val raw = afc.readHead(path, size.toInt())
            ITunesDb.parse(raw).also {
                log("(library: iPhone OS 1–2 ${path.substringAfterLast('/')}, ${it.size} items)")
                playlists?.addAll(runCatching { ITunesDb.parsePlaylists(raw) }.getOrDefault(emptyList()))
            }
        } catch (e: Exception) {
            log("Couldn't read ${path.substringAfterLast('/')}: ${e.message}")
            null
        }
    }
    log("No music library found on the device.")
    return null
}

/** Searchable song list: tap to check/uncheck, Select all/none applies to what the search shows. */
internal fun MainActivity.showMusic(tracks: List<MusicLibrary.Track>, cmds: LinkedBlockingQueue<MusicCmd>) {
    if (isFinishing) { cmds.offer(MusicCmd.Close); return }
    val density = resources.displayMetrics.density
    val pad = (16 * density).toInt()
    var visible: List<MusicLibrary.Track> = emptyList()

    fun matches(t: MusicLibrary.Track, q: String) = q.isBlank() || q.trim().split(Regex("\\s+")).all { w ->
        t.title.contains(w, true) || t.artist.contains(w, true) || t.album.contains(w, true)
    }

    val search = android.widget.EditText(this).apply {
        hint = "Search artist, title or album"
        setSingleLine()
        setText(musicQuery)
    }
    val hintView = TextView(this).apply {
        textSize = 12f
        alpha = 0.7f
        setPadding(pad / 4, 0, pad / 4, pad / 4)
    }
    // Fast-scroll bubble shows the artist's first letter (the list is sorted by artist).
    fun initial(t: MusicLibrary.Track) = if (musicOther) t.kind.label.take(1) else t.artist.firstOrNull()?.uppercaseChar()?.let { if (it.isLetter()) "$it" else "#" } ?: "#"
    var sections: Array<String> = emptyArray()
    var sectionStarts: IntArray = IntArray(0)
    val adapter = object : BaseAdapter(), android.widget.SectionIndexer {
        override fun notifyDataSetChanged() {
            val names = ArrayList<String>(); val starts = ArrayList<Int>()
            visible.forEachIndexed { i, t -> val c = initial(t); if (names.lastOrNull() != c) { names += c; starts += i } }
            sections = names.toTypedArray(); sectionStarts = starts.toIntArray()
            super.notifyDataSetChanged()
        }
        override fun getSections(): Array<Any> = arrayOf(*sections)
        override fun getPositionForSection(section: Int) = sectionStarts.getOrElse(section) { visible.size }
        override fun getSectionForPosition(position: Int): Int {
            var sec = 0
            while (sec + 1 < sectionStarts.size && sectionStarts[sec + 1] <= position) sec++
            return sec
        }
        override fun getCount() = visible.size
        override fun getItem(position: Int) = visible[position]
        override fun getItemId(position: Int) = visible[position].pid
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val tv = convertView as? TextView ?: TextView(this@showMusic).apply {
                val v = (10 * density).toInt()
                setPadding(pad / 2, v, pad / 2, v)
                textSize = 15f
            }
            val t = visible[position]
            val box = if (t.pid in musicSelected) "☑" else "☐"
            val drm = if (t.isDrm) "  🔒" else ""
            val kind = if (musicOther) "${t.kind.label} · " else ""
            val by = if (t.kind == MusicLibrary.Kind.RINGTONE) "" else "${t.artist} — ${t.album}  "
            tv.text = "$box  ${t.title}$drm\n      $kind$by(${humanSize(t.size)})"
            tv.setBackgroundColor(if (t.pid in musicSelected) 0x331E88E5 else 0)
            return tv
        }
    }
    val list = ListView(this).apply {
        this.adapter = adapter
        isFastScrollEnabled = true
        isFastScrollAlwaysVisible = true
    }
    val container = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(pad, pad / 2, pad, 0)
        addView(search)
        addView(hintView)
        addView(list, LinearLayout.LayoutParams(-1, (resources.displayMetrics.heightPixels * 0.55).toInt()))
    }
    val dialog = MaterialAlertDialogBuilder(this)
        .setTitle(if (musicOther) "Podcasts, Audiobooks, Video & Ringtones (${tracks.size})" else "Music (${tracks.size} songs)")
        .setView(container)
        .setPositiveButton("Copy", null)
        .setNeutralButton("Select all", null)
        .setNegativeButton("Close", null)
        .create()

    var handled = false
    fun go(cmd: MusicCmd) {
        if (!handled) { handled = true; cmds.offer(cmd) }
        dialog.dismiss()
    }

    fun refresh() {
        visible = tracks.filter { matches(it, musicQuery) }
        adapter.notifyDataSetChanged()
        val selectedTracks = tracks.filter { it.pid in musicSelected }
        hintView.text = "${visible.size} shown · ${selectedTracks.size} selected (${humanSize(selectedTracks.sumOf { it.size })})"
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)?.apply {
            isEnabled = selectedTracks.isNotEmpty()
            text = if (selectedTracks.isEmpty()) "Copy" else "Copy ${selectedTracks.size}"
        }
        dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL)?.text =
            if (visible.isNotEmpty() && visible.all { it.pid in musicSelected }) "Select none" else "Select all"
    }

    search.addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) {
            musicQuery = s?.toString().orEmpty()
            refresh()
        }
    })
    list.setOnItemClickListener { _, _, position, _ ->
        val pid = visible[position].pid
        if (!musicSelected.add(pid)) musicSelected.remove(pid)
        refresh()
    }
    dialog.setOnShowListener {
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val chosen = tracks.filter { it.pid in musicSelected }
            if (chosen.isNotEmpty()) {
                handled = true
                dialog.dismiss()
                confirmCopySongs(chosen, cmds)
            }
        }
        dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
            if (visible.isNotEmpty() && visible.all { it.pid in musicSelected }) visible.forEach { musicSelected.remove(it.pid) }
            else visible.forEach { musicSelected.add(it.pid) }
            refresh()
        }
        dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).setOnClickListener { go(MusicCmd.Close) }
        refresh()
    }
    dialog.setOnCancelListener { if (!handled) { handled = true; cmds.offer(MusicCmd.Close) } }
    dialog.show()
}

internal fun MainActivity.confirmCopySongs(chosen: List<MusicLibrary.Track>, cmds: LinkedBlockingQueue<MusicCmd>) {
    val total = chosen.sumOf { it.size }
    val drm = chosen.count { it.isDrm }
    val free = runCatching {
        android.os.StatFs(android.os.Environment.getExternalStorageDirectory().path).availableBytes
    }.getOrDefault(-1L)
    val msg = buildString {
        if (musicOther) {
            append("Copy ${chosen.size} item${if (chosen.size == 1) "" else "s"} (${humanSize(total)}) to ")
            append(chosen.map { it.kind.folder }.distinct().joinToString(", ") { "Download/${IpaSaver.FOLDER}/$it/" } + "?")
        } else {
            append("Copy ${chosen.size} song${if (chosen.size == 1) "" else "s"} (${humanSize(total)}) as \"Artist - Title\" to ")
            append("Download/${IpaSaver.FOLDER}/$MUSIC_SUBFOLDER/?")
        }
        if (free >= 0) {
            append("\n\nFree space on this phone: ${humanSize(free)}")
            if (total > free) append("\n⚠️ Not enough space for all of them.")
        }
        if (drm > 0) {
            append("\n\n🔒 $drm of these are DRM-protected iTunes purchases (.m4p). ")
            append("They'll only play in iTunes/Apple Music on a computer authorized for that Apple ID.")
        }
    }
    val pad = (20 * resources.displayMetrics.density).toInt()
    val tagBox = MaterialCheckBox(this).apply {
        text = "Write song info (title, artist, album, track…) into the files"
        isChecked = prefs.getBoolean("write_tags", true)
    }
    val body = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(pad, pad / 2, pad, 0)
        addView(TextView(this@confirmCopySongs).apply { text = msg; textSize = 15f })
        addView(tagBox)
    }
    var handled = false
    MaterialAlertDialogBuilder(this)
        .setView(body)
        .setPositiveButton("Copy") { _, _ ->
            handled = true
            prefs.edit().putBoolean("write_tags", tagBox.isChecked).apply()
            cmds.offer(MusicCmd.Copy(chosen, tagBox.isChecked))
        }
        .setNegativeButton("Back") { _, _ -> handled = true; cmds.offer(MusicCmd.Show) }
        .setOnDismissListener { if (!handled) { handled = true; cmds.offer(MusicCmd.Show) } }
        .show()
}

/** Worker thread: copies songs with Stop support, naming them "Artist - Title.ext". */
internal fun MainActivity.copySongs(afc: AfcClient, tracks: List<MusicLibrary.Track>, writeTags: Boolean) {
    val saver = IpaSaver(this)
    val used = HashSet<String>()
    var ok = 0
    var failed = 0
    var done = 0
    stopMode = StopMode.NONE
    keepScreenOn(true)
    setStopButtonVisible(true)
    afc.cancelCheck = { checkStopNow() }
    log("\nCopying ${tracks.size} items to Download/${IpaSaver.FOLDER}/")
    try {
        for ((i, t) in tracks.withIndex()) {
            if (stopMode != StopMode.NONE) break
            val name = MusicLibrary.uniqueName(MusicLibrary.saveName(t), used)
            val folder = t.kind.folder
            log("[${i + 1}/${tracks.size}] $name")
            val target = saver.create(name, folder)
            try {
                if (writeTags && t.extension in TAGGABLE && t.kind != MusicLibrary.Kind.VIDEO) {
                    // Tagging needs random access, so stage the file in app cache first.
                    val tmp = java.io.File(cacheDir, "song.tmp")
                    try {
                        java.io.FileOutputStream(tmp).use { afc.copyFile(t.devicePath, it) }
                        val r = try {
                            AudioTagger.writeTagged(tmp, t.extension, tagsFor(t), target.stream)
                        } catch (e: Exception) {
                            // Unusual file layout: save it untouched rather than failing.
                            log("  couldn't write tags (${e.message}); saved as-is")
                            target.discard()
                            val plain = saver.create(name, folder)
                            tmp.inputStream().use { it.copyTo(plain.stream) }
                            plain.commit()
                            ok++
                            done = i + 1
                            continue
                        }
                        if (r == AudioTagger.Result.ID3_KEPT) log("  (kept the file's existing ID3 tag)")
                    } finally {
                        tmp.delete()
                    }
                } else {
                    afc.copyFile(t.devicePath, target.stream)
                }
                target.commit()
                ok++
            } catch (e: StopNowException) {
                target.discard()
                log("  stopped; the partial file was discarded")
                break
            } catch (e: Exception) {
                target.discard()
                log("  FAILED: ${e.message}")
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
    log((if (stopped) "Stopped" else "Done") + ": $ok copied" +
        (if (failed > 0) ", $failed failed" else "") +
        (if (stopped && done < tracks.size) ", ${tracks.size - done} not copied" else "") + ".")
    musicSelected.clear()
}

internal fun MainActivity.tagsFor(t: MusicLibrary.Track) = AudioTagger.Tags(
    title = t.title,
    artist = t.artist.takeIf { it != MusicLibrary.UNKNOWN_ARTIST },
    album = t.album.takeIf { it != MusicLibrary.UNKNOWN_ALBUM },
    albumArtist = t.albumArtist,
    genre = t.genre,
    composer = t.composer,
    year = t.year,
    track = t.trackNumber,
    trackCount = t.trackCount,
    disc = t.discNumber,
    discCount = t.discCount,
)

// ---------------------------------------------------------------- playlists

internal fun MainActivity.showMusicMenu(device: UsbDevice) {
    MaterialAlertDialogBuilder(this)
        .setTitle("Music")
        .setItems(arrayOf("Songs", "Playlists")) { _, which ->
            if (which == 0) browseMusic(device, otherMedia = false) else startPlaylists(device)
        }
        .setNegativeButton("Cancel", null)
        .show()
}

/** Reads the library and its playlists, then lets the user pick which to export as .m3u. */
internal fun MainActivity.startPlaylists(device: UsbDevice) {
    log("\nLoading playlists…")
    var tracks: List<MusicLibrary.Track> = emptyList()
    val playlists = ArrayList<Playlist>()
    runOnDevice(device, after = { if (playlists.isNotEmpty()) runOnUiThread { showPlaylists(device, tracks, playlists) } }) { mux, lockdown ->
        withService(mux, lockdown, AfcClient.SERVICE) { s ->
            tracks = loadMusicLibrary(AfcClient(s), playlists) ?: return@withService
            log(if (playlists.isEmpty()) "No playlists on the device." else "${playlists.size} playlists.")
        }
    }
}

internal fun MainActivity.showPlaylists(device: UsbDevice, tracks: List<MusicLibrary.Track>, playlists: List<Playlist>) {
    if (isFinishing) return
    val byId = tracks.associateBy { it.pid }
    val labels = playlists.map { p ->
        val n = p.itemPids.count { it in byId }
        "${p.name}  ·  $n song${if (n == 1) "" else "s"}" + if (p.smart) "  ·  smart" else ""
    }
    val checked = BooleanArray(playlists.size) { true }
    val copyBox = MaterialCheckBox(this).apply {
        text = "Also copy songs that aren't saved yet"
        isChecked = prefs.getBoolean("playlist_copy_songs", false)
    }
    val list = android.widget.ListView(this).apply {
        choiceMode = android.widget.ListView.CHOICE_MODE_MULTIPLE
        adapter = android.widget.ArrayAdapter(this@showPlaylists, android.R.layout.simple_list_item_multiple_choice, labels)
        for (i in labels.indices) setItemChecked(i, true)
        setOnItemClickListener { _, _, i, _ -> checked[i] = isItemChecked(i) }
    }
    val pad = (16 * resources.displayMetrics.density).toInt()
    val body = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(pad, 0, pad, 0)
        addView(TextView(this@showPlaylists).apply {
            text = "Saved as .m3u playlists in Download/IPA Backup/$PLAYLISTS_SUBFOLDER. They point at the songs in " +
                "IPA Backup/Music (named Artist - Title), so the songs need to be saved there too."
            textSize = 13f
            alpha = 0.8f
        })
        addView(copyBox)
        addView(list, LinearLayout.LayoutParams(-1, (resources.displayMetrics.heightPixels * 0.45).toInt()))
    }
    MaterialAlertDialogBuilder(this)
        .setTitle("Export playlists")
        .setView(body)
        .setPositiveButton("Export") { _, _ ->
            prefs.edit().putBoolean("playlist_copy_songs", copyBox.isChecked).apply()
            val chosen = playlists.filterIndexed { i, _ -> checked[i] }
            if (chosen.isNotEmpty()) exportPlaylists(device, chosen, byId, copyBox.isChecked)
        }
        .setNegativeButton("Cancel", null)
        .show()
}

internal fun MainActivity.exportPlaylists(device: UsbDevice, chosen: List<Playlist>, byId: Map<Long, MusicLibrary.Track>, copySongsToo: Boolean) {
    log("\nSaving ${chosen.size} playlist(s)…")
    val used = HashSet<String>()
    for (p in chosen) {
        val name = MusicLibrary.uniqueName(IpaSaver.clean(p.name).ifBlank { "Playlist" } + ".m3u", used)
        runCatching { saveBytes(name, PLAYLISTS_SUBFOLDER, Playlists.m3u(p, byId).toByteArray()) }
            .onSuccess { log("  $name (${p.itemPids.count { it in byId }} songs)") }
            .onFailure { log("  $name FAILED: ${it.message}") }
    }
    if (!copySongsToo) { log("Done."); return }
    // Songs in these playlists that aren't in the save folders yet.
    val saver = IpaSaver(this)
    val existing = HashMap<String, Set<String>>()
    val missing = chosen.flatMap { it.itemPids }.distinct().mapNotNull { byId[it] }.filter { t ->
        MusicLibrary.saveName(t).lowercase() !in existing.getOrPut(t.kind.folder) { saver.existingNames(t.kind.folder).map { it.lowercase() }.toSet() }
    }
    if (missing.isEmpty()) { log("All their songs are already saved ✓"); return }
    log("${missing.size} song(s) not saved yet (${humanSize(missing.sumOf { it.size })}).")
    runOnDevice(device) { mux, lockdown ->
        withService(mux, lockdown, AfcClient.SERVICE) { s -> copySongs(AfcClient(s), missing, prefs.getBoolean("write_tags", true)) }
    }
}
