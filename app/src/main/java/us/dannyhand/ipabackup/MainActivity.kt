package us.dannyhand.ipabackup

import android.app.Activity
import android.app.AlertDialog
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
import us.dannyhand.ipabackup.music.MusicLibrary
import us.dannyhand.ipabackup.util.AudioTagger
import us.dannyhand.ipabackup.util.ExifThumb
import us.dannyhand.ipabackup.photos.CameraRoll
import us.dannyhand.ipabackup.media.BooksLibrary
import us.dannyhand.ipabackup.media.VoiceMemos
import us.dannyhand.ipabackup.personal.AddressBook
import us.dannyhand.ipabackup.personal.BookmarksHtml
import us.dannyhand.ipabackup.personal.NotesDb
import us.dannyhand.ipabackup.personal.NotesExport
import us.dannyhand.ipabackup.personal.SafariBookmarks
import us.dannyhand.ipabackup.personal.VCard
import us.dannyhand.ipabackup.plist.Plist
import us.dannyhand.ipabackup.usb.AppleUsbTransport
import java.io.IOException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {

    private companion object {
        const val ACTION_USB_PERMISSION = "us.dannyhand.ipabackup.USB_PERMISSION"
        /** Root-filesystem AFC, installed by the "Apple File Conduit 2" jailbreak package. */
        const val AFC2_SERVICE = "com.apple.afc2"
        const val FILES_SUBFOLDER = "Files"
        const val MUSIC_SUBFOLDER = "Music"
        const val PHOTOS_SUBFOLDER = "Photos"
        const val MEMOS_SUBFOLDER = "Voice Memos"
        const val BOOKS_SUBFOLDER = "Books"
        const val PERSONAL_SUBFOLDER = "Personal"
        const val MENU_COPY_LOG = 1
        const val MENU_CLEAR_LOG = 2
        const val THUMB_PX = 200
        /** Formats we can write song info into (DRM .m4p is left untouched). */
        val TAGGABLE = setOf("m4a", "m4b", "aac", "mp3")
    }

    private lateinit var logView: TextView
    private lateinit var scroll: ScrollView
    private lateinit var stopButton: Button
    private val usbManager by lazy { getSystemService(Context.USB_SERVICE) as UsbManager }
    private val prefs by lazy { getSharedPreferences("options", Context.MODE_PRIVATE) }
    @Volatile private var busy = false
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

        logView = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setTextIsSelectable(true)
            setPadding(24, 24, 24, 24)
        }
        scroll = ScrollView(this).apply { addView(logView) }

        fun button(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { onClick() }
        }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = { LinearLayout.LayoutParams(0, -2, 1f) }
            addView(button("Apps") { withDevice(::probe) }, lp())
            addView(button("Music") { withDevice(::browseMusic) }, lp())
            addView(button("Photos") { withDevice(::browsePhotos) }, lp())
            addView(button("Files") { withDevice(::browseFiles) }, lp())
        }
        stopButton = button("Stop…") { confirmStop() }.apply {
            visibility = View.GONE
            setTextColor(0xFFC62828.toInt())
        }
        val buttons2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = { LinearLayout.LayoutParams(0, -2, 1f) }
            addView(button("Voice Memos") { withDevice(::browseVoiceMemos) }, lp())
            addView(button("Books") { withDevice(::browseBooks) }, lp())
            addView(button("Contacts & Notes") { withDevice(::exportPersonalData) }, lp())
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(buttons)
            addView(buttons2)
            addView(stopButton)
            addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        // targetSdk 35 forces edge-to-edge; pad for the action bar + system bars.
        root.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                v.setPadding(bars.left, bars.top + actionBarHeight(), bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        setContentView(root)

        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(permissionReceiver, filter)
        }

        log("IPA Backup ${appVersion()}")
        log("Connect the iPod with an OTG adapter, then tap Apps.")
        handleIntent(intent)
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menu.add(0, MENU_COPY_LOG, 0, "Copy log")
            .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM or android.view.MenuItem.SHOW_AS_ACTION_WITH_TEXT)
        menu.add(0, MENU_CLEAR_LOG, 1, "Clear log")
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean = when (item.itemId) {
        MENU_COPY_LOG -> { copyLog(); true }
        MENU_CLEAR_LOG -> { logView.text = ""; true }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        unregisterReceiver(permissionReceiver)
        super.onDestroy()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            // Launched from the attach dialog: permission is already granted for this device.
            intent.usbDevice()?.let { probe(it) }
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
            client.stopSession()
            runCatching { stream.close() }
        }
    }

    private fun runOnDevice(device: UsbDevice, after: () -> Unit = {}, job: (UsbMux, DeviceSession) -> Unit) {
        if (busy) { log("Busy, please wait for the current operation to finish."); return }
        busy = true
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
        val skipBox = CheckBox(this).apply {
            text = "Skip apps already saved (✓ ${isSaved.count { it }} of ${apps.size})"
            isChecked = prefs.getBoolean("skip_saved", true)
        }
        val dataBox = CheckBox(this).apply {
            text = "Also back up app data (saves, settings) as a separate .zip"
            isChecked = prefs.getBoolean("include_data", false)
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
        val iconBox = CheckBox(this).apply {
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
            addView(list, LinearLayout.LayoutParams(-1, (resources.displayMetrics.heightPixels * 0.5).toInt()))
        }

        fun start(chosen: List<InstallationProxy.App>) {
            prefs.edit().putBoolean("skip_saved", skipBox.isChecked).putBoolean("include_data", dataBox.isChecked).apply()
            if (chosen.isEmpty()) { log("Nothing selected."); return }
            backup(device, chosen, skipBox.isChecked, dataBox.isChecked, saved)
        }

        AlertDialog.Builder(this)
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
            AlertDialog.Builder(this)
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
        AlertDialog.Builder(this)
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
        stopButton.visibility = if (visible) View.VISIBLE else View.GONE
        stopButton.isEnabled = true
        stopButton.text = "Stop…"
    }

    private fun confirmStop() {
        if (stopMode == StopMode.NOW) return
        AlertDialog.Builder(this)
            .setTitle("Stop?")
            .setMessage("Anything already saved is kept either way.")
            .setPositiveButton("Stop now") { _, _ ->
                stopMode = StopMode.NOW
                stopButton.isEnabled = false
                stopButton.text = "Stopping…"
                log("\nStopping now…")
            }
            .setNeutralButton("After this item") { _, _ ->
                stopMode = StopMode.AFTER_CURRENT
                stopButton.text = "Stopping after this item… (tap to stop now)"
                log("\nWill stop after the current item finishes.")
            }
            .setNegativeButton("Keep going", null)
            .show()
    }

    /** Jailbreak fallback: zip Payload/<App>.app + iTunesMetadata.plist + iTunesArtwork straight from the filesystem. */
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
                            } catch (e: Exception) {
                                log("Can't open ${cmd.path}: ${e.message}")
                                if (cmd.path != "/") cmds.offer(BrowserCmd.Open(parent(cmd.path))) else break
                                continue
                            }
                            runOnUiThread { showBrowser(label, cmd.path, entries, cmds) }
                        }
                        is BrowserCmd.Prepare -> prepareSave(afc, cmd, cmds)
                        is BrowserCmd.Download -> {
                            val many = cmd.items.size > 1
                            cmd.items.forEachIndexed { i, item ->
                                if (many) log("[${i + 1}/${cmd.items.size}] ${item.path}")
                                try {
                                    downloadPath(afc, saver, item.path, item.isDir)
                                } catch (e: Exception) {
                                    log("Download failed: ${e.message}")
                                }
                            }
                            if (many) log("Saved ${cmd.items.size} items.")
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
        AlertDialog.Builder(this)
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
        measureDialog = AlertDialog.Builder(this)
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

        val dialog = AlertDialog.Builder(this)
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
            val pos = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val neu = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            if (selecting) {
                hintView.text = "${selected.size} selected · tap to check or uncheck"
                pos.visibility = View.VISIBLE
                pos.isEnabled = selected.isNotEmpty()
                pos.text = "Save ${selected.size}"
                neu.visibility = View.VISIBLE
                neu.text = "Cancel selection"
            } else {
                hintView.text = "Tap to open · hold to select several"
                pos.visibility = View.GONE
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
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val items = selected.sorted().mapNotNull { i ->
                    val (name, e) = entries[i]
                    e?.let { BrowserItem(join(path, name), it.isDir, it.size) }
                }
                if (items.isNotEmpty()) go(BrowserCmd.Prepare(items, path))
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                if (selecting) clearSelection() else go(BrowserCmd.Prepare(listOf(BrowserItem(path, true, 0)), path))
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { go(BrowserCmd.Close) }
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
    private var musicQuery = ""

    private fun browseMusic(device: UsbDevice) {
        log("\nLoading the music library…")
        val cmds = LinkedBlockingQueue<MusicCmd>()
        musicSelected.clear()
        musicQuery = ""
        runOnDevice(device, after = { setStopButtonVisible(false); stopMode = StopMode.NONE }) { mux, lockdown ->
            withService(mux, lockdown, AfcClient.SERVICE) { stream ->
                val afc = AfcClient(stream)
                val tracks = loadMusicLibrary(afc) ?: return@withService
                log("${tracks.size} songs (${humanSize(tracks.sumOf { it.size })}) in the library.")
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

    /** Copies MediaLibrary.sqlitedb (plus its WAL files, if any) from the iPod and reads it. */
    private fun loadMusicLibrary(afc: AfcClient): List<MusicLibrary.Track>? {
        val dir = java.io.File(cacheDir, "medialib").apply { deleteRecursively(); mkdirs() }
        for (suffix in listOf("", "-wal", "-shm")) {
            val devicePath = MusicLibrary.DB_PATH + suffix
            if (suffix.isNotEmpty() && runCatching { afc.stat(devicePath) }.isFailure) continue
            try {
                java.io.FileOutputStream(java.io.File(dir, "MediaLibrary.sqlitedb$suffix")).use { afc.copyFile(devicePath, it) }
            } catch (e: Exception) {
                if (suffix.isEmpty()) {
                    log("No music library found on the iPod (${e.message}). iOS 5 or newer is needed.")
                    return null
                }
            }
        }
        return try {
            android.database.sqlite.SQLiteDatabase.openDatabase(
                java.io.File(dir, "MediaLibrary.sqlitedb").path, null,
                android.database.sqlite.SQLiteDatabase.OPEN_READWRITE, // it's our private copy
            ).use { db -> MusicLibrary(AndroidSqlDb(db)).load() }
        } catch (e: Exception) {
            log("Couldn't read the music library: ${e.message}")
            null
        }
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
        fun initial(t: MusicLibrary.Track) = t.artist.firstOrNull()?.uppercaseChar()?.let { if (it.isLetter()) "$it" else "#" } ?: "#"
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
                tv.text = "$box  ${t.title}$drm\n      ${t.artist} — ${t.album}  (${humanSize(t.size)})"
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
        val dialog = AlertDialog.Builder(this)
            .setTitle("Music (${tracks.size} songs)")
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
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                isEnabled = selectedTracks.isNotEmpty()
                text = if (selectedTracks.isEmpty()) "Copy" else "Copy ${selectedTracks.size}"
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.text =
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
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val chosen = tracks.filter { it.pid in musicSelected }
                if (chosen.isNotEmpty()) {
                    handled = true
                    dialog.dismiss()
                    confirmCopySongs(chosen, cmds)
                }
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                if (visible.isNotEmpty() && visible.all { it.pid in musicSelected }) visible.forEach { musicSelected.remove(it.pid) }
                else visible.forEach { musicSelected.add(it.pid) }
                refresh()
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { go(MusicCmd.Close) }
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
            append("Copy ${chosen.size} song${if (chosen.size == 1) "" else "s"} (${humanSize(total)}) as \"Artist - Title\" to ")
            append("Download/${IpaSaver.FOLDER}/$MUSIC_SUBFOLDER/?")
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
        val tagBox = CheckBox(this).apply {
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
        AlertDialog.Builder(this)
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
        log("\nCopying ${tracks.size} songs to Download/${IpaSaver.FOLDER}/$MUSIC_SUBFOLDER/")
        try {
            for ((i, t) in tracks.withIndex()) {
                if (stopMode != StopMode.NONE) break
                val name = MusicLibrary.uniqueName(MusicLibrary.fileName(t), used)
                log("[${i + 1}/${tracks.size}] $name")
                val target = saver.create(name, MUSIC_SUBFOLDER)
                try {
                    if (writeTags && t.extension in TAGGABLE) {
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
                                val plain = saver.create(name, MUSIC_SUBFOLDER)
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
        val dialog = AlertDialog.Builder(this)
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
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                visibility = if (selecting) View.VISIBLE else View.GONE
                isEnabled = chosen.isNotEmpty()
                text = "Save ${chosen.size}"
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.text = when {
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
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val chosen = media.filter { it.path in photoSelected }
                if (chosen.isNotEmpty()) {
                    handled = true
                    dialog.dismiss()
                    confirmSaveMedia(chosen, cmds)
                }
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                if (selecting && photoSelected.size == media.size) {
                    photoSelected.clear()
                    selecting = false
                } else {
                    selecting = true
                    media.forEach { photoSelected += it.path }
                }
                refresh()
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { go(PhotoCmd.Close) }
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
        AlertDialog.Builder(this)
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
        AlertDialog.Builder(this)
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
        progressDialog = AlertDialog.Builder(this)
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
    ) {
        log("\nReading $what…")
        val cmds = LinkedBlockingQueue<PickCmd>()
        val selected = HashSet<String>()
        runOnDevice(device, after = { setStopButtonVisible(false); stopMode = StopMode.NONE }) { mux, lockdown ->
            withService(mux, lockdown, AfcClient.SERVICE) { stream ->
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
        val dialog = AlertDialog.Builder(this)
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
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                isEnabled = chosen.isNotEmpty()
                text = if (chosen.isEmpty()) "Save" else "Save ${chosen.size}"
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.text = if (chosen.size == picks.size) "Select none" else "Select all"
        }
        list.setOnItemClickListener { _, _, position, _ ->
            val k = picks[position].key
            if (!selected.add(k)) selected.remove(k)
            refresh()
        }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val chosen = picks.filter { it.key in selected }
                if (chosen.isNotEmpty()) { handled = true; dialog.dismiss(); onSave(chosen) }
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                if (selected.size == picks.size) selected.clear() else picks.forEach { selected += it.key }
                refresh()
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
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
        AlertDialog.Builder(this)
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
        fun box(label: String, key: String) = CheckBox(this).apply { text = label; isChecked = prefs.getBoolean(key, true) }
        val contacts = box("Contacts → .vcf (imports into Google Contacts)", "export_contacts")
        val notes = box("Notes → .html, plus a .zip of text files", "export_notes")
        val bookmarks = box("Safari bookmarks & Reading List → .html (imports into any browser)", "export_bookmarks")
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
        }
        AlertDialog.Builder(this)
            .setTitle("Contacts, Notes & Bookmarks")
            .setView(body)
            .setPositiveButton("Export") { _, _ ->
                prefs.edit().putBoolean("export_contacts", contacts.isChecked).putBoolean("export_notes", notes.isChecked)
                    .putBoolean("export_bookmarks", bookmarks.isChecked).apply()
                if (contacts.isChecked || notes.isChecked || bookmarks.isChecked)
                    runPersonalExport(device, contacts.isChecked, notes.isChecked, bookmarks.isChecked)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun runPersonalExport(device: UsbDevice, doContacts: Boolean, doNotes: Boolean, doBookmarks: Boolean) {
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

    private fun copyLog() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("IPA Backup log", logView.text))
        Toast.makeText(this, "Log copied", Toast.LENGTH_SHORT).show()
    }

    private fun keepScreenOn(on: Boolean) = runOnUiThread {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun actionBarHeight(): Int {
        if (actionBar?.isShowing != true) return 0
        val tv = TypedValue()
        return if (theme.resolveAttribute(android.R.attr.actionBarSize, tv, true))
            TypedValue.complexToDimensionPixelSize(tv.data, resources.displayMetrics) else 0
    }

    private fun log(msg: String) = runOnUiThread {
        logView.append(msg + "\n")
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    @Suppress("DEPRECATION")
    private fun Intent.usbDevice(): UsbDevice? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        else getParcelableExtra(UsbManager.EXTRA_DEVICE)
}
