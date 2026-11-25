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

    /**
     * Adds a small file uncompressed (STORED). EPUB requires its "mimetype" file first and stored,
     * or readers reject the book.
     */
    fun addStored(devicePath: String, zipPath: String) = addStoredBytes(afc.readHead(devicePath, 1 shl 20), zipPath)

    fun addStoredBytes(bytes: ByteArray, zipPath: String) {
        val crc = java.util.zip.CRC32().apply { update(bytes) }
        zip.putNextEntry(ZipEntry(zipPath).apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            this.crc = crc.value
        })
        zip.write(bytes)
        zip.closeEntry()
        files++
        this.bytes += bytes.size
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

/** Totals for a device folder, with a rough estimate of how big a .zip of it would be. */
data class TreeSize(var files: Int = 0, var bytes: Long = 0, var estimatedZipBytes: Long = 0) {
    fun add(other: TreeSize) {
        files += other.files
        bytes += other.bytes
        estimatedZipBytes += other.estimatedZipBytes
    }
}

object ZipEstimate {
    /** Formats that are already compressed: deflate gains ~nothing. */
    private val COMPRESSED = setOf(
        "png", "jpg", "jpeg", "gif", "mp3", "m4a", "aac", "mp4", "m4v", "mov", "zip", "ipa", "gz",
        "bz2", "xz", "7z", "pvr", "pvrtc", "ogg", "caf", "car", "jar", "apk", "deb", "dmg",
    )
    /** Text-like formats: usually shrink to about a third. */
    private val TEXT = setOf(
        "plist", "strings", "txt", "xml", "json", "html", "htm", "css", "js", "log", "csv",
        "md", "h", "c", "m", "lua", "ini", "cfg", "svg",
    )

    fun ratio(name: String): Double {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            in COMPRESSED -> 1.0
            in TEXT -> 0.35
            else -> 0.65 // binaries, databases, nibs, fonts
        }
    }

    /** Per-entry zip overhead: local header + central directory record + the name twice. */
    fun entryOverhead(zipPath: String) = 30L + 46L + 2L * zipPath.toByteArray().size

    fun estimateFile(zipPath: String, size: Long) = (size * ratio(zipPath)).toLong() + entryOverhead(zipPath)
}

class CancelledException : RuntimeException("Cancelled")

/**
 * Walks [path] on the device, totalling files and bytes (symlinks skipped, like AfcZipper).
 * [onProgress] is called after each file; [isCancelled] is polled to abort with CancelledException.
 */
fun AfcClient.measureTree(
    path: String,
    zipPrefix: String = path.trimEnd('/').substringAfterLast('/') + "/",
    onProgress: (TreeSize) -> Unit = {},
    isCancelled: () -> Boolean = { false },
): TreeSize {
    val total = TreeSize()
    fun walk(p: String, zipPath: String) {
        for (name in readDir(p)) {
            if (isCancelled()) throw CancelledException()
            val child = if (p.endsWith("/")) p + name else "$p/$name"
            val e = runCatching { stat(child) }.getOrNull() ?: continue
            when {
                e.isLink -> {}
                e.isDir -> {
                    total.estimatedZipBytes += ZipEstimate.entryOverhead("$zipPath$name/")
                    walk(child, "$zipPath$name/")
                }
                else -> {
                    total.files++
                    total.bytes += e.size
                    total.estimatedZipBytes += ZipEstimate.estimateFile("$zipPath$name", e.size)
                    onProgress(total)
                }
            }
        }
    }
    total.estimatedZipBytes += 22 + ZipEstimate.entryOverhead(zipPrefix) // end-of-central-directory + folder entry
    walk(path, zipPrefix)
    return total
}
