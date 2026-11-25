package us.dannyhand.ipabackup.afc

import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Zips directory trees from an AFC connection (app data containers, app bundles over AFC2). */
class AfcZipper(
    private val afc: AfcClient,
    private val zip: ZipOutputStream,
    private val onFile: (path: String, bytesSoFar: Long) -> Unit = { _, _ -> },
) {
    var bytes = 0L
        private set
    var files = 0
        private set
    val skippedLinks = mutableListOf<String>()
    /** Zip paths (e.g. "Library/Caches") to leave out, along with everything under them. */
    var exclude: (String) -> Boolean = { false }

    /**
     * Adds everything under device directory [root] into the zip under [zipPrefix]
     * (e.g. root "/var/mobile/Applications/X/Foo.app", prefix "Payload/Foo.app/").
     * [include] filters top-level names only.
     */
    fun addTree(root: String, zipPrefix: String, include: (String) -> Boolean = { true }) {
        val prefix = if (zipPrefix.isEmpty() || zipPrefix.endsWith("/")) zipPrefix else "$zipPrefix/"
        if (prefix.isNotEmpty()) addDirEntry(prefix, 0)
        for (name in afc.readDir(root).sorted()) {
            if (!include(name)) continue
            walk(join(root, name), prefix + name)
        }
    }

    /** Adds one device file at zip path [zipPath], if it exists. Returns true if added. */
    fun addFileIfExists(devicePath: String, zipPath: String): Boolean {
        val e = runCatching { afc.stat(devicePath) }.getOrNull() ?: return false
        if (e.isDir || e.isLink) return false
        addFile(e, zipPath)
        return true
    }

    private fun walk(path: String, zipPath: String) {
        if (exclude(zipPath)) return
        val e = afc.stat(path)
        when {
            e.isLink -> skippedLinks += path
            e.isDir -> {
                addDirEntry("$zipPath/", e.mtimeMs)
                for (name in afc.readDir(path).sorted()) walk(join(path, name), "$zipPath/$name")
            }
            else -> addFile(e, zipPath)
        }
    }

    private fun addDirEntry(zipPath: String, mtimeMs: Long) {
        zip.putNextEntry(ZipEntry(zipPath).apply { if (mtimeMs > 0) time = mtimeMs })
        zip.closeEntry()
    }

    private fun addFile(e: AfcClient.Entry, zipPath: String) {
        zip.putNextEntry(ZipEntry(zipPath).apply { if (e.mtimeMs > 0) time = e.mtimeMs })
        // copyFile must not close the zip stream.
        val shield = object : FilterOutputStream(zip) {
            override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
            override fun close() {}
        }
        bytes += afc.copyFile(e.path, shield)
        zip.closeEntry()
        files++
        onFile(e.path, bytes)
    }

    private fun join(dir: String, name: String) = if (dir.endsWith("/")) dir + name else "$dir/$name"

    companion object {
        fun open(out: OutputStream) = ZipOutputStream(out)
    }
}
