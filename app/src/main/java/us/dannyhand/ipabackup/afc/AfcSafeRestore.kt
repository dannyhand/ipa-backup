package us.dannyhand.ipabackup.afc

import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Puts files from a zip onto the device without ever leaving a half-written file in place (used for
 * the music library database, where a broken file would empty the Music app):
 *  1. every file is written next to its target as "<name>.ipabackup-new"; if anything fails (e.g. the
 *     cable comes out), the temporary files are removed where possible and the device is untouched
 *  2. only then, for each file: the current one is renamed to "<name>.backup" (replacing an older
 *     .backup) and the new one is renamed into place. Renames are instant, so the window is tiny.
 * [retire] lists extra device files to move aside to .backup in step 2 (stale SQLite -wal/-shm files).
 * Leftover .ipabackup-new files from an interrupted step 1 are found by Tools › Clean up.
 */
object AfcSafeRestore {
    const val NEW_SUFFIX = ".ipabackup-new"
    const val BACKUP_SUFFIX = ".backup"

    class Result(val files: Int, val replaced: Int)

    fun restore(
        afc: AfcClient,
        zip: InputStream,
        allowedTop: Set<String>,
        retire: List<String> = emptyList(),
        onFile: (String) -> Unit = {},
    ): Result {
        val targets = ArrayList<String>()
        val made = HashSet<String>()
        fun ensureDir(dir: String) {
            if (dir.isEmpty() || dir == "/" || dir in made) return
            afc.makeDir(dir)
            made += dir
        }
        try {
            ZipInputStream(zip).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    if (e.isDirectory) continue
                    val parts = e.name.replace('\\', '/').split('/').filter { it.isNotEmpty() }
                    if (parts.isEmpty() || parts.any { it == ".." || it == "." } || parts[0] !in allowedTop) continue
                    val target = "/" + parts.joinToString("/")
                    ensureDir(target.substringBeforeLast('/'))
                    targets += target // before writing, so a half-written temp file is cleaned up too
                    afc.writeFile(target + NEW_SUFFIX, object : InputStream() { // don't let writeFile close the zip
                        override fun read() = z.read()
                        override fun read(b: ByteArray, off: Int, len: Int) = z.read(b, off, len)
                    })
                    onFile(target)
                }
            }
        } catch (e: Exception) {
            for (t in targets) runCatching { afc.remove(t + NEW_SUFFIX) }
            throw e
        }
        return Result(targets.size, swap(afc, targets, retire))
    }

    /** The same, for files given directly: device path → contents. Written in order, then swapped in together. */
    fun replaceFiles(
        afc: AfcClient,
        files: List<Pair<String, () -> InputStream>>,
        retire: List<String> = emptyList(),
        onFile: (String) -> Unit = {},
    ): Result {
        val targets = ArrayList<String>()
        try {
            for ((target, open) in files) {
                afc.makeDir(target.substringBeforeLast('/'))
                targets += target
                open().use { afc.writeFile(target + NEW_SUFFIX, it) }
                onFile(target)
            }
        } catch (e: Exception) {
            for (t in targets) runCatching { afc.remove(t + NEW_SUFFIX) }
            throw e
        }
        return Result(targets.size, swap(afc, targets, retire))
    }

    /** Step 2: current files to .backup, new ones into place. Returns how many existing files were replaced. */
    private fun swap(afc: AfcClient, targets: List<String>, retire: List<String>): Int {
        fun exists(p: String) = runCatching { afc.fileInfo(p) }.isSuccess
        fun moveAside(p: String): Boolean {
            if (!exists(p)) return false
            if (exists(p + BACKUP_SUFFIX)) afc.remove(p + BACKUP_SUFFIX)
            afc.rename(p, p + BACKUP_SUFFIX)
            return true
        }
        var replaced = 0
        for (p in retire) if (p !in targets) moveAside(p)
        for (t in targets) {
            if (moveAside(t)) replaced++
            afc.rename(t + NEW_SUFFIX, t)
        }
        return replaced
    }
}
