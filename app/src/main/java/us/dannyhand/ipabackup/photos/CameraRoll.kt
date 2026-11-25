package us.dannyhand.ipabackup.photos

import us.dannyhand.ipabackup.afc.AfcClient

/**
 * Photos and videos in the iPod's camera roll (AFC media root: /DCIM/1xxAPPLE/). No jailbreak needed.
 * Dates come from the files' modification times, i.e. the device clock when taken; a device whose clock
 * reset to 1970 (flat battery) produces 1970-dated photos until the clock is set again.
 */
object CameraRoll {
    val PHOTO_EXT = setOf("jpg", "jpeg", "png", "gif", "heic", "tif", "tiff")
    val VIDEO_EXT = setOf("mov", "mp4", "m4v")

    data class Media(val path: String, val name: String, val size: Long, val mtimeMs: Long, val isVideo: Boolean) {
        val extension get() = name.substringAfterLast('.', "").lowercase()
    }

    /** Newest first. [onProgress] gets the running count while files are examined. */
    fun list(afc: AfcClient, onProgress: (Int) -> Unit = {}): List<Media> {
        val folders = runCatching { afc.readDir("/DCIM") }.getOrDefault(emptyList()).sorted()
        val out = ArrayList<Media>()
        for (folder in folders) {
            val dir = "/DCIM/$folder"
            val names = runCatching { afc.readDir(dir) }.getOrNull() ?: continue // not a folder
            for (name in names) {
                val ext = name.substringAfterLast('.', "").lowercase()
                val isVideo = ext in VIDEO_EXT
                if (!isVideo && ext !in PHOTO_EXT) continue
                val e = runCatching { afc.stat("$dir/$name") }.getOrNull() ?: continue
                if (e.isDir) continue
                out += Media("$dir/$name", name, e.size, e.mtimeMs, isVideo)
                if (out.size % 50 == 0) onProgress(out.size)
            }
        }
        return out.sortedWith(compareByDescending<Media> { it.mtimeMs }.thenByDescending { it.name })
    }

    /** Where iOS keeps a video's poster frame, e.g. /PhotoData/Metadata/DCIM/100APPLE/IMG_0005.JPG */
    fun posterCandidates(m: Media): List<String> {
        val base = "/PhotoData/Metadata" + m.path.substringBeforeLast('.')
        return listOf("$base.JPG", "$base.THM", "$base.jpg")
    }
}
