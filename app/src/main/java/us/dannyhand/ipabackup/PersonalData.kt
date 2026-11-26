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

// Contacts, notes, bookmarks, calendars, history, messages and Cydia packages.
// These are MainActivity extension functions: they run on the activity and use its state and helpers.

// ---------------------------------------------------------------- contacts, notes, bookmarks

internal fun MainActivity.exportPersonalData(device: UsbDevice) {
    val pad = (20 * resources.displayMetrics.density).toInt()
    fun box(label: String, key: String) = MaterialCheckBox(this).apply { text = label; isChecked = prefs.getBoolean(key, true) }
    val contacts = box("Contacts → .vcf (imports into Google Contacts)", "export_contacts")
    val notes = box("Notes → .html, plus a .zip of text files", "export_notes")
    val bookmarks = box("Safari bookmarks & Reading List → .html (imports into any browser)", "export_bookmarks")
    val calendars = box("Calendars → .ics, one file per calendar (imports into Google Calendar)", "export_calendars")
    val safariHistory = box("Safari history → .html", "export_safari_history")
    val calls = box("Call & FaceTime history → .html", "export_calls")
    val body = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(pad, pad / 2, pad, 0)
        addView(TextView(this@exportPersonalData).apply {
            text = "Saves to Download/${IpaSaver.FOLDER}/$PERSONAL_SUBFOLDER/. Needs a jailbroken iPod with AFC2."
            textSize = 13f
            alpha = 0.8f
        })
        addView(contacts)
        addView(notes)
        addView(bookmarks)
        addView(calendars)
        addView(safariHistory)
        addView(calls)
    }
    MaterialAlertDialogBuilder(this)
        .setTitle("Personal data")
        .setView(body)
        .setPositiveButton("Export") { _, _ ->
            prefs.edit().putBoolean("export_contacts", contacts.isChecked).putBoolean("export_notes", notes.isChecked)
                .putBoolean("export_bookmarks", bookmarks.isChecked).putBoolean("export_calendars", calendars.isChecked)
                .putBoolean("export_safari_history", safariHistory.isChecked).putBoolean("export_calls", calls.isChecked).apply()
            if (contacts.isChecked || notes.isChecked || bookmarks.isChecked || calendars.isChecked || safariHistory.isChecked || calls.isChecked)
                runPersonalExport(device, contacts.isChecked, notes.isChecked, bookmarks.isChecked, calendars.isChecked,
                    safariHistory.isChecked, calls.isChecked)
        }
        .setNegativeButton("Cancel", null)
        .show()
}

