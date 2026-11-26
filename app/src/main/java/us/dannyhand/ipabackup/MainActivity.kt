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
import androidx.compose.material.icons.outlined.Movie
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

    /** Main-screen state (log, busy, Stop button, device) rendered by ui/MainScreen.kt. */
    internal val ui = UiState()
    /** Most recent operation heading (log lines that start a new block), shown as the status card title. */
    @Volatile internal var lastHeadline: String? = null
    internal val usbManager by lazy { getSystemService(Context.USB_SERVICE) as UsbManager }
    internal val prefs by lazy { getSharedPreferences("options", Context.MODE_PRIVATE) }
    internal var busy: Boolean
        get() = ui.busy
        set(value) { ui.busy = value }
    /** What to run once USB permission is granted. */
    internal var pendingAction: ((UsbDevice) -> Unit)? = null

    internal val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_USB_PERMISSION) return
            val device = intent.usbDevice()
            val action = pendingAction ?: this@MainActivity::probe
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
            Feature("Back up apps", Icons.Outlined.Apps, "Apps") { withDevice(this::probe) },
            Feature("Restore", Icons.Outlined.InstallMobile, "Apps") { withDevice(this::showRestorePicker) },
            Feature("Uninstall", Icons.Outlined.DeleteSweep, "Apps") { withDevice(this::startUninstall) },
            Feature("Music", Icons.Outlined.LibraryMusic, "Media") { startMedia(MediaView.MUSIC) },
            Feature("Photos", Icons.Outlined.PhotoLibrary, "Media") { withDevice(this::browsePhotos) },
            Feature("Synced Videos", Icons.Outlined.Movie, "Media") { startMedia(MediaView.VIDEOS) },
            Feature("Other Audio", Icons.Outlined.Podcasts, "Media") { startMedia(MediaView.AUDIO) },
            Feature("Voice Memos", Icons.Outlined.Mic, "Media") { withDevice(this::browseVoiceMemos) },
            Feature("Books", Icons.Outlined.MenuBook, "Media") { startBooks() },
            Feature("Messages", Icons.Outlined.Forum, "Personal data", needsJailbreak = true) { withDevice(this::exportMessages) },
            Feature("Contacts, Notes & more", Icons.Outlined.Contacts, "Personal data", needsJailbreak = true) { withDevice(this::exportPersonalData) },
            Feature("Cydia packages", Icons.Outlined.Extension, "Personal data", needsJailbreak = true) { withDevice(this::exportCydia) },
            Feature("Device info", Icons.Outlined.Info, "Device") { withDevice(this::showDeviceInfo) },
            Feature("Power", Icons.Outlined.PowerSettingsNew, "Device") { withDevice(this::showPower) },
            Feature("Home screen", Icons.Outlined.Dashboard, "Device") { withDevice(this::showHomeScreen) },
            Feature("System log", Icons.Outlined.Terminal, "Device") { withDevice(this::showSyslog) },
            Feature("Crash reports", Icons.Outlined.BugReport, "Device") { withDevice(this::browseCrashReports) },
            Feature("Files", Icons.Outlined.FolderOpen, "Tools") { withDevice(this::browseFiles) },
            Feature("Music library", Icons.Outlined.Storage, "Tools") { withDevice(this::startMusicLibraryBackup) },
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
                    onCopyLog = this::copyLog,
                    onSaveLog = this::saveLog,
                    onClearLog = { ui.log.clear() },
                    onStop = this::confirmStop,
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

    internal fun handleIntent(intent: Intent?) {
        if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            // Launched from the attach dialog: permission is already granted for this device.
            intent.usbDevice()?.let { if (prefs.getBoolean("auto_backup", false)) autoBackup(it) else probe(it) }
        }
    }

    /** Finds the iPod and runs [action] once we have USB permission. */
    internal fun withDevice(action: (UsbDevice) -> Unit) {
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
    internal inner class DeviceSession(private val mux: UsbMux) {
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

    internal fun runOnDevice(device: UsbDevice, after: () -> Unit = {}, job: (UsbMux, DeviceSession) -> Unit) {
        if (busy) { log("Busy, please wait for the current operation to finish."); return }
        busy = true
        ui.task = lastHeadline
        Thread {
            try {
                AppleUsbTransport.open(usbManager, device, this::log).use { usb ->
                    UsbMux(usb, this::log).use { mux ->
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
    internal fun openSession(stream: MuxStream, quiet: Boolean = false): LockdownClient {
        val store = PairRecordStore(this)
        val lockdown = LockdownClient(stream, log = if (quiet) { _ -> } else this::log)
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

    internal fun pairNew(lockdown: LockdownClient, store: PairRecordStore, udid: String): PairRecord {
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

    internal fun <T> withService(mux: UsbMux, lockdown: DeviceSession, name: String, block: (MuxStream) -> T): T {
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
    internal fun tryAfc2(mux: UsbMux, lockdown: DeviceSession): MuxStream? = try {
        val svc = lockdown.startService(AFC2_SERVICE)
        if (svc.ssl) null else mux.connect(svc.port)
    } catch (e: LockdownException) {
        null
    }

    // ---------------------------------------------------------------- app list + backup  (functions in AppBackup.kt)
    // ---------------------------------------------------------------- stopping

    internal enum class StopMode { NONE, AFTER_CURRENT, NOW }

    internal class StopNowException : RuntimeException("Stopped")

    @Volatile internal var stopMode = StopMode.NONE

    /** Called from transfer loops and progress callbacks; aborts the current step on "Stop now". */
    internal fun checkStopNow() {
        if (stopMode == StopMode.NOW) throw StopNowException()
    }

    internal fun setStopButtonVisible(visible: Boolean) = runOnUiThread {
        ui.stopVisible = visible
        ui.stopEnabled = true
        ui.stopLabel = "Stop"
    }

    internal fun confirmStop() {
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
    internal fun backupIpaViaAfc2(afc2: AfcClient, saver: IpaSaver, app: InstallationProxy.App) {
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
    internal fun backupData(mux: UsbMux, lockdown: DeviceSession, saver: IpaSaver, app: InstallationProxy.App): Boolean =
        withService(mux, lockdown, HouseArrest.SERVICE) { stream ->
            HouseArrest.vendContainer(stream, app.bundleId)
            val afc = AfcClient(stream).apply { cancelCheck = { checkStopNow() } }
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

    // ---------------------------------------------------------------- file browser  (functions in FileBrowser.kt)
    internal data class BrowserItem(val path: String, val isDir: Boolean, val size: Long)

    internal sealed class BrowserCmd {
        data class Open(val path: String) : BrowserCmd()
        /** Measure any folders in [items], then ask the user to confirm saving them all. */
        data class Prepare(val items: List<BrowserItem>, val returnTo: String) : BrowserCmd()
        data class Download(val items: List<BrowserItem>, val returnTo: String) : BrowserCmd()
        /** Copy files picked on the phone into [dir] on the device. */
        data class Upload(val uris: List<android.net.Uri>, val dir: String) : BrowserCmd()
        object Close : BrowserCmd()
    }

    internal data class BrowserEntry(val name: String, val entry: AfcClient.Entry?)

    @Volatile internal var measureCancelled = false
    internal var measureDialog: AlertDialog? = null
    internal var measureText: TextView? = null

    /** Browser waiting on the system file picker: its command queue and the folder to upload into. */
    internal var pendingUpload: Pair<LinkedBlockingQueue<BrowserCmd>, String>? = null

    internal val pickUploads = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val (cmds, dir) = pendingUpload ?: return@registerForActivityResult
        pendingUpload = null
        cmds.offer(if (uris.isNullOrEmpty()) BrowserCmd.Open(dir) else BrowserCmd.Upload(uris, dir))
    }

    // ---------------------------------------------------------------- music browser  (functions in MusicBrowser.kt)
    internal sealed class MusicCmd {
        object Show : MusicCmd()
        data class Copy(val tracks: List<MusicLibrary.Track>, val writeTags: Boolean) : MusicCmd()
        object Close : MusicCmd()
    }

    /** Track pids checked in the music list; kept while the browser is open. */
    internal val musicSelected = HashSet<Long>()
    @Volatile internal var musicOther = false
    @Volatile internal var musicView = MediaView.MUSIC
    internal var musicQuery = ""

    /** Add songs: the device (if any) waiting on the file picker, and the songs staged for adding. */
    internal var addSongsDevice: UsbDevice? = null
    internal var addSongsPending = false
    internal var addKind = us.dannyhand.ipabackup.music.SongImport.AddKind.MUSIC
    internal var stagedSongs: List<StagedSong> = emptyList()
    internal val pickSongs = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (!addSongsPending) return@registerForActivityResult
        addSongsPending = false
        if (!uris.isNullOrEmpty()) prepareSongs(addSongsDevice, uris, addKind)
    }

    // ---------------------------------------------------------------- playlists  (functions in MusicBrowser.kt)
    // ---------------------------------------------------------------- photos & videos  (functions in PhotoBrowser.kt)
    internal sealed class PhotoCmd {
        object Show : PhotoCmd()
        data class Preview(val m: CameraRoll.Media) : PhotoCmd()
        data class SavePreview(val m: CameraRoll.Media, val file: java.io.File) : PhotoCmd()
        data class Save(val items: List<CameraRoll.Media>) : PhotoCmd()
        object Close : PhotoCmd()
    }

    /** Thumbnails live on disk (cache/thumbs); this keeps the recently shown ones decoded. */
    internal val thumbLru = object : android.util.LruCache<String, android.graphics.Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8 / 1024).toInt()
    ) {
        override fun sizeOf(key: String, value: android.graphics.Bitmap) = value.byteCount / 1024
    }
    /** Paths whose thumbnail has been made (or can't be). */
    internal val thumbDone = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())
    internal val photoSelected = HashSet<String>()
    @Volatile internal var photoFirstVisible = 0
    @Volatile internal var photoAdapter: BaseAdapter? = null
    @Volatile internal var loadCancelled = false
    internal var progressDialog: AlertDialog? = null
    internal var progressText: TextView? = null

    /** Grid cell that stays square. */
    internal class SquareFrame(ctx: Context) : android.widget.FrameLayout(ctx) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) = super.onMeasure(widthMeasureSpec, widthMeasureSpec)
    }

    // ---------------------------------------------------------------- generic pick-and-save list  (functions in PickList.kt)
    /** One row in a pick-and-save list. */
    internal class Pick(val key: String, val title: String, val subtitle: String, val size: Long)

    internal sealed class PickCmd {
        object Show : PickCmd()
        data class Save(val keys: List<String>) : PickCmd()
        object Close : PickCmd()
    }

    // ---------------------------------------------------------------- voice memos  (functions in PickList.kt)
    // ---------------------------------------------------------------- books  (functions in Books.kt)
    // ---------------------------------------------------------------- contacts, notes, bookmarks  (functions in PersonalData.kt)
    // ---------------------------------------------------------------- messages  (functions in PersonalData.kt)
    // ---------------------------------------------------------------- restore (install .ipa + app data)  (functions in Restore.kt)
    /** An .ipa to install: one this app saved, or one picked with the system file picker. */
    internal class RestoreItem(val name: String, val uri: android.net.Uri, val size: Long)

    /** Waiting for the system file picker; resumed in [handlePickedIpas]. */
    internal var pendingRestoreDevice: UsbDevice? = null

    internal val pickIpas = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        handlePickedIpas(uris)
    }

    // ---------------------------------------------------------------- device tools  (functions in DeviceTools.kt)
    // ---------------------------------------------------------------- system log  (functions in DeviceTools.kt)
    // ---------------------------------------------------------------- crash reports  (functions in DeviceTools.kt)
    // ---------------------------------------------------------------- home screen  (functions in DeviceTools.kt)
    internal var pendingLayoutDevice: UsbDevice? = null
    internal val pickLayout = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        val device = pendingLayoutDevice ?: return@registerForActivityResult
        pendingLayoutDevice = null
        if (uri != null) confirmRestoreLayout(device, uri)
    }

    // ---------------------------------------------------------------- uninstall  (functions in AppBackup.kt)
    // ---------------------------------------------------------------- auto-backup  (functions in AppBackup.kt)
    // ---------------------------------------------------------------- Cydia  (functions in PersonalData.kt)
    // ---------------------------------------------------------------- root certificates  (functions in Certificates.kt)
    internal var fileServer: FileServer? = null

    // ---------------------------------------------------------------- add books  (functions in Books.kt)
    internal var pendingBooksDevice: UsbDevice? = null
    internal val pickBooks = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val device = pendingBooksDevice
        pendingBooksDevice = null
        if (!uris.isNullOrEmpty()) prepareBooks(device, uris)
    }

    internal class NewBook(val fileName: String, val bytes: ByteArray, val info: BookImport.Info)

    // ---------------------------------------------------------------- music library database backup  (functions in MusicLibraryBackup.kt)
    internal var pendingLibDevice: UsbDevice? = null
    internal val pickLibZip = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        val device = pendingLibDevice ?: return@registerForActivityResult
        pendingLibDevice = null
        if (uri != null) confirmMusicLibraryRestore(device, uri)
    }

    // ---------------------------------------------------------------- clean up  (functions in CleanUp.kt)
    /** A leftover on the device: name and size. */
    internal class Leftover(val name: String, val size: Long)

    // ---------------------------------------------------------------- helpers

    internal fun join(dir: String, name: String) = if (dir.endsWith("/")) dir + name else "$dir/$name"
    internal fun parent(path: String) = path.trimEnd('/').substringBeforeLast('/').ifEmpty { "/" }

    internal fun sizeLabel(bytes: Long) = if (bytes > 0) "  (${mb(bytes)})" else ""
    internal fun mb(bytes: Long) = if (bytes < 0) "?" else "%.1f MB".format(bytes / 1048576.0)
    internal fun humanSize(bytes: Long) = when {
        bytes < 0 -> "?"
        bytes < 1024 -> "$bytes B"
        bytes < 1048576 -> "%.1f KB".format(bytes / 1024.0)
        else -> mb(bytes)
    }

    internal fun appVersion(): String = runCatching {
        "v" + packageManager.getPackageInfo(packageName, 0).versionName
    }.getOrDefault("")

    /** Saves the activity log to Download/IPA Backup/Logs/log-yyyy-MM-dd-HH-mm-ss.txt. */
    internal fun saveLog() {
        val name = "log-" + java.text.SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", java.util.Locale.US).format(java.util.Date()) + ".txt"
        runCatching { saveBytes(name, LOGS_SUBFOLDER, (ui.log.joinToString("\n") + "\n").toByteArray()) }
            .onSuccess { Toast.makeText(this, "Saved to $it", Toast.LENGTH_SHORT).show() }
            .onFailure { log("Saving the log FAILED: ${it.message}") }
    }

    internal fun copyLog() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("IPA Backup log", ui.log.joinToString("\n")))
        Toast.makeText(this, "Log copied", Toast.LENGTH_SHORT).show()
    }

    internal fun keepScreenOn(on: Boolean) = runOnUiThread {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    internal fun log(msg: String) {
        if (msg.startsWith("\n")) lastHeadline = msg.trim().lineSequence().firstOrNull()
        runOnUiThread {
            msg.split('\n').forEach { ui.log.add(it) }
            if (ui.log.size > 5000) ui.log.removeRange(0, ui.log.size - 5000)
        }
    }

    @Suppress("DEPRECATION")
    internal fun Intent.usbDevice(): UsbDevice? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        else getParcelableExtra(UsbManager.EXTRA_DEVICE)
}
