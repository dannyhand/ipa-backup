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
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import us.dannyhand.ipabackup.afc.AfcClient
import us.dannyhand.ipabackup.afc.AfcZipper
import us.dannyhand.ipabackup.housearrest.HouseArrest
import us.dannyhand.ipabackup.instproxy.InstallationProxy
import us.dannyhand.ipabackup.lockdown.LockdownClient
import us.dannyhand.ipabackup.lockdown.LockdownException
import us.dannyhand.ipabackup.mux.MuxStream
import us.dannyhand.ipabackup.mux.UsbMux
import us.dannyhand.ipabackup.pairing.PairRecord
import us.dannyhand.ipabackup.pairing.PairRecordStore
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
            addView(button("Browse files") { withDevice(::browseFiles) }, lp())
            addView(button("Copy log") { copyLog() }, lp())
        }
        stopButton = button("Stop backup…") { confirmStop() }.apply {
            visibility = android.view.View.GONE
            setTextColor(0xFFC62828.toInt())
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(buttons)
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
        val list = ListView(this).apply {
            choiceMode = ListView.CHOICE_MODE_MULTIPLE
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_list_item_multiple_choice, labels)
        }
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
            addView(skipBox)
            addView(dataBox)
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
            .setNeutralButton("Back up all") { _, _ -> start(apps) }
            .setNegativeButton("Cancel", null)
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
        stopButton.visibility = if (visible) android.view.View.VISIBLE else android.view.View.GONE
        stopButton.isEnabled = true
        stopButton.text = "Stop backup…"
    }

    private fun confirmStop() {
        if (stopMode == StopMode.NOW) return
        AlertDialog.Builder(this)
            .setTitle("Stop backup?")
            .setMessage("Apps already saved are kept either way.")
            .setPositiveButton("Stop now") { _, _ ->
                stopMode = StopMode.NOW
                stopButton.isEnabled = false
                stopButton.text = "Stopping…"
                log("\nStopping now…")
            }
            .setNeutralButton("After this app") { _, _ ->
                stopMode = StopMode.AFTER_CURRENT
                stopButton.text = "Stopping after this app… (tap to stop now)"
                log("\nWill stop after the current app finishes.")
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

    private sealed class BrowserCmd {
        data class List(val path: String) : BrowserCmd()
        data class Download(val path: String, val isDir: Boolean, val returnTo: String) : BrowserCmd()
        object Close : BrowserCmd()
    }

    private data class BrowserEntry(val name: String, val entry: AfcClient.Entry?)

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
                cmds.offer(BrowserCmd.List("/"))
                while (true) {
                    // Close quietly if left idle.
                    val cmd = cmds.poll(15, TimeUnit.MINUTES) ?: break
                    when (cmd) {
                        is BrowserCmd.Close -> break
                        is BrowserCmd.List -> {
                            val entries = try {
                                afc.readDir(cmd.path).sortedBy { it.lowercase() }.map { name ->
                                    BrowserEntry(name, runCatching { afc.stat(join(cmd.path, name)) }.getOrNull())
                                }
                            } catch (e: Exception) {
                                log("Can't open ${cmd.path}: ${e.message}")
                                if (cmd.path != "/") cmds.offer(BrowserCmd.List(parent(cmd.path))) else break
                                continue
                            }
                            runOnUiThread { showBrowser(label, cmd.path, entries, cmds) }
                        }
                        is BrowserCmd.Download -> {
                            try {
                                downloadPath(afc, saver, cmd.path, cmd.isDir)
                            } catch (e: Exception) {
                                log("Download failed: ${e.message}")
                            }
                            cmds.offer(BrowserCmd.List(cmd.returnTo))
                        }
                    }
                }
            }
            log("File browser closed.")
        }
    }

    private fun showBrowser(label: String, path: String, entries: List<BrowserEntry>, cmds: LinkedBlockingQueue<BrowserCmd>) {
        if (isFinishing) { cmds.offer(BrowserCmd.Close); return }
        val atRoot = path == "/"
        val rows = mutableListOf<String>()
        if (!atRoot) rows += "⬆  .."
        entries.forEach { (name, e) ->
            rows += when {
                e == null -> "?  $name"
                e.isDir -> "📁  $name"
                e.isLink -> "↪  $name"
                else -> "📄  $name  (${humanSize(e.size)})"
            }
        }
        if (entries.isEmpty()) rows += "(empty)"

        val b = AlertDialog.Builder(this)
            .setTitle("$label  $path")
            .setItems(rows.toTypedArray()) { _, which ->
                val idx = if (atRoot) which else which - 1
                if (idx < 0) { cmds.offer(BrowserCmd.List(parent(path))); return@setItems }
                val be = entries.getOrNull(idx) ?: run { cmds.offer(BrowserCmd.List(path)); return@setItems }
                val full = join(path, be.name)
                val e = be.entry
                when {
                    e == null || e.isDir || e.isLink -> cmds.offer(BrowserCmd.List(full)) // links: try to open as folder
                    else -> confirmDownload(full, e.size, false, path, cmds)
                }
            }
            .setNegativeButton("Close") { _, _ -> cmds.offer(BrowserCmd.Close) }
            .setOnCancelListener { cmds.offer(BrowserCmd.Close) }
        if (!atRoot) b.setNeutralButton("Save folder (.zip)") { _, _ -> confirmDownload(path, -1, true, path, cmds) }
        b.show()
    }

    private fun confirmDownload(path: String, size: Long, isDir: Boolean, returnTo: String, cmds: LinkedBlockingQueue<BrowserCmd>) {
        val what = if (isDir) "the folder ${path.substringAfterLast('/')} as a .zip" else "${path.substringAfterLast('/')} (${humanSize(size)})"
        AlertDialog.Builder(this)
            .setMessage("Save $what to Download/${IpaSaver.FOLDER}/$FILES_SUBFOLDER/?")
            .setPositiveButton("Save") { _, _ -> cmds.offer(BrowserCmd.Download(path, isDir, returnTo)) }
            .setNegativeButton("Back") { _, _ -> cmds.offer(BrowserCmd.List(returnTo)) }
            .setOnCancelListener { cmds.offer(BrowserCmd.List(returnTo)) }
            .show()
    }

    private fun downloadPath(afc: AfcClient, saver: IpaSaver, path: String, isDir: Boolean) {
        val base = IpaSaver.clean(path.trimEnd('/').substringAfterLast('/').ifEmpty { "root" })
        if (isDir) {
            val target = saver.create("$base.zip", FILES_SUBFOLDER)
            try {
                log("Zipping $path …")
                val zos = AfcZipper.open(target.stream)
                val z = AfcZipper(afc, zos)
                z.addTree(path, "$base/")
                zos.finish()
                target.commit()
                log("Saved ${target.location} (${z.files} files, ${mb(z.bytes)})")
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