internal fun MainActivity.runPersonalExport(device: UsbDevice, doContacts: Boolean, doNotes: Boolean, doBookmarks: Boolean, doCalendars: Boolean,
                              doSafariHistory: Boolean = false, doCalls: Boolean = false) {
    log("\nExporting personal data…")
    runOnDevice(device) { mux, lockdown ->
        val stream = tryAfc2(mux, lockdown) ?: run {
            log("Contacts, notes and bookmarks live in system folders that need a jailbroken device with " +
                "AFC2 (\"Apple File Conduit 2\" from Cydia). Voice Memos and Books work without it.")
            return@runOnDevice
        }
        stream.use {
            val afc = AfcClient(stream)
            val saver = IpaSaver(this)
            val stamp = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            val owner = lockdown.deviceName

            fun saveText(name: String, text: String): String {
                val t = saver.create(name, PERSONAL_SUBFOLDER)
                try {
                    t.stream.write(text.toByteArray(Charsets.UTF_8))
                    t.commit()
                    return t.location
                } catch (e: Exception) {
                    t.discard()
                    throw e
                }
            }

            if (doContacts) try {
                val db = copySqlite(afc, AddressBook.DB_PATH, "contacts") ?: throw IOException("AddressBook.sqlitedb not found")
                val list = withSqlite(db) { AddressBook(it).load() }
                val where = saveText("Contacts ($owner) $stamp.vcf", VCard.write(list))
                log("Contacts: ${list.size} saved to $where")
            } catch (e: Exception) {
                log("Contacts FAILED: ${e.message}")
            }

            if (doNotes) try {
                val db = copySqlite(afc, NotesDb.DB_PATH, "notes") ?: throw IOException("notes.sqlite not found")
                val list = withSqlite(db) { NotesDb(it).load() }
                val df = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
                val where = saveText("Notes ($owner) $stamp.html", NotesExport.html(list, owner, df))
                // Plus one .txt per note, for search or import elsewhere.
                val t = saver.create("Notes ($owner) $stamp (text).zip", PERSONAL_SUBFOLDER)
                try {
                    val zos = java.util.zip.ZipOutputStream(t.stream)
                    val used = HashSet<String>()
                    val day = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    for (n in list) {
                        val prefix = (n.modifiedMs ?: n.createdMs)?.let { day.format(java.util.Date(it)) + " " } ?: ""
                        val name = MusicLibrary.uniqueName(IpaSaver.clean(prefix + n.title).take(100) + ".txt", used)
                        zos.putNextEntry(java.util.zip.ZipEntry(name).apply { (n.modifiedMs ?: n.createdMs)?.let { time = it } })
                        zos.write(NotesExport.toText(n.html).toByteArray(Charsets.UTF_8))
                        zos.closeEntry()
                    }
                    zos.finish()
                    t.commit()
                } catch (e: Exception) {
                    t.discard()
                    throw e
                }
                log("Notes: ${list.size} saved to $where (and a .zip of text files)")
            } catch (e: Exception) {
                log("Notes FAILED: ${e.message}")
            }

            if (doBookmarks) try {
                val db = copySqlite(afc, SafariBookmarks.DB_PATH, "bookmarks") ?: throw IOException("Bookmarks.db not found")
                val root = withSqlite(db) { SafariBookmarks(it).load() }
                val where = saveText("Safari Bookmarks ($owner) $stamp.html", BookmarksHtml.write(root))
                log("Safari bookmarks: ${root.count()} saved to $where")
            } catch (e: Exception) {
                log("Safari bookmarks FAILED: ${e.message}")
            }

            if (doCalendars) try {
                val db = copySqlite(afc, CalendarDb.DB_PATH, "calendar") ?: throw IOException("Calendar.sqlitedb not found")
                val cals = withSqlite(db) { CalendarDb(it).load() }
                if (cals.isEmpty()) log("Calendars: no events found.")
                val used = HashSet<String>()
                for (cal in cals) {
                    val name = MusicLibrary.uniqueName(IpaSaver.clean("Calendar - ${cal.title} ($owner) $stamp").take(150) + ".ics", used)
                    saveText(name, Ics.write(cal, owner))
                    log("Calendar \"${cal.title}\": ${cal.events.size} events saved")
                }
            } catch (e: Exception) {
                log("Calendars FAILED: ${e.message}")
            }

            val df = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
            if (doSafariHistory) try {
                val bytes = java.io.ByteArrayOutputStream().also { afc.copyFile(SafariHistory.PATH, it) }.toByteArray()
                val visits = SafariHistory.parse(Plist.parse(bytes))
                if (visits.isEmpty()) log("Safari history: empty.") else {
                    val where = saveText("Safari History ($owner) $stamp.html", SafariHistory.html(owner, visits, df))
                    log("Safari history: ${visits.size} pages saved to $where")
                }
            } catch (e: AfcException) {
                log("Safari history: none on the device.")
            } catch (e: Exception) {
                log("Safari history FAILED: ${e.message}")
            }

            if (doCalls) try {
                val db = copySqlite(afc, CallHistory.DB_PATH, "calls")
                val list = db?.let { withSqlite(it) { CallHistory(it).load() } }.orEmpty()
                if (list.isEmpty()) log("Call history: none on the device (an iPod only keeps FaceTime calls).") else {
                    val contactsList = runCatching {
                        copySqlite(afc, AddressBook.DB_PATH, "contacts")?.let { withSqlite(it) { AddressBook(it).load() } }
                    }.getOrNull().orEmpty()
                    val where = saveText("Call History ($owner) $stamp.html", CallHistoryHtml.html(owner, list, ContactNames(contactsList), df))
                    log("Call history: ${list.size} calls saved to $where")
                }
            } catch (e: Exception) {
                log("Call history FAILED: ${e.message}")
            }
        }
    }
}

// ---------------------------------------------------------------- messages

