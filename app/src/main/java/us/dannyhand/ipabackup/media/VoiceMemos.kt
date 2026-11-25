package us.dannyhand.ipabackup.media

import us.dannyhand.ipabackup.music.SqlDb
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Voice Memos live in the media folder's /Recordings (no jailbreak needed), with names and labels in
 * /Recordings/Recordings.db. Its schema varies by iOS version, so this looks for a table with a path
 * column plus whatever date / duration / label columns exist, and falls back to the file listing.
 */
object VoiceMemos {
    const val DIR = "/Recordings"
    const val DB_PATH = "/Recordings/Recordings.db"
    val AUDIO_EXT = setOf("m4a", "caf", "aac", "wav", "mp3")
    private val PRESETS = listOf("Memo", "Podcast", "Interview", "Lecture", "Idea", "Meeting")

    data class Memo(
        val fileName: String,
        val devicePath: String,
        val dateMs: Long,
        val durationSec: Double?,
        val label: String?,
        val size: Long,
    ) {
        /** e.g. "2013-06-01 12.30.05 - Interview.m4a" (sortable, no ':' for Windows). */
        fun saveName(): String {
            val d = SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.US).format(Date(dateMs))
            val l = label?.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")?.trim()?.takeIf { it.isNotEmpty() && it != "Memo" }
            return "$d${l?.let { " - $it" } ?: ""}.${fileName.substringAfterLast('.', "m4a").lowercase()}"
        }
    }

    class DbInfo(val label: String?, val dateMs: Long?, val durationSec: Double?)

    /** Seconds since 1970 or since 2001 (Core Data); small values are the 2001 epoch. */
    fun toMs(v: Any?): Long? {
        val d = (v as? Number)?.toDouble() ?: (v as? String)?.toDoubleOrNull() ?: return null
        if (d <= 0) return null
        return if (d < 900_000_000) 978_307_200_000L + (d * 1000).toLong() else (d * 1000).toLong()
    }

    /** File name (lowercase) -> metadata from Recordings.db. */
    fun readDb(db: SqlDb): Map<String, DbInfo> {
        val out = HashMap<String, DbInfo>()
        for (t in db.tables()) {
            val cols = db.columns(t)
            fun find(vararg keys: String) = cols.firstOrNull { c -> keys.any { c.lowercase().contains(it) } }
            val path = find("path", "filename") ?: continue
            val date = find("date")
            val duration = find("duration")
            val custom = find("customlabel", "label", "name", "title")
            val preset = cols.firstOrNull { it.lowercase().contains("preset") }
            val sel = listOf(path, date, duration, custom, preset).map { it ?: "NULL" }.joinToString(", ") { if (it == "NULL") it else "\"$it\"" }
            for (r in db.query("SELECT $sel FROM \"$t\"")) {
                val p = (r[0] as? String)?.substringAfterLast('/')?.lowercase() ?: continue
                val label = (r[3] as? String)?.trim()?.ifEmpty { null }
                    ?: (r[4] as? Number)?.toInt()?.let { PRESETS.getOrNull(it) }
                out[p] = DbInfo(label, toMs(r[1]), (r[2] as? Number)?.toDouble())
            }
        }
        return out
    }

    /** Recordings named like "20130601 123005.m4a" carry their date in the name. */
    fun dateFromName(name: String): Long? {
        val m = Regex("(\\d{8}) (\\d{6})").find(name) ?: return null
        return runCatching { SimpleDateFormat("yyyyMMdd HHmmss", Locale.US).parse(m.value)?.time }.getOrNull()
    }

    data class FileEntry(val name: String, val size: Long, val mtimeMs: Long)

    fun combine(files: List<FileEntry>, meta: Map<String, DbInfo>): List<Memo> = files
        .filter { it.name.substringAfterLast('.', "").lowercase() in AUDIO_EXT }
        .map { f ->
            val info = meta[f.name.lowercase()]
            Memo(
                fileName = f.name,
                devicePath = "$DIR/${f.name}",
                dateMs = info?.dateMs ?: dateFromName(f.name) ?: f.mtimeMs,
                durationSec = info?.durationSec,
                label = info?.label,
                size = f.size,
            )
        }.sortedByDescending { it.dateMs }
}
