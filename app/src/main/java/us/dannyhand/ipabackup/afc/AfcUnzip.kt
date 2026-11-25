package us.dannyhand.ipabackup.afc

import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Writes a zip's entries onto the device under [destRoot] (e.g. an app's sandbox via house_arrest).
 * Entries whose top-level folder isn't in [allowedTop], and any path trying to escape with "..", are skipped.
 * Existing files are overwritten; files not in the zip are left alone. Returns the number of files written.
 */
fun AfcClient.unzipInto(
    zip: InputStream,
    destRoot: String,
    allowedTop: Set<String>,
    onFile: (String) -> Unit = {},
): Int {
    val made = HashSet<String>()
    fun ensureDir(dir: String) {
        if (dir.isEmpty() || dir == "/" || dir in made) return
        makeDir(dir) // mkdir -p semantics
        var d = dir
        while (d.isNotEmpty() && d != "/") { made += d; d = d.substringBeforeLast('/', "") }
    }
    val root = destRoot.trimEnd('/')
    var count = 0
    ZipInputStream(zip).use { z ->
        while (true) {
            val e = z.nextEntry ?: break
            val name = e.name.replace('\\', '/').trimStart('/')
            val parts = name.split('/').filter { it.isNotEmpty() }
            if (parts.isEmpty() || parts.any { it == ".." || it == "." } || parts[0] !in allowedTop) continue
            val target = "$root/" + parts.joinToString("/")
            if (e.isDirectory) {
                ensureDir(target)
            } else {
                ensureDir(target.substringBeforeLast('/'))
                writeFile(target, object : InputStream() { // don't let writeFile close the zip
                    override fun read() = z.read()
                    override fun read(b: ByteArray, off: Int, len: Int) = z.read(b, off, len)
                })
                count++
                onFile(name)
            }
        }
    }
    return count
}