internal fun MainActivity.exportMessages(device: UsbDevice) {
    val pad = (20 * resources.displayMetrics.density).toInt()
    val attach = MaterialCheckBox(this).apply {
        text = "Include photos & attachments (adds a .zip with one page per conversation)"
        isChecked = prefs.getBoolean("msg_attachments", true)
    }
    val body = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(pad, pad / 2, pad, 0)
        addView(TextView(this@exportMessages).apply {
            text = "Saves your iMessage/SMS conversations as a readable web page in " +
                "Download/${IpaSaver.FOLDER}/$PERSONAL_SUBFOLDER/, with names from your contacts. " +
                "Needs a jailbroken iPod with AFC2."
            textSize = 13f
            alpha = 0.8f
        })
        addView(attach)
    }
    MaterialAlertDialogBuilder(this)
        .setTitle("Messages")
        .setView(body)
        .setPositiveButton("Export") { _, _ ->
            prefs.edit().putBoolean("msg_attachments", attach.isChecked).apply()
            runMessagesExport(device, attach.isChecked)
        }
        .setNegativeButton("Cancel", null)
        .show()
}

internal fun MainActivity.runMessagesExport(device: UsbDevice, includeAttachments: Boolean) {
    log("\nExporting messages…")
    runOnDevice(device, after = { setStopButtonVisible(false); stopMode = StopMode.NONE }) { mux, lockdown ->
        val stream = tryAfc2(mux, lockdown) ?: run {
            log("Messages live in a system folder that needs a jailbroken device with AFC2 (\"Apple File Conduit 2\" from Cydia).")
            return@runOnDevice
        }
        stream.use {
            val afc = AfcClient(stream)
            val saver = IpaSaver(this)
            val owner = lockdown.deviceName
            val stamp = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

            val db = copySqlite(afc, MessagesDb.DB_PATH, "sms") ?: run { log("No messages database on the iPod."); return@runOnDevice }
            val chats = try {
                withSqlite(db) { MessagesDb(it).load() }
            } catch (e: Exception) {
                log("Couldn't read messages: ${e.message}")
                return@runOnDevice
            }
            val contacts = runCatching {
                copySqlite(afc, AddressBook.DB_PATH, "contacts")?.let { f -> withSqlite(f) { AddressBook(it).load() } }
            }.getOrNull().orEmpty()
            val names = ContactNames(contacts)
            val attachments = chats.flatMap { c -> c.messages.flatMap { it.attachments } }
            log("${chats.size} conversations, ${chats.sumOf { it.messages.size }} messages, ${attachments.size} attachments " +
                "(${humanSize(attachments.sumOf { it.size })}).")
            if (chats.isEmpty()) return@runOnDevice

            val dayFmt = java.text.DateFormat.getDateInstance(java.text.DateFormat.FULL)
            val shortDay = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
            val timeFmt = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)

            // 1) One self-contained page with every conversation (attachments listed by name).
            val single = buildString {
                append(MessagesHtml.index(owner, chats, names, { "#chat-${it.id}" }, shortDay))
                for (c in chats) {
                    append("<h2 id=\"chat-${c.id}\">").append(NotesExport.escape(MessagesHtml.title(c, names))).append("</h2>\n")
                    append(MessagesHtml.conversation(c, names, dayFmt, timeFmt) { null })
                }
            }
            val t = saver.create("Messages ($owner) $stamp.html", PERSONAL_SUBFOLDER)
            try {
                t.stream.write(MessagesHtml.page("Messages from $owner", single).toByteArray(Charsets.UTF_8))
                t.commit()
                log("Saved ${t.location}")
            } catch (e: Exception) {
                t.discard()
                log("Couldn't save messages: ${e.message}")
                return@runOnDevice
            }

            // 2) Zip: index + one page per conversation + the attachment files, linked and shown inline.
            if (!includeAttachments) return@runOnDevice
            stopMode = StopMode.NONE
            keepScreenOn(true)
            setStopButtonVisible(true)
            afc.cancelCheck = { checkStopNow() }
            val z = saver.create("Messages ($owner) $stamp.zip", PERSONAL_SUBFOLDER)
            try {
                val zos = java.util.zip.ZipOutputStream(z.stream)
                val tmp = java.io.File(cacheDir, "attachment.tmp")
                val hrefs = HashMap<Long, String>()
                var copied = 0
                var missing = 0
                for ((i, a) in attachments.withIndex()) {
                    if (stopMode == StopMode.AFTER_CURRENT) break
                    val path = a.devicePath ?: run { missing++; null } ?: continue
                    try {
                        java.io.FileOutputStream(tmp).use { afc.copyFile(path, it) }
                        val entry = "attachments/${a.id}-${IpaSaver.clean(a.name)}"
                        zos.putNextEntry(java.util.zip.ZipEntry("Messages/$entry"))
                        tmp.inputStream().use { it.copyTo(zos, 1 shl 16) }
                        zos.closeEntry()
                        hrefs[a.id] = entry
                        copied++
                    } catch (e: StopNowException) {
                        throw e
                    } catch (e: Exception) {
                        missing++ // deleted on the device, or never downloaded
                    }
                    if ((i + 1) % 25 == 0) log("  attachments: ${i + 1}/${attachments.size}")
                }
                tmp.delete()
                val pages = HashMap<Long, String>()
                val used = HashSet<String>()
                chats.forEachIndexed { i, c ->
                    pages[c.id] = MusicLibrary.uniqueName("%03d %s.html".format(i + 1, IpaSaver.clean(MessagesHtml.title(c, names)).take(60)), used)
                }
                fun put(name: String, html: String) {
                    zos.putNextEntry(java.util.zip.ZipEntry("Messages/$name"))
                    zos.write(html.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }
                put("index.html", MessagesHtml.page("Messages from $owner", MessagesHtml.index(owner, chats, names, { pages[it.id]!! }, shortDay)))
                for (c in chats) {
                    val title = MessagesHtml.title(c, names)
                    val content = "<p><a href=\"index.html\">← All conversations</a></p><h1>${NotesExport.escape(title)}</h1>\n" +
                        MessagesHtml.conversation(c, names, dayFmt, timeFmt) { hrefs[it.id] }
                    put(pages[c.id]!!, MessagesHtml.page(title, content))
                }
                zos.finish()
                z.commit()
                log("Saved ${z.location} ($copied attachments" + (if (missing > 0) ", $missing no longer on the iPod" else "") + ")")
            } catch (e: StopNowException) {
                z.discard()
                log("Stopped; the .zip was discarded (the .html with all messages was already saved).")
            } catch (e: Exception) {
                z.discard()
                log("Couldn't build the messages .zip: ${e.message}")
            } finally {
                afc.cancelCheck = {}
            }
        }
    }
}

