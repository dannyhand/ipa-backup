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

/**
 * IPA Backup: backs up an old iPod touch / iPhone / iPad to this phone over a USB OTG cable, with no
 * computer, no root on the phone and (for most features) no jailbreak on the iOS device.
 *
 * How a connection works (each layer has its own file and notes):
 *   USB Host API (usb/) -> usbmux, TCP-like streams over USB (mux/) -> lockdownd: pairing + TLS session (lockdown/,
 *   pairing/) -> per-feature services started through lockdownd: installation_proxy (apps), AFC (media files),
 *   house_arrest (app data), springboardservices (icons), and AFC2 (whole filesystem, jailbreak only).
 *
 * What each feature needs (confirmed on an iPod touch 4G, iOS 6.1.6, jailbroken):
 *   Apps (.ipa)            installation_proxy Archive: iOS 8 or older. Apple removed app extraction in iOS 9
 *                          (apps are "thinned" per device), so newer devices only work via the AFC2 fallback.
 *   App data               house_arrest: no jailbreak needed.
 *   Music / Other Media    AFC media folder: no jailbreak needed. The database format depends on the iOS version,
 *                          not on jailbreak: iTunesDB (1-2), iTunes Library.itlp (3-4), MediaLibrary.sqlitedb (5+).
 *   Photos, Voice Memos,   AFC media folder: no jailbreak needed.
 *   Books
 *   Contacts, Notes,       System databases under /var/mobile/Library, outside the media folder: need AFC2
 *   Bookmarks, Calendars,  ("Apple File Conduit 2" from Cydia). Doing these without a jailbreak would need the
 *   Messages               iTunes-style backup protocol (mobilebackup2), a much bigger job.
 *   Files                  Whole filesystem with AFC2; just the media folder without it.
 *
 * Things worth knowing:
 *  - App Store apps stay FairPlay-encrypted to the Apple ID that bought them. A backup reinstalls on devices
 *    signed into that account, or on jailbroken devices with AppSync. This app doesn't remove DRM.
 *  - iOS 6 and earlier show no "Trust this computer" prompt; the device just has to be unlocked to pair.
 *  - A device whose battery ran completely flat comes back with its clock at 1970. Anything recorded before the
 *    clock is set again (voice memos, photos) really carries a 1970 date; that's not a parsing bug.
 *  - The phone powers the device over OTG. If the version handshake gets no reply, try a powered OTG hub.
 */
class MainActivity : androidx.activity.ComponentActivity() {

    private companion object {
        const val ACTION_USB_PERMISSION = "us.dannyhand.ipabackup.USB_PERMISSION"
        /** Device path of an upload in progress; set while copying, cleared when done. */
        private const val PREF_PARTIAL_UPLOAD = "partial_upload"
        /** Root-filesystem AFC, installed by the "Apple File Conduit 2" jailbreak package. */
        const val AFC2_SERVICE = "com.apple.afc2"
        const val FILES_SUBFOLDER = "Files"
        const val MUSIC_SUBFOLDER = "Music"
        const val PHOTOS_SUBFOLDER = "Photos"
        const val MEMOS_SUBFOLDER = "Voice Memos"
        const val BOOKS_SUBFOLDER = "Books"
        const val PERSONAL_SUBFOLDER = "Personal"
        const val CRASH_SUBFOLDER = "Crash Reports"
        const val LOGS_SUBFOLDER = "Logs"
        const val PLAYLISTS_SUBFOLDER = "Playlists"
        const val MUSICLIB_SUBFOLDER = "Music Library Backups"
        const val LIB_DIR = "/iTunes_Control/iTunes"
        const val ART_DIR = "/iTunes_Control/Artwork"
        /** Name of a book copied in by Books › Add (a 16-hex-digit Persistent ID). */
        private val BOOK_COPY = Regex("^[0-9A-F]{16}\\.(epub|pdf)$")
        const val THUMB_PX = 200
        /** Formats we can write song info into (DRM .m4p is left untouched). */
        val TAGGABLE = setOf("m4a", "m4b", "m4r", "aac", "mp3")
    }

