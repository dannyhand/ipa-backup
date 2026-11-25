package us.dannyhand.ipabackup.pairing

import android.content.Context
import us.dannyhand.ipabackup.plist.Plist
import java.io.File
import java.util.UUID

/** Pair records in app-private storage, one XML plist per device UDID. */
class PairRecordStore(context: Context) {
    private val dir = File(context.filesDir, "pair_records").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("pairing", Context.MODE_PRIVATE)

    /** Stable identifier for this "host" (like a computer's SystemBUID in iTunes). */
    val systemBuid: String
        get() = prefs.getString("system_buid", null) ?: UUID.randomUUID().toString().uppercase().also {
            prefs.edit().putString("system_buid", it).apply()
        }

    private fun file(udid: String) = File(dir, "${udid.filter { it.isLetterOrDigit() }}.plist")

    @Suppress("UNCHECKED_CAST")
    fun load(udid: String): PairRecord? {
        val f = file(udid)
        if (!f.exists()) return null
        return runCatching { PairRecord.fromStorage(Plist.parse(f.readBytes()) as Map<String, Any?>) }.getOrNull()
    }

    fun save(udid: String, record: PairRecord) {
        file(udid).writeText(Plist.toXml(record.toStorage()))
    }

    fun delete(udid: String) { file(udid).delete() }
}