// ---------------------------------------------------------------- Cydia

internal fun MainActivity.exportCydia(device: UsbDevice) {
    log("\nExporting Cydia packages…")
    runOnDevice(device) { mux, lockdown ->
        val stream = tryAfc2(mux, lockdown) ?: run {
            log("The package list is in a system folder that needs a jailbroken device with AFC2.")
            return@runOnDevice
        }
        stream.use {
            val afc = AfcClient(stream)
            fun read(path: String) = java.io.ByteArrayOutputStream().also { afc.copyFile(path, it) }.toString("UTF-8")
            val packages = Dpkg.parseStatus(read(Dpkg.STATUS))
            val sources = (runCatching { afc.readDir(Dpkg.SOURCES_DIR) }.getOrDefault(emptyList())
                .filter { it.endsWith(".list") }.map { "${Dpkg.SOURCES_DIR}/$it" } + Dpkg.SOURCES_FILE)
                .flatMap { p -> runCatching { Dpkg.parseSources(read(p)) }.getOrDefault(emptyList()) }.distinct()
            val owner = lockdown.deviceName
            val base = IpaSaver.clean("Cydia Packages ($owner) ${today()}")
            val where = saveBytes("$base.html", PERSONAL_SUBFOLDER, Dpkg.html(owner, packages, sources, today()).toByteArray())
            saveBytes("$base.txt", PERSONAL_SUBFOLDER, Dpkg.text(packages, sources).toByteArray())
            log("${packages.count { !Dpkg.isSystem(it) }} tweaks and apps, ${packages.count(Dpkg::isSystem)} system packages, " +
                "${sources.size} sources saved to $where (and a .txt list)")
        }
    }
}