    /** Main-screen state (log, busy, Stop button, device) rendered by ui/MainScreen.kt. */
    private val ui = UiState()
    /** Most recent operation heading (log lines that start a new block), shown as the status card title. */
    @Volatile private var lastHeadline: String? = null
    private val usbManager by lazy { getSystemService(Context.USB_SERVICE) as UsbManager }
    private val prefs by lazy { getSharedPreferences("options", Context.MODE_PRIVATE) }
    private var busy: Boolean
        get() = ui.busy
        set(value) { ui.busy = value }
    /** What to run once USB permission is granted. */
    private var pendingAction: ((UsbDevice) -> Unit)? = null

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_USB_PERMISSION) return
            val device = intent.usbDevice()
            val action = pendingAction ?: ::probe
            pendingAction = null
            if (device != null && intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                action(device)
            } else {
                log("USB permission denied")
            }
        }
    }

    // ---------------------------------------------------------------- UI setup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        // Wallpaper-based colors for the classic dialogs too (Android 12+), matching the Compose screen.
        com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this)
        ui.device = prefs.getString("last_device", null)

        // Feature tiles, grouped into sections, at most three per row.
        val features = listOf(
            Feature("Back up apps", Icons.Outlined.Apps, "Apps") { withDevice(::probe) },
            Feature("Restore", Icons.Outlined.InstallMobile, "Apps") { withDevice(::showRestorePicker) },
            Feature("Uninstall", Icons.Outlined.DeleteSweep, "Apps") { withDevice(::startUninstall) },
            Feature("Music", Icons.Outlined.LibraryMusic, "Media") { withDevice(::showMusicMenu) },
            Feature("Photos", Icons.Outlined.PhotoLibrary, "Media") { withDevice(::browsePhotos) },
            Feature("Other media", Icons.Outlined.Podcasts, "Media") { withDevice { d -> browseMusic(d, otherMedia = true) } },
            Feature("Voice Memos", Icons.Outlined.Mic, "Media") { withDevice(::browseVoiceMemos) },
            Feature("Books", Icons.Outlined.MenuBook, "Media") { startBooks() },
            Feature("Messages", Icons.Outlined.Forum, "Personal data", needsJailbreak = true) { withDevice(::exportMessages) },
            Feature("Contacts, Notes & more", Icons.Outlined.Contacts, "Personal data", needsJailbreak = true) { withDevice(::exportPersonalData) },
            Feature("Cydia packages", Icons.Outlined.Extension, "Personal data", needsJailbreak = true) { withDevice(::exportCydia) },
            Feature("Device info", Icons.Outlined.Info, "Device") { withDevice(::showDeviceInfo) },
            Feature("Power", Icons.Outlined.PowerSettingsNew, "Device") { withDevice(::showPower) },
            Feature("Home screen", Icons.Outlined.Dashboard, "Device") { withDevice(::showHomeScreen) },
            Feature("System log", Icons.Outlined.Terminal, "Device") { withDevice(::showSyslog) },
            Feature("Crash reports", Icons.Outlined.BugReport, "Device") { withDevice(::browseCrashReports) },
            Feature("Files", Icons.Outlined.FolderOpen, "Tools") { withDevice(::browseFiles) },
            Feature("Music library", Icons.Outlined.Storage, "Tools") { withDevice(::startMusicLibraryBackup) },
            Feature("Clean up", Icons.Outlined.CleaningServices, "Tools") { startCleanup() },
            Feature("Certificates", Icons.Outlined.VerifiedUser, "Tools") { startCertificates() },
        )
        val notes = mapOf("Personal data" to "needs a jailbroken device with AFC2")
        setContent {
            IpaBackupTheme {
                MainScreen(
                    state = ui,
                    features = features,
                    sectionNotes = notes,
                    onCopyLog = ::copyLog,
                    onSaveLog = ::saveLog,
                    onClearLog = { ui.log.clear() },
                    onStop = ::confirmStop,
                )
            }
        }

        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(permissionReceiver, filter)
        }

        log("IPA Backup ${appVersion()}")
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        unregisterReceiver(permissionReceiver)
        fileServer?.stop()
        super.onDestroy()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            // Launched from the attach dialog: permission is already granted for this device.
            intent.usbDevice()?.let { if (prefs.getBoolean("auto_backup", false)) autoBackup(it) else probe(it) }
        }
    }

    /** Finds the iPod and runs [action] once we have USB permission. */
    private fun withDevice(action: (UsbDevice) -> Unit) {
        val devices = usbManager.deviceList.values.filter { AppleUsbTransport.isApple(it) }
        if (devices.isEmpty()) {
            log("No Apple USB device found. Check the OTG adapter and that the iPod is powered on.")
            return
        }
        val device = devices.firstOrNull { AppleUsbTransport.findMuxInterface(it) != null } ?: run {
            log("Apple device found but no usbmux interface (is it in recovery/DFU mode?)")
            return
        }
        if (usbManager.hasPermission(device)) {
            action(device)
        } else {
            pendingAction = action
            val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val pi = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION).setPackage(packageName), flags)
            usbManager.requestPermission(device, pi)
        }
    }

    // ---------------------------------------------------------------- device session plumbing

    /** USB -> usbmux -> lockdownd session, runs [job] on a background thread, then tears down. */
    /**
     * lockdownd connection that reconnects by itself: the iPod closes an idle lockdown connection
     * after a while (e.g. during a long backup), and every service start goes through lockdownd.
     */
    private inner class DeviceSession(private val mux: UsbMux) {
        private var stream: MuxStream = mux.connect(LockdownClient.PORT)
        private var client: LockdownClient = openSession(stream)
        val deviceName: String = runCatching { client.getValue("DeviceName") as String }.getOrDefault("iPod")
        val productVersion: String? = runCatching { client.getValue("ProductVersion") as String }.getOrNull()

        /** A lockdown value (null key = the whole domain), or null if it can't be read. */
        fun value(key: String?, domain: String?): Any? = runCatching { client.getValue(key, domain) }.getOrNull()

        /** The device clock, in seconds since 1970, or null if it won't say. */
        fun deviceTime(): Long? = runCatching { (client.getValue("TimeIntervalSince1970") as Number).toLong() }.getOrNull()

        init {
            val label = deviceName + (productVersion?.let { " · iOS $it" } ?: "")
            ui.device = label
            ui.connected = true
            prefs.edit().putString("last_device", label).apply()
        }

        fun startService(name: String): LockdownClient.ServiceInfo {
            if (!stream.isClosed) {
                try {
                    return client.startService(name)
                } catch (e: LockdownException) {
                    throw e // a real answer from lockdownd (e.g. InvalidService), not a dead connection
                } catch (e: IOException) {
                    // fall through and reconnect
                }
            }
            log("(lockdown connection was closed by the iPod; reconnecting)")
            close()
            stream = mux.connect(LockdownClient.PORT)
            client = openSession(stream, quiet = true)
            return client.startService(name)
        }

        fun close() {
            // Tolerant: after Restart/Shutdown (or a pulled cable) the device is already gone.
            runCatching { client.stopSession() }
            runCatching { stream.close() }
        }
    }

    private fun runOnDevice(device: UsbDevice, after: () -> Unit = {}, job: (UsbMux, DeviceSession) -> Unit) {
        if (busy) { log("Busy, please wait for the current operation to finish."); return }
        busy = true
        ui.task = lastHeadline
        Thread {
            try {
                AppleUsbTransport.open(usbManager, device, ::log).use { usb ->
                    UsbMux(usb, ::log).use { mux ->
                        mux.handshake()
                        mux.start()
                        val session = DeviceSession(mux)
                        try {
                            job(mux, session)
                        } finally {
                            session.close()
                        }
                    }
                }
            } catch (e: Exception) {
                log("ERROR: ${e.javaClass.simpleName}: ${e.message}")
            } finally {
                busy = false
                ui.connected = false
                ui.task = null
                keepScreenOn(false)
                after()
            }
        }.start()
    }

    /** Pairs if needed (or re-pairs if the saved pairing is rejected), then starts a TLS session. */
    private fun openSession(stream: MuxStream, quiet: Boolean = false): LockdownClient {
        val store = PairRecordStore(this)
        val lockdown = LockdownClient(stream, log = if (quiet) { _ -> } else ::log)
        val type = lockdown.queryType()
        if (type != "com.apple.mobile.lockdown") log("Unexpected QueryType: $type")
        val udid = lockdown.getValue("UniqueDeviceID") as String
        if (!quiet) log("${lockdown.getValue("DeviceName")}, iOS ${lockdown.getValue("ProductVersion")}")

        var record = store.load(udid)
        var freshPair = false
        if (record == null) {
            record = pairNew(lockdown, store, udid)
            freshPair = true
        }
        try {
            lockdown.validatePair(record)
        } catch (e: LockdownException) {
            if (e.code == "InvalidHostID" && !freshPair) {
                log("Saved pairing was rejected (iPod was reset or unpaired). Pairing again…")
                store.delete(udid)
                record = pairNew(lockdown, store, udid)
                lockdown.validatePair(record)
            } else {
                log("ValidatePair: ${e.code} (continuing)")
            }
        }
        lockdown.startSession(record)
        return lockdown
    }

    private fun pairNew(lockdown: LockdownClient, store: PairRecordStore, udid: String): PairRecord {
        log("Not paired yet. Generating keys and certificates…")
        val devKey = lockdown.getValue("DevicePublicKey") as? ByteArray
            ?: throw IOException("Device didn't return DevicePublicKey")
        val record = PairRecord.generate(devKey, store.systemBuid)
        log("Sending Pair request (unlock the iPod if it has a passcode)…")
        val reply = try {
            lockdown.pair(record)
        } catch (e: LockdownException) {
            if (e.code == "PasswordProtected") log("The iPod is locked. Unlock it and try again.")
            throw e
        }
        record.escrowBag = reply["EscrowBag"] as? ByteArray
        store.save(udid, record)
        log("Paired! Pairing saved for this iPod.")
        return record
    }

    private fun <T> withService(mux: UsbMux, lockdown: DeviceSession, name: String, block: (MuxStream) -> T): T {
        val svc = lockdown.startService(name)
        if (svc.ssl) throw IOException("$name wants SSL, which isn't supported yet")
        return mux.connect(svc.port).use(block)
    }

    /** Opens AFC2 if the iPod is jailbroken with AFC2 installed, else null. */
    /**
     * AFC2 is the jailbreak-only twin of AFC: same protocol, but rooted at "/" instead of /var/mobile/Media.
     * It's what makes contacts, notes, bookmarks, calendars, messages, the full file browser and the
     * app-bundle .ipa fallback possible. On a stock device lockdownd answers InvalidService.
     */
    private fun tryAfc2(mux: UsbMux, lockdown: DeviceSession): MuxStream? = try {
        val svc = lockdown.startService(AFC2_SERVICE)
        if (svc.ssl) null else mux.connect(svc.port)
    } catch (e: LockdownException) {
        null
    }

    // ---------------------------------------------------------------- app list + backup

    /** Connects, pairs if needed, lists user apps, then offers to back them up. */
    private fun probe(device: UsbDevice) {
        log("\nConnecting…")
        var apps: List<InstallationProxy.App> = emptyList()
        runOnDevice(device, after = { runOnUiThread { showPicker(device, apps) } }) { mux, lockdown ->
            apps = withService(mux, lockdown, InstallationProxy.SERVICE) { InstallationProxy(it).browseUserApps() }
            log("${apps.size} user apps installed.")
            if (prefs.getBoolean("show_icons", false)) fetchIcons(mux, lockdown, apps)
        }
    }

    /** Downloads home-screen icons for apps that aren't in the icon cache yet. */
    private fun fetchIcons(mux: UsbMux, lockdown: DeviceSession, apps: List<InstallationProxy.App>) {
        val cache = IconCache(this)
        val missing = apps.filter { !cache.has(it) }
        if (missing.isEmpty()) return
        log("Loading ${missing.size} app icons…")
        try {
            withService(mux, lockdown, SpringBoardServices.SERVICE) { stream ->
                val sbs = SpringBoardServices(stream)
                var ok = 0
                for (app in missing) {
                    val png = runCatching { sbs.iconPng(app.bundleId) }.getOrNull() ?: continue
                    if (runCatching { cache.put(app, png) }.getOrDefault(false)) ok++
                }
                if (ok < missing.size) log("Loaded $ok of ${missing.size} icons.")
            }
        } catch (e: Exception) {
            log("Couldn't load icons: ${e.message}")
        }
    }

    private fun showPicker(device: UsbDevice, apps: List<InstallationProxy.App>) {
        if (isFinishing || apps.isEmpty()) return
        val saved = IpaSaver(this).existingNames()
        val isSaved = apps.map { IpaSaver.ipaName(it.name, it.version, it.bundleId) in saved }
        val labels = apps.mapIndexed { i, a -> (if (isSaved[i]) "✓ " else "") + "${a.name} ${a.version}${sizeLabel(a.sizeBytes)}" }

        val pad = (16 * resources.displayMetrics.density).toInt()
        val skipBox = MaterialCheckBox(this).apply {
            text = "Skip apps already saved (✓ ${isSaved.count { it }} of ${apps.size})"
            isChecked = prefs.getBoolean("skip_saved", true)
        }
        val dataBox = MaterialCheckBox(this).apply {
            text = "Also back up app data (saves, settings) as a separate .zip"
            isChecked = prefs.getBoolean("include_data", false)
        }
        val autoBox = MaterialCheckBox(this).apply {
            text = "Back up new apps automatically when the device is plugged in"
            isChecked = prefs.getBoolean("auto_backup", false)
            setOnCheckedChangeListener { _, on ->
                prefs.edit().putBoolean("auto_backup", on).apply()
                if (on) Toast.makeText(this@MainActivity, "When Android asks which app to open for this device, pick IPA Backup and tick Always.", Toast.LENGTH_LONG).show()
            }
        }
        val iconCache = IconCache(this)
        val iconPx = (40 * resources.displayMetrics.density).toInt()
        var showIcons = prefs.getBoolean("show_icons", false)
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_multiple_choice, labels) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val tv = super.getView(position, convertView, parent) as TextView
                val icon = if (showIcons) iconCache.bitmap(apps[position]) else null
                if (icon != null) {
                    val d = android.graphics.drawable.BitmapDrawable(resources, icon).apply { setBounds(0, 0, iconPx, iconPx) }
                    tv.setCompoundDrawablesRelative(d, null, null, null)
                    tv.compoundDrawablePadding = pad / 2
                } else {
                    tv.setCompoundDrawablesRelative(null, null, null, null)
                }
                return tv
            }
        }
        val list = ListView(this).apply {
            choiceMode = ListView.CHOICE_MODE_MULTIPLE
            this.adapter = adapter
            isFastScrollEnabled = true
            isFastScrollAlwaysVisible = apps.size > 20
        }
        var pickerDialog: AlertDialog? = null
        val iconBox = MaterialCheckBox(this).apply {
            text = "Show app icons"
            isChecked = showIcons
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("show_icons", checked).apply()
                showIcons = checked
                if (checked && apps.any { !iconCache.has(it) }) {
                    // Icons come from the iPod: reconnect to fetch them, then this list reopens.
                    pickerDialog?.dismiss()
                    probe(device)
                } else {
                    adapter.notifyDataSetChanged()
                }
            }
        }
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            addView(skipBox)
            addView(dataBox)
            addView(iconBox)
            addView(autoBox)
            addView(list, LinearLayout.LayoutParams(-1, (resources.displayMetrics.heightPixels * 0.5).toInt()))
        }

        fun start(chosen: List<InstallationProxy.App>) {
            prefs.edit().putBoolean("skip_saved", skipBox.isChecked).putBoolean("include_data", dataBox.isChecked).apply()
            if (chosen.isEmpty()) { log("Nothing selected."); return }
            backup(device, chosen, skipBox.isChecked, dataBox.isChecked, saved)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Back up which apps?")
            .setView(view)
            .setPositiveButton("Back up selected") { _, _ ->
                val checked = list.checkedItemPositions
                start(apps.filterIndexed { i, _ -> checked.get(i) })
            }
            .setNeutralButton("Back up all") { _, _ ->
                prefs.edit().putBoolean("skip_saved", skipBox.isChecked).putBoolean("include_data", dataBox.isChecked).apply()
                confirmBackupAll(device, apps, saved, skipBox.isChecked, dataBox.isChecked)
            }
            .setNegativeButton("Cancel", null)
            .show()
            .also { pickerDialog = it }
    }

    /** "Back up all" safety check: what will run, how big it'll be, and whether the phone has room. */
    private fun confirmBackupAll(
        device: UsbDevice,
        apps: List<InstallationProxy.App>,
        saved: Set<String>,
        skipSaved: Boolean,
        includeData: Boolean,
    ) {
        val toArchive = apps.filter { !(skipSaved && IpaSaver.ipaName(it.name, it.version, it.bundleId) in saved) }
        val alreadySaved = apps.size - toArchive.size
        if (toArchive.isEmpty() && !includeData) {
            MaterialAlertDialogBuilder(this)
                .setMessage("All ${apps.size} apps are already saved. Uncheck \"Skip apps already saved\" to back them up again.")
                .setPositiveButton("OK") { _, _ -> showPicker(device, apps) }
                .setOnCancelListener { showPicker(device, apps) }
                .show()
            return
        }

        val installed = toArchive.sumOf { it.sizeBytes }
        // .ipa archives come out at roughly 90-95% of the installed size (see test backups).
        val estimate = (installed * 0.95).toLong()
        val largest = toArchive.maxByOrNull { it.sizeBytes }
        val free = runCatching {
            android.os.StatFs(android.os.Environment.getExternalStorageDirectory().path).availableBytes
        }.getOrDefault(-1L)

        val msg = buildString {
            append("Back up ${toArchive.size} app${if (toArchive.size == 1) "" else "s"}")
            if (alreadySaved > 0) append(" (skipping $alreadySaved already saved)")
            append("?\n\n")
            append("Estimated size: about ${humanSize(estimate)}\n")
            append("(${humanSize(installed)} installed on the iPod)\n")
            if (includeData) append("Plus app data for ${apps.size} apps (size known only once it's copied)\n")
            if (free >= 0) {
                append("\nFree space on this phone: ${humanSize(free)}\n")
                if (estimate > free) append("⚠️ Not enough space for everything. The backup will fail partway.\n")
                else if (estimate > free * 0.8) append("⚠️ This will use most of your free space.\n")
            }
            if (largest != null && largest.sizeBytes > 0) {
                append("\nThe iPod needs free space for one archive at a time (largest: ${largest.name}, ${humanSize(largest.sizeBytes)}).\n")
            }
            append("\nKeep both devices connected. You can stop at any time.")
        }
        var handled = false
        MaterialAlertDialogBuilder(this)
            .setTitle("Back up all apps?")
            .setMessage(msg)
            .setPositiveButton("Back up") { _, _ ->
                handled = true
                backup(device, apps, skipSaved, includeData, saved)
            }
            .setNegativeButton("Back") { _, _ -> handled = true; showPicker(device, apps) }
            .setOnDismissListener { if (!handled) showPicker(device, apps) }
            .show()
    }

    private fun backup(
        device: UsbDevice,
        apps: List<InstallationProxy.App>,
        skipSaved: Boolean,
        includeData: Boolean,
        savedNames: Set<String>,
    ) {
        val todo = apps.filter { app ->
            includeData || !(skipSaved && IpaSaver.ipaName(app.name, app.version, app.bundleId) in savedNames)
        }
        if (todo.isEmpty()) { log("\nEverything selected is already saved. Nothing to do."); return }
        if (busy) { log("Busy, please wait for the current operation to finish."); return }
        keepScreenOn(true)
        stopMode = StopMode.NONE
        setStopButtonVisible(true)
        val saver = IpaSaver(this)
        log("\nBacking up ${todo.size} app(s) to Download/${IpaSaver.FOLDER}/" + if (includeData) " (with app data)" else "")

        runOnDevice(device, after = { setStopButtonVisible(false); stopMode = StopMode.NONE }) { mux, lockdown ->
            val afc2 = tryAfc2(mux, lockdown)
            if (afc2 != null) log("AFC2 available (jailbroken): will copy the app bundle directly if archiving fails.")
            try {
                withService(mux, lockdown, InstallationProxy.SERVICE) { ipStream ->
                    withService(mux, lockdown, AfcClient.SERVICE) { afcStream ->
                        val ip = InstallationProxy(ipStream)
                        val afc = AfcClient(afcStream).apply { cancelCheck = ::checkStopNow }
                        val afc2Client = afc2?.let { AfcClient(it).apply { cancelCheck = ::checkStopNow } }
                        removeLeftoverArchives(ip, afc)

                        var ipas = 0
                        var skipped = 0
                        var datas = 0
                        var done = 0
                        val failed = mutableListOf<String>()

                        for ((i, app) in todo.withIndex()) {
                            if (stopMode != StopMode.NONE) break
                            log("\n[${i + 1}/${todo.size}] ${app.name} ${app.version}")
                            val ipaName = IpaSaver.ipaName(app.name, app.version, app.bundleId)
                            if (skipSaved && ipaName in savedNames) {
                                log("  .ipa already saved, skipping")
                                skipped++
                            } else {
                                try {
                                    backupIpa(ip, afc, afc2Client, saver, app)
                                    ipas++
                                } catch (e: StopNowException) {
                                    log("  stopped; the partial file was discarded")
                                    break
                                } catch (e: Exception) {
                                    log("  FAILED: ${e.message}")
                                    failed += app.name
                                }
                            }
                            if (includeData) {
                                try {
                                    if (backupData(mux, lockdown, saver, app)) datas++
                                } catch (e: StopNowException) {
                                    log("  stopped; the partial data backup was discarded")
                                    break
                                } catch (e: Exception) {
                                    log("  data FAILED: ${e.message}")
                                    failed += "${app.name} (data)"
                                }
                            }
                            done = i + 1
                        }

                        val remaining = todo.size - done
                        val header = if (stopMode != StopMode.NONE) "\nBackup stopped" else "\nBackup finished"
                        log("$header: $ipas .ipa saved" +
                            (if (skipped > 0) ", $skipped already saved" else "") +
                            (if (includeData) ", $datas data backups" else "") +
                            (if (stopMode != StopMode.NONE && remaining > 0) ", $remaining not backed up" else "") +
                            (if (failed.isEmpty()) "." else "\nFailed: ${failed.joinToString()}"))
                    }
                }
            } finally {
                afc2?.close()
            }
        }
    }

    /**
     * Deletes archives left in ApplicationArchives/ by an interrupted run (e.g. Stop now while
     * the iPod was still archiving), so they don't eat the iPod's free space.
     */
    private fun removeLeftoverArchives(ip: InstallationProxy, afc: AfcClient) {
        val leftovers = runCatching { afc.readDir("ApplicationArchives") }.getOrDefault(emptyList())
            .filter { it.endsWith(".zip") }
        if (leftovers.isEmpty()) return
        var removed = 0
        for (f in leftovers) {
            if (runCatching { ip.removeArchive(f.removeSuffix(".zip")) }.isSuccess) removed++
        }
        log("Cleaned up $removed leftover archive(s) on the iPod.")
    }

    private fun backupIpa(ip: InstallationProxy, afc: AfcClient, afc2: AfcClient?, saver: IpaSaver, app: InstallationProxy.App) {
        try {
            backupIpaViaArchive(ip, afc, saver, app)
        } catch (e: StopNowException) {
            throw e
        } catch (e: Exception) {
            if (afc2 == null) throw e
            log("  archive failed (${e.message}); copying the app bundle over AFC2 instead…")
            backupIpaViaAfc2(afc2, saver, app)
        }
    }

    /** installation_proxy Archive (works without jailbreak on iOS ≤ 8). */
    private fun backupIpaViaArchive(ip: InstallationProxy, afc: AfcClient, saver: IpaSaver, app: InstallationProxy.App) {
        runCatching { ip.removeArchive(app.bundleId) } // clear any leftover archive; fails harmlessly if none

        var lastPct = -1
        try {
            ip.archive(app.bundleId) { status, pct ->
                checkStopNow()
                if (pct != null && pct / 25 > lastPct / 25) { lastPct = pct; log("  archiving… $pct% ($status)") }
            }
        } catch (e: StopNowException) {
            // The iPod keeps archiving in the background and this connection is mid-command, so don't
            // send RemoveArchive now; the leftover is cleaned up at the start of the next backup.
            log("  (the iPod may finish this archive in the background; it's deleted next time you back up)")
            throw e
        }

        val target = saver.create(IpaSaver.ipaName(app.name, app.version, app.bundleId))
        try {
            var lastDecile = -1L
            val n = afc.copyFile("ApplicationArchives/${app.bundleId}.zip", target.stream) { copied, total ->
                val decile = if (total > 0) copied * 10 / total else -1L
                if (decile > lastDecile && decile % 2 == 0L) { lastDecile = decile; log("  copying ${mb(copied)} / ${mb(total)}") }
            }
            target.commit()
            log("  saved ${target.location} (${mb(n)})")
        } catch (e: Exception) {
            target.discard()
            throw e
        } finally {
            runCatching { ip.removeArchive(app.bundleId) }
                .onFailure { log("  (couldn't delete the archive on the iPod: ${it.message})") }
        }
    }

    // ---------------------------------------------------------------- stopping

    private enum class StopMode { NONE, AFTER_CURRENT, NOW }

    private class StopNowException : RuntimeException("Stopped")

    @Volatile private var stopMode = StopMode.NONE

    /** Called from transfer loops and progress callbacks; aborts the current step on "Stop now". */
    private fun checkStopNow() {
        if (stopMode == StopMode.NOW) throw StopNowException()
    }

    private fun setStopButtonVisible(visible: Boolean) = runOnUiThread {
        ui.stopVisible = visible
        ui.stopEnabled = true
        ui.stopLabel = "Stop"
    }

    private fun confirmStop() {
        if (stopMode == StopMode.NOW) return
        MaterialAlertDialogBuilder(this)
            .setTitle("Stop?")
            .setMessage("Anything already saved is kept either way.")
            .setPositiveButton("Stop now") { _, _ ->
                stopMode = StopMode.NOW
                ui.stopEnabled = false
                ui.stopLabel = "Stopping…"
                log("\nStopping now…")
            }
            .setNeutralButton("After this item") { _, _ ->
                stopMode = StopMode.AFTER_CURRENT
                ui.stopLabel = "Stopping after this item (tap to stop now)"
                log("\nWill stop after the current item finishes.")
            }
            .setNegativeButton("Keep going", null)
            .show()
    }

    /** Jailbreak fallback: zip Payload/<App>.app + iTunesMetadata.plist + iTunesArtwork straight from the filesystem. */
    // The bundle copied this way is the installed, still FairPlay-encrypted binary, exactly like an Archive;
    // it just doesn't depend on installation_proxy (useful where Archive fails, e.g. iOS 9+).
    private fun backupIpaViaAfc2(afc2: AfcClient, saver: IpaSaver, app: InstallationProxy.App) {
        val path = app.path ?: throw IOException("installation_proxy didn't report the app's path")
        val container = app.container ?: path.substringBeforeLast('/')
        val bundleDir = path.substringAfterLast('/')
        val target = saver.create(IpaSaver.ipaName(app.name, app.version, app.bundleId))
        try {
            val zos = AfcZipper.open(target.stream)
            var nextLog = 5L shl 20
            val z = AfcZipper(afc2, zos) { _, bytes ->
                if (bytes >= nextLog) { log("  zipping… ${mb(bytes)}"); nextLog = bytes + (5L shl 20) }
            }
            z.addTree(path, "Payload/$bundleDir/")
            z.addFileIfExists("$container/iTunesMetadata.plist", "iTunesMetadata.plist")
            z.addFileIfExists("$container/iTunesArtwork", "iTunesArtwork")
            zos.finish()
            target.commit()
            if (z.skippedLinks.isNotEmpty()) log("  (skipped ${z.skippedLinks.size} symlinks)")
            log("  saved ${target.location} (${z.files} files, ${mb(z.bytes)} uncompressed)")
        } catch (e: Exception) {
            target.discard()
            throw e
        }
    }

    /** house_arrest: zip the app's Documents/ and Library/ (minus Caches). Returns false if there's no data. */
    private fun backupData(mux: UsbMux, lockdown: DeviceSession, saver: IpaSaver, app: InstallationProxy.App): Boolean =
        withService(mux, lockdown, HouseArrest.SERVICE) { stream ->
            HouseArrest.vendContainer(stream, app.bundleId)
            val afc = AfcClient(stream).apply { cancelCheck = ::checkStopNow }
            val wanted = setOf("Documents", "Library")
            if (afc.readDir("/").none { it in wanted }) {
                log("  no app data")
                return@withService false
            }
            val target = saver.create(IpaSaver.dataName(app.name, app.version, app.bundleId))
            try {
                val zos = AfcZipper.open(target.stream)
                val z = AfcZipper(afc, zos)
                z.exclude = { it == "Library/Caches" }
                z.addTree("/", "") { it in wanted }
                zos.finish()
                target.commit()
                log("  data saved ${target.location} (${z.files} files, ${mb(z.bytes)})")
                true
            } catch (e: Exception) {
                target.discard()
                throw e
            }
        }

    // ---------------------------------------------------------------- file browser

    private data class BrowserItem(val path: String, val isDir: Boolean, val size: Long)

    private sealed class BrowserCmd {
        data class Open(val path: String) : BrowserCmd()
        /** Measure any folders in [items], then ask the user to confirm saving them all. */
        data class Prepare(val items: List<BrowserItem>, val returnTo: String) : BrowserCmd()
        data class Download(val items: List<BrowserItem>, val returnTo: String) : BrowserCmd()
        /** Copy files picked on the phone into [dir] on the device. */
        data class Upload(val uris: List<android.net.Uri>, val dir: String) : BrowserCmd()
        object Close : BrowserCmd()
    }

    private data class BrowserEntry(val name: String, val entry: AfcClient.Entry?)

    @Volatile private var measureCancelled = false
    private var measureDialog: AlertDialog? = null
    private var measureText: TextView? = null

    /** Root filesystem via AFC2 when jailbroken, else the media folder via regular AFC. */
    private fun browseFiles(device: UsbDevice) {
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
    private fun prepareSave(afc: AfcClient, cmd: BrowserCmd.Prepare, cmds: LinkedBlockingQueue<BrowserCmd>) {
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

    private fun saveSummary(files: List<BrowserItem>, dirs: List<BrowserItem>, folders: TreeSize): String = buildString {
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

    private fun confirmSave(msg: String, cmd: BrowserCmd.Prepare, cmds: LinkedBlockingQueue<BrowserCmd>) {
        var handled = false
        MaterialAlertDialogBuilder(this)
            .setMessage(msg)
            .setPositiveButton("Save") { _, _ -> handled = true; cmds.offer(BrowserCmd.Download(cmd.items, cmd.returnTo)) }
            .setNegativeButton("Back") { _, _ -> handled = true; cmds.offer(BrowserCmd.Open(cmd.returnTo)) }
            .setOnDismissListener { if (!handled) { handled = true; cmds.offer(BrowserCmd.Open(cmd.returnTo)) } }
            .show()
    }

    private fun showMeasuring() = runOnUiThread {
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

    private fun updateMeasuring(files: Int, bytes: Long) = runOnUiThread {
        measureText?.text = "Counting files…\n$files files, ${humanSize(bytes)} so far"
    }

    private fun dismissMeasuring() = runOnUiThread {
        measureDialog?.dismiss()
        measureDialog = null
        measureText = null
    }

    /**
     * Folder view: tap opens folders / saves a file; hold starts multi-select (tap then toggles).
     * The current path is shown as selectable text. Back goes up a folder (or clears the selection).
     */
    private fun showBrowser(label: String, path: String, entries: List<BrowserEntry>, cmds: LinkedBlockingQueue<BrowserCmd>) {
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
                val tv = convertView as? TextView ?: TextView(this@MainActivity).apply {
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

    /** Browser waiting on the system file picker: its command queue and the folder to upload into. */
    private var pendingUpload: Pair<LinkedBlockingQueue<BrowserCmd>, String>? = null

    private val pickUploads = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val (cmds, dir) = pendingUpload ?: return@registerForActivityResult
        pendingUpload = null
        cmds.offer(if (uris.isNullOrEmpty()) BrowserCmd.Open(dir) else BrowserCmd.Upload(uris, dir))
    }

    /** Name and size of a picked document. */
    private fun docInfo(uri: android.net.Uri): Pair<String, Long> {
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
    private fun freeName(afc: AfcClient, dir: String, name: String): String {
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
    private fun uploadFiles(afc: AfcClient, cmd: BrowserCmd.Upload, viaAfc2: Boolean): Boolean {
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

    private fun zipBaseName(path: String) = IpaSaver.clean(path.trimEnd('/').substringAfterLast('/').ifEmpty { "root" })

    private fun downloadPath(afc: AfcClient, saver: IpaSaver, path: String, isDir: Boolean) {
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

    // ---------------------------------------------------------------- music browser

    private sealed class MusicCmd {
        object Show : MusicCmd()
        data class Copy(val tracks: List<MusicLibrary.Track>, val writeTags: Boolean) : MusicCmd()
        object Close : MusicCmd()
    }

    /** Track pids checked in the music list; kept while the browser is open. */
    private val musicSelected = HashSet<Long>()
    @Volatile private var musicOther = false
    private var musicQuery = ""

    /** Music, or (otherMedia) podcasts, audiobooks, iTunes U, synced videos and ringtones. */
    private fun browseMusic(device: UsbDevice, otherMedia: Boolean) {
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
    private fun loadMusicLibrary(afc: AfcClient, playlists: MutableList<Playlist>? = null): List<MusicLibrary.Track>? {
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
    private fun showMusic(tracks: List<MusicLibrary.Track>, cmds: LinkedBlockingQueue<MusicCmd>) {
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
                val tv = convertView as? TextView ?: TextView(this@MainActivity).apply {
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

    private fun confirmCopySongs(chosen: List<MusicLibrary.Track>, cmds: LinkedBlockingQueue<MusicCmd>) {
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
            addView(TextView(this@MainActivity).apply { text = msg; textSize = 15f })
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
    private fun copySongs(afc: AfcClient, tracks: List<MusicLibrary.Track>, writeTags: Boolean) {
        val saver = IpaSaver(this)
        val used = HashSet<String>()
        var ok = 0
        var failed = 0
        var done = 0
        stopMode = StopMode.NONE
        keepScreenOn(true)
        setStopButtonVisible(true)
        afc.cancelCheck = ::checkStopNow
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

    private fun tagsFor(t: MusicLibrary.Track) = AudioTagger.Tags(
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

    private fun showMusicMenu(device: UsbDevice) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Music")
            .setItems(arrayOf("Songs", "Playlists")) { _, which ->
                if (which == 0) browseMusic(device, otherMedia = false) else startPlaylists(device)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Reads the library and its playlists, then lets the user pick which to export as .m3u. */
    private fun startPlaylists(device: UsbDevice) {
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

    private fun showPlaylists(device: UsbDevice, tracks: List<MusicLibrary.Track>, playlists: List<Playlist>) {
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
            adapter = android.widget.ArrayAdapter(this@MainActivity, android.R.layout.simple_list_item_multiple_choice, labels)
            for (i in labels.indices) setItemChecked(i, true)
            setOnItemClickListener { _, _, i, _ -> checked[i] = isItemChecked(i) }
        }
        val pad = (16 * resources.displayMetrics.density).toInt()
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, 0, pad, 0)
            addView(TextView(this@MainActivity).apply {
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

    private fun exportPlaylists(device: UsbDevice, chosen: List<Playlist>, byId: Map<Long, MusicLibrary.Track>, copySongsToo: Boolean) {
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

    // ---------------------------------------------------------------- photos & videos

    private sealed class PhotoCmd {
        object Show : PhotoCmd()
        data class Preview(val m: CameraRoll.Media) : PhotoCmd()
        data class SavePreview(val m: CameraRoll.Media, val file: java.io.File) : PhotoCmd()
        data class Save(val items: List<CameraRoll.Media>) : PhotoCmd()
        object Close : PhotoCmd()
    }

    /** Thumbnails live on disk (cache/thumbs); this keeps the recently shown ones decoded. */
    private val thumbLru = object : android.util.LruCache<String, android.graphics.Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8 / 1024).toInt()
    ) {
        override fun sizeOf(key: String, value: android.graphics.Bitmap) = value.byteCount / 1024
    }
    /** Paths whose thumbnail has been made (or can't be). */
    private val thumbDone = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())
    private val photoSelected = HashSet<String>()
    @Volatile private var photoFirstVisible = 0
    @Volatile private var photoAdapter: BaseAdapter? = null
    @Volatile private var loadCancelled = false
    private var progressDialog: AlertDialog? = null
    private var progressText: TextView? = null

    private fun browsePhotos(device: UsbDevice) {
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

    private fun thumbFile(m: CameraRoll.Media) =
        java.io.File(java.io.File(cacheDir, "thumbs"), "%08x_%x_%x.jpg".format(m.path.hashCode(), m.size, m.mtimeMs))

    private fun nextThumb(media: List<CameraRoll.Media>): CameraRoll.Media? {
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
    private fun makeThumb(afc: AfcClient, m: CameraRoll.Media): Boolean {
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

    private fun thumbBitmap(m: CameraRoll.Media): android.graphics.Bitmap? =
        thumbLru.get(m.path) ?: thumbFile(m).takeIf { it.exists() }
            ?.let { android.graphics.BitmapFactory.decodeFile(it.path) }
            ?.also { thumbLru.put(m.path, it) }

    private fun decodeSampled(bytes: ByteArray, minSide: Int): android.graphics.Bitmap? {
        val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        if (o.outWidth <= 0) return null
        var sample = 1
        while (minOf(o.outWidth, o.outHeight) / (sample * 2) >= minSide) sample *= 2
        return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
            android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun exifRotation(bytes: ByteArray): Int = runCatching {
        ExifThumb.rotationDegrees(
            android.media.ExifInterface(bytes.inputStream())
                .getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)
        )
    }.getOrDefault(0)

    private fun rotate(b: android.graphics.Bitmap, degrees: Int): android.graphics.Bitmap =
        if (degrees == 0) b else android.graphics.Bitmap.createBitmap(
            b, 0, 0, b.width, b.height, android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }, true
        )

    /** Grid cell that stays square. */
    private class SquareFrame(ctx: Context) : android.widget.FrameLayout(ctx) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) = super.onMeasure(widthMeasureSpec, widthMeasureSpec)
    }

    /** Thumbnail grid: tap to preview; hold to start selecting, then tap to check/uncheck. */
    private fun showPhotos(media: List<CameraRoll.Media>, cmds: LinkedBlockingQueue<PhotoCmd>) {
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
                val cell = convertView as? SquareFrame ?: SquareFrame(this@MainActivity).apply {
                    setBackgroundColor(0x22888888)
                    addView(android.widget.ImageView(this@MainActivity).apply {
                        scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                    }, android.widget.FrameLayout.LayoutParams(-1, -1))
                    addView(TextView(this@MainActivity).apply { // badge: video size / placeholder
                        textSize = 11f
                        setTextColor(0xFFFFFFFF.toInt())
                        setShadowLayer(3f, 0f, 0f, 0xFF000000.toInt())
                        setPadding(gap * 2, gap, gap * 2, gap)
                    }, android.widget.FrameLayout.LayoutParams(-2, -2, android.view.Gravity.BOTTOM or android.view.Gravity.START))
                    addView(TextView(this@MainActivity).apply { // selection check
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
    private fun previewMedia(afc: AfcClient, m: CameraRoll.Media, cmds: LinkedBlockingQueue<PhotoCmd>) {
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

    private fun showViewer(m: CameraRoll.Media, f: java.io.File, cmds: LinkedBlockingQueue<PhotoCmd>) {
        if (isFinishing) { cmds.offer(PhotoCmd.Close); return }
        val h = (resources.displayMetrics.heightPixels * 0.62).toInt()
        val content: View = if (m.isVideo) {
            android.widget.VideoView(this).apply {
                setVideoPath(f.path)
                val controller = android.widget.MediaController(this@MainActivity)
                controller.setAnchorView(this)
                setMediaController(controller)
                setOnPreparedListener { start() }
                setOnErrorListener { _, _, _ ->
                    Toast.makeText(this@MainActivity, "This phone can't play this video, but it can still be saved.", Toast.LENGTH_LONG).show()
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
    private fun decodeForViewer(f: java.io.File): android.graphics.Bitmap? {
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

    private fun confirmSaveMedia(chosen: List<CameraRoll.Media>, cmds: LinkedBlockingQueue<PhotoCmd>) {
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

    private fun saveMediaFile(m: CameraRoll.Media, f: java.io.File) {
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
    private fun saveMedia(afc: AfcClient, items: List<CameraRoll.Media>) {
        val saver = IpaSaver(this)
        val used = HashSet<String>()
        var ok = 0
        var failed = 0
        var done = 0
        stopMode = StopMode.NONE
        keepScreenOn(true)
        setStopButtonVisible(true)
        afc.cancelCheck = ::checkStopNow
        log("\nSaving ${items.size} items to Download/${IpaSaver.FOLDER}/$PHOTOS_SUBFOLDER/")
        try {
            for ((i, m) in items.withIndex()) {
                if (stopMode != StopMode.NONE) break
                val name = MusicLibrary.uniqueName(m.name, used)
                if (m.size > (5L shl 20) || i % 10 == 0 || i == items.size - 1) log("[${i + 1}/${items.size}] $name (${humanSize(m.size)})")
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

    private fun showProgress(title: String, onCancel: () -> Unit) = runOnUiThread {
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

    private fun updateProgress(text: String) = runOnUiThread { progressText?.text = text }

    private fun dismissProgress() = runOnUiThread {
        progressDialog?.dismiss()
        progressDialog = null
        progressText = null
    }

    // ---------------------------------------------------------------- generic pick-and-save list

    /** One row in a pick-and-save list. */
    private class Pick(val key: String, val title: String, val subtitle: String, val size: Long)

    private sealed class PickCmd {
        object Show : PickCmd()
        data class Save(val keys: List<String>) : PickCmd()
        object Close : PickCmd()
    }

    /**
     * Shared flow for Voice Memos and Books: load items on the worker thread, show a checklist,
     * confirm with size / free space, then save the selection with Stop support.
     */
    private fun <T> runPicker(
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

    private fun showChecklist(
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
                val tv = convertView as? TextView ?: TextView(this@MainActivity).apply {
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

    private fun confirmPicks(what: String, subfolder: String, chosen: List<Pick>, note: String?, cmds: LinkedBlockingQueue<PickCmd>) {
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

    private fun <T> savePicks(
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
        afc.cancelCheck = ::checkStopNow
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
    private fun copySqlite(afc: AfcClient, devicePath: String, tag: String): java.io.File? {
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

    private fun <R> withSqlite(file: java.io.File, block: (us.dannyhand.ipabackup.music.SqlDb) -> R): R =
        android.database.sqlite.SQLiteDatabase.openDatabase(
            file.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE, // our private copy
        ).use { block(AndroidSqlDb(it)) }

    private fun shortDate(ms: Long): String =
        java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(ms))

    /** Saves [devicePath] as [name]; stages through cache and writes tags when [tags] is given (m4a/mp3). */
    private fun saveAudio(afc: AfcClient, saver: IpaSaver, devicePath: String, name: String, sub: String, dateMs: Long?, tags: AudioTagger.Tags?): String {
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

    private fun browseVoiceMemos(device: UsbDevice) = runPicker(
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

    // ---------------------------------------------------------------- books

    private fun browseBooks(device: UsbDevice) = runPicker(
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

    // ---------------------------------------------------------------- contacts, notes, bookmarks

    private fun exportPersonalData(device: UsbDevice) {
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
            addView(TextView(this@MainActivity).apply {
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

    private fun runPersonalExport(device: UsbDevice, doContacts: Boolean, doNotes: Boolean, doBookmarks: Boolean, doCalendars: Boolean,
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

    private fun exportMessages(device: UsbDevice) {
        val pad = (20 * resources.displayMetrics.density).toInt()
        val attach = MaterialCheckBox(this).apply {
            text = "Include photos & attachments (adds a .zip with one page per conversation)"
            isChecked = prefs.getBoolean("msg_attachments", true)
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            addView(TextView(this@MainActivity).apply {
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

    private fun runMessagesExport(device: UsbDevice, includeAttachments: Boolean) {
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
                afc.cancelCheck = ::checkStopNow
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

    // ---------------------------------------------------------------- restore (install .ipa + app data)

    /** An .ipa to install: one this app saved, or one picked with the system file picker. */
    private class RestoreItem(val name: String, val uri: android.net.Uri, val size: Long)

    /** Waiting for the system file picker; resumed in [handlePickedIpas]. */
    private var pendingRestoreDevice: UsbDevice? = null

    private val pickIpas = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        handlePickedIpas(uris)
    }

    private fun bundleIdFromName(name: String) = Regex("\\(([^()]+)\\)(?: data\\.zip|\\.ipa)$").find(name)?.groupValues?.get(1)

    private fun showRestorePicker(device: UsbDevice) {
        val saver = IpaSaver(this)
        val ipas = saver.listSaved(".ipa").sortedBy { it.name.lowercase() }
        // Newest data backup per app.
        val dataZips = saver.listSaved(" data.zip").filter { bundleIdFromName(it.name) != null }
            .groupBy { bundleIdFromName(it.name)!! }.values.map { g -> g.maxByOrNull { it.modifiedMs }!! }
            .sortedBy { it.name.lowercase() }
        val dataIds = dataZips.mapNotNull { bundleIdFromName(it.name) }.toSet()
        val density = resources.displayMetrics.density
        val pad = (16 * density).toInt()
        val shortDay = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)

        val ipaLabels = ipas.map { s ->
            s.name.removeSuffix(".ipa") + "  (${humanSize(s.size)})" + if (bundleIdFromName(s.name) in dataIds) "  + data" else ""
        }
        val dataLabels = dataZips.map { s ->
            s.name.removeSuffix(" data.zip") + "  (${humanSize(s.size)}, ${shortDay.format(java.util.Date(s.modifiedMs))})"
        }

        var dataOnly = prefs.getBoolean("restore_data_only", false)
        val modes = android.widget.RadioGroup(this).apply { orientation = android.widget.RadioGroup.VERTICAL }
        val installMode = com.google.android.material.radiobutton.MaterialRadioButton(this).apply { text = "Install apps (.ipa)"; id = View.generateViewId() }
        val dataMode = com.google.android.material.radiobutton.MaterialRadioButton(this).apply { text = "Restore app data only (apps already on the device)"; id = View.generateViewId() }
        modes.addView(installMode)
        modes.addView(dataMode)
        val hint = TextView(this).apply { textSize = 13f; alpha = 0.8f; setPadding(0, pad / 3, 0, pad / 3) }
        val dataBox = MaterialCheckBox(this).apply {
            text = "Also restore app data (saves, settings) from a matching data .zip backup. " +
                "This replaces the saves currently on the device."
            isChecked = prefs.getBoolean("restore_data", false)
        }
        val list = ListView(this).apply {
            choiceMode = ListView.CHOICE_MODE_MULTIPLE
            isFastScrollEnabled = true
        }
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            addView(modes)
            addView(hint)
            addView(dataBox)
            addView(list, LinearLayout.LayoutParams(-1, (resources.displayMetrics.heightPixels * 0.42).toInt()))
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Restore to the device")
            .setView(view)
            .setPositiveButton("Restore selected", null)
            .setNeutralButton("Choose file…", null)
            .setNegativeButton("Cancel", null)
            .create()

        fun applyMode() {
            list.clearChoices()
            list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, if (dataOnly) dataLabels else ipaLabels)
            dataBox.visibility = if (dataOnly) View.GONE else View.VISIBLE
            hint.text = when {
                dataOnly && dataZips.isEmpty() -> "No app data backups yet. Back up apps with \"Also back up app data\" checked first."
                dataOnly -> "Writes the saved Documents/ and Library/ back into apps that are already installed, without " +
                    "reinstalling them. Close the app on the device first (double-press Home and remove it), or it may " +
                    "overwrite the restored saves when it quits."
                ipas.isEmpty() -> "No .ipa backups in Download/${IpaSaver.FOLDER}/ yet. Use \"Choose file…\" to pick one."
                else -> "Apps backed up to Download/${IpaSaver.FOLDER}/:"
            }
            dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL)?.visibility = if (dataOnly) View.GONE else View.VISIBLE
        }
        modes.check(if (dataOnly) dataMode.id else installMode.id)
        modes.setOnCheckedChangeListener { _, checkedId ->
            dataOnly = checkedId == dataMode.id
            applyMode()
        }
        fun remember() = prefs.edit().putBoolean("restore_data", dataBox.isChecked).putBoolean("restore_data_only", dataOnly).apply()

        dialog.setOnShowListener {
            applyMode()
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                remember()
                val checked = list.checkedItemPositions
                if (dataOnly) {
                    val chosen = dataZips.filterIndexed { i, _ -> checked.get(i) }
                    if (chosen.isEmpty()) { Toast.makeText(this, "Nothing selected", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                    dialog.dismiss()
                    confirmRestoreDataOnly(device, chosen)
                } else {
                    val chosen = ipas.filterIndexed { i, _ -> checked.get(i) }.map { RestoreItem(it.name, it.uri, it.size) }
                    if (chosen.isEmpty()) { Toast.makeText(this, "Nothing selected", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                    dialog.dismiss()
                    confirmRestore(device, chosen, dataBox.isChecked)
                }
            }
            dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
                remember()
                dialog.dismiss()
                pendingRestoreDevice = device
                pickIpas.launch(arrayOf("*/*"))
            }
        }
        dialog.show()
    }

    private fun confirmRestoreDataOnly(device: UsbDevice, zips: List<IpaSaver.Saved>) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Restore app data?")
            .setMessage("Write ${zips.size} data backup${if (zips.size == 1) "" else "s"} into the installed apps?\n\n" +
                "Saved files replace the matching files on the device; anything the backup doesn't have is left alone. " +
                "Apps that aren't installed are skipped.\n\nClose these apps on the device first.")
            .setPositiveButton("Restore") { _, _ -> runRestoreDataOnly(device, zips) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Writes a data backup into an installed app's sandbox (house_arrest). Returns files written. */
    private fun restoreDataZip(mux: UsbMux, lockdown: DeviceSession, bundleId: String, zip: IpaSaver.Saved): Int =
        withService(mux, lockdown, HouseArrest.SERVICE) { s ->
            HouseArrest.vendContainer(s, bundleId)
            val box = AfcClient(s).apply { cancelCheck = ::checkStopNow }
            contentResolver.openInputStream(zip.uri)?.use { box.unzipInto(it, "/", setOf("Documents", "Library")) }
                ?: throw IOException("Can't open ${zip.name}")
        }

    private fun runRestoreDataOnly(device: UsbDevice, zips: List<IpaSaver.Saved>) {
        if (busy) { log("Busy, please wait for the current operation to finish."); return }
        stopMode = StopMode.NONE
        keepScreenOn(true)
        setStopButtonVisible(true)
        log("\nRestoring app data for ${zips.size} app(s)…")
        runOnDevice(device, after = { setStopButtonVisible(false); stopMode = StopMode.NONE }) { mux, lockdown ->
            val installed = withService(mux, lockdown, InstallationProxy.SERVICE) { InstallationProxy(it).browseUserApps() }
                .associateBy { it.bundleId }
            var ok = 0
            var done = 0
            val skipped = ArrayList<String>()
            val failed = ArrayList<String>()
            for ((i, zip) in zips.withIndex()) {
                if (stopMode != StopMode.NONE) break
                val id = bundleIdFromName(zip.name)!!
                val label = zip.name.removeSuffix(" data.zip")
                log("\n[${i + 1}/${zips.size}] $label")
                val app = installed[id]
                if (app == null) {
                    log("  not installed on the device; use Install mode with \"Also restore app data\" instead")
                    skipped += label
                    done = i + 1
                    continue
                }
                if (IpaSaver.dataName(app.name, app.version, app.bundleId) != zip.name) {
                    log("  note: backup is from a different version than the installed ${app.version} (usually fine)")
                }
                try {
                    val n = restoreDataZip(mux, lockdown, id, zip)
                    log("  restored $n files ✓")
                    ok++
                } catch (e: StopNowException) {
                    log("  stopped partway; some files were already written")
                    break
                } catch (e: Exception) {
                    log("  FAILED: ${e.message}")
                    failed += label
                }
                done = i + 1
            }
            val stopped = stopMode != StopMode.NONE
            log((if (stopped) "\nStopped" else "\nDone") + ": $ok restored" +
                (if (skipped.isNotEmpty()) ", ${skipped.size} not installed" else "") +
                (if (stopped && done < zips.size) ", ${zips.size - done} not restored" else "") +
                (if (failed.isEmpty()) "." else "\nFailed: ${failed.joinToString()}"))
        }
    }

    private fun handlePickedIpas(uris: List<android.net.Uri>) {
        val device = pendingRestoreDevice ?: return
        pendingRestoreDevice = null
        if (uris.isEmpty()) return
        val items = uris.map { uri ->
            var name = uri.lastPathSegment?.substringAfterLast('/') ?: "app.ipa"
            var size = -1L
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(0)?.let { name = it }
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
            RestoreItem(name, uri, size)
        }
        val notIpa = items.filter { !it.name.endsWith(".ipa", true) }
        if (notIpa.isNotEmpty()) log("Skipping files that aren't .ipa: ${notIpa.joinToString { it.name }}")
        val ipas = items - notIpa.toSet()
        if (ipas.isNotEmpty()) confirmRestore(device, ipas, prefs.getBoolean("restore_data", false))
    }

    private fun confirmRestore(device: UsbDevice, items: List<RestoreItem>, restoreData: Boolean) {
        val total = items.sumOf { maxOf(it.size, 0) }
        val msg = buildString {
            append("Install ${items.size} app${if (items.size == 1) "" else "s"} (${humanSize(total)}) onto the device?\n\n")
            append("An app that's already installed is replaced by this version, keeping its data")
            append(if (restoreData) ", except that saves from a matching data backup are written over it." else ".")
            append("\n\nApp Store apps only install on a device signed into the Apple ID that bought them, ")
            append("or on a jailbroken device with AppSync.")
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Install apps?")
            .setMessage(msg)
            .setPositiveButton("Install") { _, _ -> runRestore(device, items, restoreData) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Turns installation_proxy error codes into something actionable. */
    private fun explainInstallError(message: String?): String {
        val m = message ?: "unknown error"
        return m + when {
            "ApplicationVerificationFailed" in m -> " (the device won't accept this app's signature/DRM: it must be signed into " +
                "the Apple ID that bought it, or be jailbroken with AppSync)"
            "DeviceOSVersionTooLow" in m -> " (the app needs a newer iOS)"
            "PackageExtractionFailed" in m || "PackageInspectionFailed" in m -> " (the .ipa looks damaged or isn't an app)"
            "InstallProhibited" in m -> " (installs are restricted on the device: check Settings → General → Restrictions)"
            else -> ""
        }
    }

    /**
     * Uploads each .ipa to /PublicStaging over AFC, installs it with installation_proxy, removes the staged
     * copy, then (optionally) writes the matching data backup into the app's sandbox via house_arrest.
     */
    private fun runRestore(device: UsbDevice, items: List<RestoreItem>, restoreData: Boolean) {
        if (busy) { log("Busy, please wait for the current operation to finish."); return }
        val dataZips = if (restoreData) IpaSaver(this).listSaved(" data.zip") else emptyList()
        stopMode = StopMode.NONE
        keepScreenOn(true)
        setStopButtonVisible(true)
        log("\nInstalling ${items.size} app(s)…")
        runOnDevice(device, after = { setStopButtonVisible(false); stopMode = StopMode.NONE }) { mux, lockdown ->
            withService(mux, lockdown, AfcClient.SERVICE) { afcStream ->
                withService(mux, lockdown, InstallationProxy.SERVICE) { ipStream ->
                    val afc = AfcClient(afcStream).apply { cancelCheck = ::checkStopNow }
                    val ip = InstallationProxy(ipStream)
                    afc.makeDir("/PublicStaging")
                    var ok = 0
                    var datas = 0
                    var done = 0
                    val failed = ArrayList<String>()
                    for ((i, item) in items.withIndex()) {
                        if (stopMode != StopMode.NONE) break
                        try {
                            val info = contentResolver.openInputStream(item.uri)?.use { IpaInfo.read(it) { b -> Plist.parse(b) } }
                                ?: throw IOException("Can't open ${item.name}")
                            val label = listOfNotNull(info.name ?: item.name.removeSuffix(".ipa"), info.version).joinToString(" ")
                            log("\n[${i + 1}/${items.size}] $label" + (info.bundleId?.let { " ($it)" } ?: ""))
                            if (IpaInfo.needsNewerOs(info.minimumOs, lockdown.productVersion)) {
                                log("  needs iOS ${info.minimumOs}, but the device has iOS ${lockdown.productVersion}. Skipped.")
                                failed += label
                                done = i + 1
                                continue
                            }
                            val staged = "PublicStaging/" + IpaSaver.clean(info.bundleId ?: item.name.removeSuffix(".ipa")) + ".ipa"
                            try {
                                var lastDecile = -1L
                                contentResolver.openInputStream(item.uri)?.use { input ->
                                    afc.writeFile("/$staged", input.buffered(1 shl 16), item.size) { w, t ->
                                        val decile = if (t > 0) w * 10 / t else -1L
                                        if (decile > lastDecile && decile % 2 == 0L) { lastDecile = decile; log("  uploading ${mb(w)} / ${mb(t)}") }
                                    }
                                } ?: throw IOException("Can't open ${item.name}")
                                var lastPct = -1
                                // Not stoppable mid-install: interrupting installd could leave the app half-installed.
                                ip.install(staged, info.iTunesMetadata, info.sinf) { status, pct ->
                                    if (pct != null && pct / 25 > lastPct / 25) { lastPct = pct; log("  installing… $pct% ($status)") }
                                }
                                log("  installed ✓")
                                ok++
                            } finally {
                                runCatching { afc.remove("/$staged") }
                            }
                            if (restoreData) {
                                val id = info.bundleId
                                val dz = dataZips.filter { id != null && bundleIdFromName(it.name) == id }.maxByOrNull { it.modifiedMs }
                                if (dz == null) {
                                    log("  no data backup for this app")
                                } else {
                                    val n = restoreDataZip(mux, lockdown, id!!, dz)
                                    log("  restored $n data files from ${dz.name}")
                                    datas++
                                }
                            }
                        } catch (e: StopNowException) {
                            log("  stopped")
                            break
                        } catch (e: Exception) {
                            log("  FAILED: ${explainInstallError(e.message)}")
                            failed += item.name
                        }
                        done = i + 1
                    }
                    val stopped = stopMode != StopMode.NONE
                    log((if (stopped) "\nStopped" else "\nDone") + ": $ok installed" +
                        (if (restoreData) ", $datas data restores" else "") +
                        (if (stopped && done < items.size) ", ${items.size - done} not installed" else "") +
                        (if (failed.isEmpty()) "." else "\nFailed: ${failed.joinToString()}"))
                }
            }
        }
    }

    // ---------------------------------------------------------------- device tools

    private fun saveBytes(name: String, sub: String, bytes: ByteArray): String {
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

    private fun today() = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

    private fun monoText(text: String) = TextView(this).apply {
        val pad = (16 * resources.displayMetrics.density).toInt()
        setPadding(pad, pad / 2, pad, pad / 2)
        typeface = Typeface.MONOSPACE
        textSize = 11.5f
        setTextIsSelectable(true)
        this.text = text
    }

    /** Model, iOS, serial numbers, storage, battery health and jailbreak status, shown and saveable as text. */
    private fun showDeviceInfo(device: UsbDevice) {
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

    private fun showReport(report: String, owner: String) {
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
    private fun showPower(device: UsbDevice) {
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
    private fun showSyslog(device: UsbDevice) {
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
    private fun browseCrashReports(device: UsbDevice) {
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

    private var pendingLayoutDevice: UsbDevice? = null
    private val pickLayout = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        val device = pendingLayoutDevice ?: return@registerForActivityResult
        pendingLayoutDevice = null
        if (uri != null) confirmRestoreLayout(device, uri)
    }

    private fun showHomeScreen(device: UsbDevice) {
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

    private fun saveLayout(device: UsbDevice) {
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

    private fun confirmRestoreLayout(device: UsbDevice, uri: android.net.Uri) {
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
    private fun saveWallpapers(device: UsbDevice) {
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

    // ---------------------------------------------------------------- uninstall

    private fun startUninstall(device: UsbDevice) {
        log("\nLoading installed apps…")
        var apps: List<InstallationProxy.App> = emptyList()
        runOnDevice(device, after = { runOnUiThread { showUninstallPicker(device, apps) } }) { mux, lockdown ->
            apps = withService(mux, lockdown, InstallationProxy.SERVICE) { InstallationProxy(it).browseUserApps() }
            log("${apps.size} user apps installed.")
        }
    }

    private fun showUninstallPicker(device: UsbDevice, apps: List<InstallationProxy.App>) {
        if (isFinishing || apps.isEmpty()) return
        val saved = IpaSaver(this).existingNames()
        val isSaved = apps.map { IpaSaver.ipaName(it.name, it.version, it.bundleId) in saved }
        val labels = apps.mapIndexed { i, a -> (if (isSaved[i]) "✓ " else "") + "${a.name} ${a.version}${sizeLabel(a.sizeBytes)}" }
        val checked = BooleanArray(apps.size)
        MaterialAlertDialogBuilder(this)
            .setTitle("Uninstall which apps? (✓ = backed up)")
            .setMultiChoiceItems(labels.toTypedArray(), checked) { _, i, on -> checked[i] = on }
            .setPositiveButton("Uninstall") { _, _ ->
                val chosen = apps.filterIndexed { i, _ -> checked[i] }
                if (chosen.isEmpty()) return@setPositiveButton
                val notSaved = chosen.filterIndexed { _, a -> !isSaved[apps.indexOf(a)] }
                MaterialAlertDialogBuilder(this)
                    .setTitle("Uninstall ${chosen.size} app${if (chosen.size == 1) "" else "s"}?")
                    .setMessage("Deletes ${chosen.joinToString { it.name }} and all their data (saves, settings) from the device." +
                        if (notSaved.isEmpty()) "" else "\n\n⚠ Not backed up: ${notSaved.joinToString { it.name }}. " +
                            "Apps no longer in the App Store can't be downloaded again.")
                    .setPositiveButton("Uninstall") { _, _ -> runUninstall(device, chosen) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun runUninstall(device: UsbDevice, apps: List<InstallationProxy.App>) {
        log("\nUninstalling ${apps.size} app(s)…")
        runOnDevice(device) { mux, lockdown ->
            withService(mux, lockdown, InstallationProxy.SERVICE) { s ->
                val ip = InstallationProxy(s)
                var ok = 0
                for ((i, a) in apps.withIndex()) {
                    try {
                        ip.uninstall(a.bundleId)
                        ok++
                        log("[${i + 1}/${apps.size}] ${a.name} ✓")
                    } catch (e: Exception) {
                        log("[${i + 1}/${apps.size}] ${a.name} FAILED: ${e.message}")
                    }
                }
                log("Done: $ok uninstalled.")
            }
        }
    }

    // ---------------------------------------------------------------- auto-backup

    /** On plug-in (when enabled): back up apps that aren't saved yet, without asking. */
    private fun autoBackup(device: UsbDevice) {
        log("\nAuto-backup: checking for new apps…")
        var todo: List<InstallationProxy.App> = emptyList()
        val saved = IpaSaver(this).existingNames()
        runOnDevice(device, after = {
            runOnUiThread {
                if (todo.isNotEmpty()) backup(device, todo, skipSaved = true, includeData = prefs.getBoolean("include_data", false), savedNames = saved)
            }
        }) { mux, lockdown ->
            val apps = withService(mux, lockdown, InstallationProxy.SERVICE) { InstallationProxy(it).browseUserApps() }
            todo = apps.filter { IpaSaver.ipaName(it.name, it.version, it.bundleId) !in saved }
            log(if (todo.isEmpty()) "All ${apps.size} apps are already backed up." else "${todo.size} new or updated app(s) to back up.")
        }
    }

    // ---------------------------------------------------------------- Cydia

    private fun exportCydia(device: UsbDevice) {
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

    // ---------------------------------------------------------------- root certificates

    private fun loadRoots(): List<RootProfile.Root> =
        assets.list("roots").orEmpty().sorted().map { f -> RootProfile.Root(f.removeSuffix(".cer"), assets.open("roots/$f").use { it.readBytes() }) }

    /**
     * Fixes "The certificate for this server is invalid" in Safari on old iOS by installing a profile of
     * current root certificates (see RootProfile). Checks the device clock first: a wrong date (an iPod
     * whose battery ran flat resets to 1970) makes every certificate look not-yet-valid, with the same error.
     */
    private fun startCertificates() {
        if (busy) { log("Busy, please wait for the current operation to finish."); return }
        val connected = usbManager.deviceList.values.any { AppleUsbTransport.isApple(it) && AppleUsbTransport.findMuxInterface(it) != null }
        if (!connected) { showCertificates(null, null, null); return }
        withDevice { device ->
            log("\nChecking the device clock and installed profiles…")
            var time: Long? = null
            var installed: Boolean? = null
            runOnDevice(device, after = { runOnUiThread { showCertificates(device, time, installed) } }) { mux, lockdown ->
                time = lockdown.deviceTime()
                installed = runCatching {
                    withService(mux, lockdown, McInstall.SERVICE) { McInstall(it).installedIdentifiers() }
                }.getOrNull()?.contains(RootProfile.IDENTIFIER)
            }
        }
    }

    private fun showCertificates(device: UsbDevice?, deviceTime: Long?, installed: Boolean?) {
        val roots = runCatching { loadRoots() }.getOrElse { log("Couldn't load the certificates: ${it.message}"); return }
        val profile = RootProfile.build(roots)
        val fmt = java.text.SimpleDateFormat("MMM d, yyyy h:mm a", java.util.Locale.US)
        val now = System.currentTimeMillis() / 1000
        val msg = buildString {
            if (deviceTime != null) {
                val skew = deviceTime - now
                log("Device clock: ${fmt.format(java.util.Date(deviceTime * 1000))}")
                if (kotlin.math.abs(skew) > 2 * 86400) {
                    append("⚠ The device's date is ${fmt.format(java.util.Date(deviceTime * 1000))}. With a wrong date, every ")
                    append("certificate looks invalid, whatever is installed. Fix it first in Settings › General › ")
                    append("Date & Time (turn on Set Automatically, or set it by hand).\n\n")
                }
            }
            if (installed == true) append("These certificates are already installed on the device. Installing again replaces them.\n\n")
            append("Installs a profile with ${roots.size} current root certificates, so Safari and apps can open sites ")
            append("that use certificate authorities newer than iOS 6 (Let's Encrypt and others):\n\n")
            append(roots.joinToString("\n") { "• " + it.commonName })
            append("\n\nYou'll tap Install on the device to accept it. It can be removed later in Settings › General › Profiles.")
            if (device == null) append("\n\nNo device connected over USB, so it can only be shared over Wi-Fi.")
        }
        val b = MaterialAlertDialogBuilder(this)
            .setTitle("Root certificates")
            .setMessage(msg)
            .setNeutralButton("Share over Wi-Fi") { _, _ -> shareProfileOverWifi(profile) }
            .setNegativeButton("Cancel", null)
        if (device != null) b.setPositiveButton("Install over USB") { _, _ -> installProfileOverUsb(device, profile) }
        b.show()
    }

    private fun installProfileOverUsb(device: UsbDevice, profile: ByteArray) {
        log("\nSending the certificate profile to the device…")
        var failed: String? = null
        runOnDevice(device, after = {
            failed?.let { err ->
                runOnUiThread {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Couldn't install over USB")
                        .setMessage("$err\n\nYou can install it from Safari on the device over Wi-Fi instead.")
                        .setPositiveButton("Share over Wi-Fi") { _, _ -> shareProfileOverWifi(profile) }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
        }) { mux, lockdown ->
            try {
                withService(mux, lockdown, McInstall.SERVICE) { McInstall(it).installProfile(profile) }
                log("Sent ✓ On the device, tap Install (and Install again to confirm). If no screen appears, " +
                    "unlock it and look in Settings › General › Profiles.")
            } catch (e: Exception) {
                failed = e.message ?: e.toString()
                log("Install over USB failed: $failed")
            }
        }
    }

    private var fileServer: FileServer? = null

    private fun shareProfileOverWifi(profile: ByteArray) = shareOverWifi(
        "Updated root certificates for old iOS",
        listOf(FileServer.File("roots.mobileconfig", "application/x-apple-aspen-config", profile, "Install profile")),
        "Tap Install profile, then Install.",
    )

    /** Serves [files] over plain HTTP until the dialog is closed; the device opens the shown address in Safari. */
    private fun shareOverWifi(title: String, files: List<FileServer.File>, howTo: String) {
        fileServer?.stop()
        val server = FileServer(title, files) { line -> log("  request from $line") }
        try { server.start() } catch (e: Exception) { log("Couldn't start the Wi-Fi server: ${e.message}"); return }
        fileServer = server
        val addrs = FileServer.localAddresses()
        if (addrs.isEmpty()) {
            server.stop()
            log("No Wi-Fi network found on this phone.")
            MaterialAlertDialogBuilder(this).setTitle("No Wi-Fi").setMessage(
                "Connect this phone and the device to the same Wi-Fi network (or turn on this phone's hotspot and " +
                "join it from the device), then try again.").setPositiveButton("OK", null).show()
            return
        }
        val urls = addrs.map { "http://$it:${server.port}/" }
        log("\nSharing over Wi-Fi at ${urls.joinToString(" or ")}")
        val text = TextView(this).apply {
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
            setTextIsSelectable(true)
            textSize = 15f
            text = "On the device, open Safari and go to:\n\n" + urls.joinToString("\n") +
                "\n\n$howTo\n\nBoth need to be on the same Wi-Fi (this phone's hotspot works too). Keep this open until you're done."
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Share over Wi-Fi")
            .setView(text)
            .setCancelable(false)
            .setPositiveButton("Done") { _, _ -> server.stop(); fileServer = null; log("Stopped sharing.") }
            .show()
    }

    // ---------------------------------------------------------------- add books

    /** Books works without a device too (adding over Wi-Fi). */
    private fun startBooks() {
        if (busy) { log("Busy, please wait for the current operation to finish."); return }
        val connected = usbManager.deviceList.values.any { AppleUsbTransport.isApple(it) && AppleUsbTransport.findMuxInterface(it) != null }
        if (connected) withDevice { showBooksMenu(it) } else showBooksMenu(null)
    }

    private var pendingBooksDevice: UsbDevice? = null
    private val pickBooks = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val device = pendingBooksDevice
        pendingBooksDevice = null
        if (!uris.isNullOrEmpty()) prepareBooks(device, uris)
    }

    private fun showBooksMenu(device: UsbDevice?) {
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

    private class NewBook(val fileName: String, val bytes: ByteArray, val info: BookImport.Info)

    private fun prepareBooks(device: UsbDevice?, uris: List<android.net.Uri>) {
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

    private fun addBooksOverUsb(device: UsbDevice, books: List<NewBook>) {
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
    private fun startMusicLibraryBackup(device: UsbDevice) {
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

    private fun showMusicLibraryDialog(device: UsbDevice, found: List<String>, libSize: Long, artSize: Long) {
        val pad = (20 * resources.displayMetrics.density).toInt()
        val art = MaterialCheckBox(this).apply {
            text = "Include artwork (${humanSize(artSize)})"
            isChecked = prefs.getBoolean("musiclib_artwork", true)
            isEnabled = artSize > 0
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            addView(TextView(this@MainActivity).apply {
                text = (if (found.isEmpty()) "No music library database found." else "Found:\n" + found.joinToString("\n") { "• $it" }) +
                    "\n\nLibrary folder: ${humanSize(libSize)}. Only the database is saved, not the songs themselves." +
                    "\n\nSaved to Download/IPA Backup/$MUSICLIB_SUBFOLDER."
                textSize = 14f
            })
            addView(art)
            if (found.any { it.startsWith("MediaLibrary") }) addView(com.google.android.material.button.MaterialButton(
                this@MainActivity, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
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
    private fun exportLibrarySample(device: UsbDevice) {
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
    private fun zipMusicLibrary(afc: AfcClient, owner: String, artwork: Boolean, label: String): String {
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

    private var pendingLibDevice: UsbDevice? = null
    private val pickLibZip = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        val device = pendingLibDevice ?: return@registerForActivityResult
        pendingLibDevice = null
        if (uri != null) confirmMusicLibraryRestore(device, uri)
    }

    private fun confirmMusicLibraryRestore(device: UsbDevice, uri: android.net.Uri) {
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
            addView(TextView(this@MainActivity).apply {
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

    private fun runMusicLibraryRestore(device: UsbDevice, uri: android.net.Uri, zipNames: List<String>, restart: Boolean) {
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

    // ---------------------------------------------------------------- clean up

    /** A leftover on the device: name and size. */
    private class Leftover(val name: String, val size: Long)

    /**
     * Finds what an interrupted run can leave behind (e.g. the OTG cable coming out mid-copy):
     *  - device: .zip files in ApplicationArchives (made by Archive, normally deleted right after copying) and
     *    PublicStaging contents (an .ipa uploaded by Restore, normally deleted after installing)
     *  - phone: unfinished (still pending) saves, and temporary working files in the app cache
     * Without a device connected, only the phone side is checked.
     */
    private fun startCleanup() {
        if (busy) { log("Busy, please wait for the current operation to finish."); return }
        val connected = usbManager.deviceList.values.any { AppleUsbTransport.isApple(it) && AppleUsbTransport.findMuxInterface(it) != null }
        if (!connected) {
            log("\nNo device connected: checking this phone only.")
            showCleanup(null, emptyList(), emptyList(), emptyList())
            return
        }
        withDevice { device ->
            log("\nLooking for leftovers on the device…")
            var archives = emptyList<Leftover>()
            var staged = emptyList<Leftover>()
            var orphanBooks = emptyList<Leftover>()
            var partial = emptyList<Leftover>()
            runOnDevice(device, after = { runOnUiThread { showCleanup(device, archives, staged + orphanBooks, partial) } }) { mux, lockdown ->
                withService(mux, lockdown, AfcClient.SERVICE) { s ->
                    val afc = AfcClient(s)
                    fun scan(dir: String, filter: (String) -> Boolean) =
                        runCatching { afc.readDir(dir) }.getOrDefault(emptyList()).filter(filter).map { n ->
                            val path = "$dir/$n"
                            Leftover(n, runCatching {
                                val e = afc.stat(path)
                                if (e.isDir) afc.measureTree(path).bytes else e.size
                            }.getOrDefault(0L))
                        }
                    archives = scan("/ApplicationArchives") { it.endsWith(".zip") }
                    staged = scan("/PublicStaging") { true }
                    // Book copies cut off before their Books.plist entry was written (Books › Add over USB).
                    val listed = runCatching {
                        BookImport.entries(Plist.parse(java.io.ByteArrayOutputStream().also { afc.copyFile(BookImport.PLIST, it) }.toByteArray()))
                            .mapNotNull { (it["Path"] as? String)?.substringAfterLast('/') }.toSet()
                    }.getOrNull()
                    if (listed != null) orphanBooks = scan("/Books") { it.matches(BOOK_COPY) && it !in listed }
                        .map { Leftover("../Books/" + it.name, it.size) }
                    // Library files from a music library restore that was cut off before the swap.
                    orphanBooks = orphanBooks + listOf(LIB_DIR, "$LIB_DIR/iTunes Library.itlp", "$ART_DIR").flatMap { dir ->
                        scan(dir) { it.endsWith(AfcSafeRestore.NEW_SUFFIX) }.map { Leftover("..$dir/" + it.name, it.size) }
                    }
                }
                // An upload cut off mid-copy. It may have been written through AFC2 (whole filesystem).
                prefs.getString(PREF_PARTIAL_UPLOAD, null)?.let { saved ->
                    val path = saved.substringAfter(':')
                    val afc2 = if (saved.startsWith("afc2:")) tryAfc2(mux, lockdown) else null
                    val found = if (afc2 != null) afc2.use { s -> runCatching { AfcClient(s).stat(path).size }.getOrNull() }
                        else withService(mux, lockdown, AfcClient.SERVICE) { s -> runCatching { AfcClient(s).stat(path).size }.getOrNull() }
                    if (found != null) partial = listOf(Leftover(saved, found)) else prefs.edit().remove(PREF_PARTIAL_UPLOAD).apply()
                }
            }
        }
    }

    /** Temporary working files (database copies, previews, staging). Thumbnails are a useful cache, so they stay. */
    private fun tempFiles(): List<java.io.File> = cacheDir.listFiles()?.filter { it.name != "thumbs" } ?: emptyList()

    private fun dirSize(f: java.io.File): Long = if (f.isDirectory) f.listFiles()?.sumOf { dirSize(it) } ?: 0L else f.length()

    private fun showCleanup(device: UsbDevice?, archives: List<Leftover>, staged: List<Leftover>, partial: List<Leftover>) {
        val saver = IpaSaver(this)
        val pending = saver.listPending()
        val temp = tempFiles()
        val tempSize = temp.sumOf { dirSize(it) }
        val pad = (20 * resources.displayMetrics.density).toInt()

        class Group(val box: MaterialCheckBox, val detail: String)
        fun group(title: String, count: Int, size: Long, detail: String) =
            Group(MaterialCheckBox(this).apply { text = "$title: $count (${humanSize(size)})"; isChecked = true }, detail)

        val groups = LinkedHashMap<String, Group>()
        if (archives.isNotEmpty()) groups["archives"] = group("Leftover app archives on the device", archives.size, archives.sumOf { it.size },
            archives.joinToString("\n") { "• " + it.name })
        if (staged.isNotEmpty()) groups["staged"] = group("Unfinished installs and copies on the device", staged.size, staged.sumOf { it.size },
            staged.joinToString("\n") { "• " + if (it.name.startsWith("../")) (if (it.name.startsWith("../Books/")) "Books/" + it.name.substringAfterLast('/') + " (not in iBooks' list)" else it.name.removePrefix("../")) else it.name })
        if (partial.isNotEmpty()) groups["partial"] = group("Partly uploaded file on the device", 1, partial[0].size, "• " + partial[0].name.substringAfter(':') +
            if (partial[0].name.startsWith("afc:")) "  (in the Media folder)" else "")
        if (pending.isNotEmpty()) groups["pending"] = group("Unfinished saves on this phone", pending.size, pending.sumOf { it.size },
            pending.joinToString("\n") { "• " + it.name })
        if (tempSize > 0) groups["temp"] = group("Temporary files on this phone", temp.size, tempSize,
            "Database copies, previews and staging files. Thumbnails are kept.")

        if (groups.isEmpty()) {
            log("Nothing to clean up" + if (device == null) " on this phone." else ".")
            MaterialAlertDialogBuilder(this)
                .setTitle("All clean")
                .setMessage(if (device == null) "Nothing left over on this phone. Connect the device to check it too."
                    else "Nothing left over on the device or this phone.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            if (device == null) addView(TextView(this@MainActivity).apply {
                text = "No device connected, so only this phone was checked."
                textSize = 13f
                alpha = 0.8f
            })
            for (g in groups.values) {
                addView(g.box)
                addView(TextView(this@MainActivity).apply {
                    text = g.detail
                    textSize = 12f
                    alpha = 0.7f
                    maxLines = 6
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(pad * 2, 0, 0, pad / 2)
                })
            }
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Clean up")
            .setView(android.widget.ScrollView(this).apply { addView(body) })
            .setPositiveButton("Delete selected") { _, _ ->
                fun on(key: String) = groups[key]?.box?.isChecked == true
                runCleanup(device, if (on("archives")) archives else emptyList(), if (on("staged")) staged else emptyList(),
                    if (on("partial")) partial else emptyList(),
                    if (on("pending")) pending else emptyList(), on("temp"))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun runCleanup(device: UsbDevice?, archives: List<Leftover>, staged: List<Leftover>, partial: List<Leftover>, pending: List<IpaSaver.Saved>, temp: Boolean) {
        // Phone side first: quick and needs no connection.
        if (pending.isNotEmpty()) {
            val saver = IpaSaver(this)
            val n = pending.count { saver.delete(it.uri) }
            log("Deleted $n unfinished save${if (n == 1) "" else "s"} on this phone.")
        }
        if (temp) {
            val freed = tempFiles().sumOf { f -> dirSize(f).also { f.deleteRecursively() } }
            log("Deleted temporary files (${humanSize(freed)}).")
        }
        if (device == null || (archives.isEmpty() && staged.isEmpty() && partial.isEmpty())) return
        log("\nCleaning up the device…")
        runOnDevice(device) { mux, lockdown ->
            for (p in partial) {
                val path = p.name.substringAfter(':')
                val afc2 = if (p.name.startsWith("afc2:")) tryAfc2(mux, lockdown) else null
                val ok = if (afc2 != null) afc2.use { s -> runCatching { AfcClient(s).remove(path) }.isSuccess }
                    else withService(mux, lockdown, AfcClient.SERVICE) { s -> runCatching { AfcClient(s).remove(path) }.isSuccess }
                if (ok) { prefs.edit().remove(PREF_PARTIAL_UPLOAD).apply(); log("Deleted the partly uploaded $path ✓") }
                else log("  couldn't delete $path")
            }
            withService(mux, lockdown, AfcClient.SERVICE) { s ->
                val afc = AfcClient(s)
                if (archives.isNotEmpty()) {
                    // Prefer installation_proxy's RemoveArchive (it owns ApplicationArchives); AFC delete as a fallback.
                    withService(mux, lockdown, InstallationProxy.SERVICE) { ipStream ->
                        val ip = InstallationProxy(ipStream)
                        var n = 0
                        for (a in archives) {
                            val ok = runCatching { ip.removeArchive(a.name.removeSuffix(".zip")) }.isSuccess ||
                                runCatching { afc.removeTree("/ApplicationArchives/${a.name}") }.isSuccess
                            if (ok) n++ else log("  couldn't delete ${a.name}")
                        }
                        log("Deleted $n leftover app archive${if (n == 1) "" else "s"} (${humanSize(archives.sumOf { it.size })}) ✓")
                    }
                }
                if (staged.isNotEmpty()) {
                    var n = 0
                    for (st in staged) {
                        if (runCatching { afc.removeTree(if (st.name.startsWith("../")) st.name.removePrefix("..") else "/PublicStaging/${st.name}") }.isSuccess) n++ else log("  couldn't delete ${st.name}")
                    }
                    log("Deleted $n unfinished install${if (n == 1) "" else "s"} (${humanSize(staged.sumOf { it.size })}) ✓")
                }
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun join(dir: String, name: String) = if (dir.endsWith("/")) dir + name else "$dir/$name"
    private fun parent(path: String) = path.trimEnd('/').substringBeforeLast('/').ifEmpty { "/" }

    private fun sizeLabel(bytes: Long) = if (bytes > 0) "  (${mb(bytes)})" else ""
    private fun mb(bytes: Long) = if (bytes < 0) "?" else "%.1f MB".format(bytes / 1048576.0)
    private fun humanSize(bytes: Long) = when {
        bytes < 0 -> "?"
        bytes < 1024 -> "$bytes B"
        bytes < 1048576 -> "%.1f KB".format(bytes / 1024.0)
        else -> mb(bytes)
    }

    private fun appVersion(): String = runCatching {
        "v" + packageManager.getPackageInfo(packageName, 0).versionName
    }.getOrDefault("")

    /** Saves the activity log to Download/IPA Backup/Logs/log-yyyy-MM-dd-HH-mm-ss.txt. */
    private fun saveLog() {
        val name = "log-" + java.text.SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", java.util.Locale.US).format(java.util.Date()) + ".txt"
        runCatching { saveBytes(name, LOGS_SUBFOLDER, (ui.log.joinToString("\n") + "\n").toByteArray()) }
            .onSuccess { Toast.makeText(this, "Saved to $it", Toast.LENGTH_SHORT).show() }
            .onFailure { log("Saving the log FAILED: ${it.message}") }
    }

    private fun copyLog() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("IPA Backup log", ui.log.joinToString("\n")))
        Toast.makeText(this, "Log copied", Toast.LENGTH_SHORT).show()
    }

    private fun keepScreenOn(on: Boolean) = runOnUiThread {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun log(msg: String) {
        if (msg.startsWith("\n")) lastHeadline = msg.trim().lineSequence().firstOrNull()
        runOnUiThread {
            msg.split('\n').forEach { ui.log.add(it) }
            if (ui.log.size > 5000) ui.log.removeRange(0, ui.log.size - 5000)
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.usbDevice(): UsbDevice? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        else getParcelableExtra(UsbManager.EXTRA_DEVICE)
}
