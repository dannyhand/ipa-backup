package us.dannyhand.ipabackup

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import us.dannyhand.ipabackup.instproxy.InstallationProxy
import us.dannyhand.ipabackup.util.CgbiPng
import java.io.File

/** App icons fetched from SpringBoard, stored as standard PNGs keyed by bundle ID + version. */
class IconCache(context: Context) {
    private val dir = File(context.filesDir, "icons").apply { mkdirs() }
    private val memory = HashMap<String, Bitmap?>()

    private fun key(app: InstallationProxy.App) = IpaSaver.clean("${app.bundleId}_${app.version}") + ".png"

    fun has(app: InstallationProxy.App) = File(dir, key(app)).exists()

    /** Stores [png] (converting Apple CgBI PNGs if needed). Returns false if it couldn't be decoded. */
    fun put(app: InstallationProxy.App, png: ByteArray): Boolean {
        val standard = if (CgbiPng.isCgbi(png)) CgbiPng.toStandard(png) ?: return false else png
        BitmapFactory.decodeByteArray(standard, 0, standard.size) ?: return false
        File(dir, key(app)).writeBytes(standard)
        memory.remove(key(app))
        return true
    }

    fun bitmap(app: InstallationProxy.App): Bitmap? = memory.getOrPut(key(app)) {
        File(dir, key(app)).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
    }
}
