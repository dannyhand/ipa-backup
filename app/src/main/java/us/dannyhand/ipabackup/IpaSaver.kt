package us.dannyhand.ipabackup

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Writes backups to Download/IPA Backup/ (MediaStore on Android 10+, app storage before that).
 * Re-saving a file with the same name replaces it.
 */
class IpaSaver(private val context: Context) {

    companion object {
        const val FOLDER = "IPA Backup"
        private val BAD = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")
        fun clean(s: String) = s.replace(BAD, "_").trim()
        fun ipaName(name: String, version: String, bundleId: String) =
            "${clean(name)} ${clean(version)} (${clean(bundleId)}).ipa"
        fun dataName(name: String, version: String, bundleId: String) =
            "${clean(name)} ${clean(version)} (${clean(bundleId)}) data.zip"
    }

    class Target(val stream: OutputStream, val location: String, private val finish: (Boolean) -> Unit) {
        fun commit() { stream.close(); finish(true) }
        fun discard() { runCatching { stream.close() }; finish(false) }
    }

    private fun relPath(sub: String?) =
        "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER/" + (sub?.let { "${clean(it)}/" } ?: "")

    private fun legacyDir(sub: String?) =
        File(context.getExternalFilesDir(null), FOLDER + (sub?.let { "/${clean(it)}" } ?: "")).apply { mkdirs() }

    /** Names of non-empty files already saved in the folder (by this app). */
    fun existingNames(sub: String? = null): Set<String> {
        if (Build.VERSION.SDK_INT < 29) {
            return legacyDir(sub).listFiles()?.filter { it.length() > 0 }?.map { it.name }?.toSet() ?: emptySet()
        }
        val names = HashSet<String>()
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads.DISPLAY_NAME, MediaStore.Downloads.SIZE),
            "${MediaStore.Downloads.RELATIVE_PATH} = ? AND ${MediaStore.Downloads.IS_PENDING} = 0",
            arrayOf(relPath(sub)), null,
        )?.use { c ->
            while (c.moveToNext()) if (c.getLong(1) > 0) names += c.getString(0)
        }
        return names
    }

    private fun findUris(fileName: String, sub: String?): List<Uri> {
        val uris = mutableListOf<Uri>()
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.RELATIVE_PATH} = ? AND ${MediaStore.Downloads.DISPLAY_NAME} = ?",
            arrayOf(relPath(sub), fileName), null,
        )?.use { c ->
            while (c.moveToNext()) uris += ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(0))
        }
        return uris
    }

    fun create(fileName: String, sub: String? = null): Target {
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            findUris(fileName, sub).forEach { runCatching { resolver.delete(it, null, null) } } // replace old copy
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, if (fileName.endsWith(".zip")) "application/zip" else "application/octet-stream")
                put(MediaStore.Downloads.RELATIVE_PATH, relPath(sub))
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri: Uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw java.io.IOException("Couldn't create $fileName in Downloads")
            val out = resolver.openOutputStream(uri) ?: throw java.io.IOException("Couldn't open $uri")
            return Target(out.buffered(1 shl 16), relPath(sub).replace(Environment.DIRECTORY_DOWNLOADS, "Download") + fileName) { ok ->
                if (ok) {
                    resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
                } else {
                    resolver.delete(uri, null, null)
                }
            }
        } else {
            val f = File(legacyDir(sub), fileName)
            return Target(FileOutputStream(f).buffered(1 shl 16), f.absolutePath) { ok -> if (!ok) f.delete() }
        }
    }
}
