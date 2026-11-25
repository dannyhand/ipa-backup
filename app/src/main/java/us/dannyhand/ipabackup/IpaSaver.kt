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
 *
 * Android 10+ only lets an app see Downloads entries it created itself, so "already saved" checks only know
 * about files from this install. Reinstalling with a different signing key (e.g. debug -> release) loses that.
 */
class IpaSaver(private val context: Context) {

    companion object {
        const val FOLDER = "IPA Backup"
        private val BAD = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")
        fun clean(s: String) = s.replace(BAD, "_").trim()
        fun ipaName(name: String, version: String, bundleId: String) =
            "${clean(name)} ${clean(version)} (${clean(bundleId)}).ipa"
        fun mimeType(fileName: String) = when (fileName.substringAfterLast('.', "").lowercase()) {
            "zip" -> "application/zip"
            "mp3" -> "audio/mpeg"
            "m4a", "m4p", "m4b", "aac" -> "audio/mp4"
            "wav" -> "audio/wav"
            "aif", "aiff" -> "audio/aiff"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "vcf" -> "text/x-vcard"
            "ics" -> "text/calendar"
            "html", "htm" -> "text/html"
            "txt" -> "text/plain"
            "epub" -> "application/epub+zip"
            "pdf" -> "application/pdf"
            "heic" -> "image/heic"
            "mov" -> "video/quicktime"
            "mp4", "m4v" -> "video/mp4"
            "jpg", "jpeg" -> "image/jpeg"
            else -> "application/octet-stream"
        }
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

    data class Saved(val name: String, val uri: Uri, val size: Long, val modifiedMs: Long)

    /** Files this app saved in the folder whose names end with [suffix] (e.g. ".ipa", " data.zip"). */
    fun listSaved(suffix: String, sub: String? = null): List<Saved> {
        if (Build.VERSION.SDK_INT < 29) {
            return legacyDir(sub).listFiles()?.filter { it.name.endsWith(suffix, true) && it.length() > 0 }
                ?.map { Saved(it.name, Uri.fromFile(it), it.length(), it.lastModified()) } ?: emptyList()
        }
        val out = ArrayList<Saved>()
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME, MediaStore.Downloads.SIZE, MediaStore.Downloads.DATE_MODIFIED),
            "${MediaStore.Downloads.RELATIVE_PATH} = ? AND ${MediaStore.Downloads.IS_PENDING} = 0",
            arrayOf(relPath(sub)), null,
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                if (!name.endsWith(suffix, true) || c.getLong(2) <= 0) continue
                out += Saved(name, ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(0)), c.getLong(2), c.getLong(3) * 1000)
            }
        }
        return out
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

    /** [dateTakenMs] sets the gallery date for photos/videos (Android 10+). */
    fun create(fileName: String, sub: String? = null, dateTakenMs: Long? = null): Target {
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            findUris(fileName, sub).forEach { runCatching { resolver.delete(it, null, null) } } // replace old copy
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType(fileName))
                put(MediaStore.Downloads.RELATIVE_PATH, relPath(sub))
                put(MediaStore.Downloads.IS_PENDING, 1)
                if (dateTakenMs != null && dateTakenMs > 0) put(MediaStore.MediaColumns.DATE_TAKEN, dateTakenMs)
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
