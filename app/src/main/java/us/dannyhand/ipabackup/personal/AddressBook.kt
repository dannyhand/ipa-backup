package us.dannyhand.ipabackup.personal

import us.dannyhand.ipabackup.music.SqlDb
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Reads /var/mobile/Library/AddressBook/AddressBook.sqlitedb (needs AFC2) and writes vCard 3.0,
 * which Google Contacts, Android and Outlook import directly.
 *
 * Layout: ABPerson holds names; ABMultiValue holds phones (property 3), emails (4), addresses (5,
 * with parts in ABMultiValueEntry), dates (12), URLs (22); labels like "_$!<Mobile>!$_" live in ABMultiValueLabel.
 */
class AddressBook(private val db: SqlDb) {

    companion object {
        const val DB_PATH = "/var/mobile/Library/AddressBook/AddressBook.sqlitedb"
        const val PHONE = 3
        const val EMAIL = 4
        const val ADDRESS = 5
        const val URL = 22
        /** Core Data / CFAbsoluteTime epoch (2001-01-01) in Unix milliseconds. */
        const val APPLE_EPOCH_MS = 978_307_200_000L

        fun cleanLabel(raw: String?): String? = raw?.removePrefix("_\$!<")?.removeSuffix(">!\$_")?.trim()?.ifEmpty { null }
    }

    data class Labeled(val label: String?, val value: String)
    data class Address(val label: String?, val street: String?, val city: String?, val state: String?, val zip: String?, val country: String?)

    data class Contact(
        val id: Long,
        val first: String?, val last: String?, val middle: String?,
        val prefix: String?, val suffix: String?, val nickname: String?,
        val organization: String?, val department: String?, val jobTitle: String?,
        val note: String?, val birthdayMs: Long?,
        val phones: List<Labeled>, val emails: List<Labeled>, val urls: List<Labeled>, val addresses: List<Address>,
    ) {
        val displayName: String
            get() = listOfNotNull(prefix, first, middle, last, suffix).joinToString(" ").ifBlank { null }
                ?: organization ?: nickname ?: emails.firstOrNull()?.value ?: phones.firstOrNull()?.value ?: "(no name)"
    }

    fun load(): List<Contact> {
        val tables = db.tables()
        if ("ABPerson" !in tables) throw IOException("Not an address book (no ABPerson table)")
        val pc = db.columns("ABPerson")
        fun c(name: String) = if (name in pc) name else "NULL"

        val people = db.query(
            "SELECT ROWID, ${c("First")}, ${c("Last")}, ${c("Middle")}, ${c("Prefix")}, ${c("Suffix")}, ${c("Nickname")}, " +
                "${c("Organization")}, ${c("Department")}, ${c("JobTitle")}, ${c("Note")}, ${c("Birthday")} FROM ABPerson"
        )

        val labels: Map<Long, String> = if ("ABMultiValueLabel" in tables)
            db.query("SELECT ROWID, value FROM ABMultiValueLabel").associate { (it[0] as Number).toLong() to (it[1] as? String).orEmpty() }
        else emptyMap()

        val entryKeys: Map<Long, String> = if ("ABMultiValueEntryKey" in tables)
            db.query("SELECT ROWID, value FROM ABMultiValueEntryKey").associate { (it[0] as Number).toLong() to (it[1] as? String).orEmpty() }
        else emptyMap()

        // parent multivalue UID -> (key name -> value)
        val entries = HashMap<Long, HashMap<String, String>>()
        if ("ABMultiValueEntry" in tables) {
            for (r in db.query("SELECT parent_id, key, value FROM ABMultiValueEntry")) {
                val parent = (r[0] as? Number)?.toLong() ?: continue
                val key = entryKeys[(r[1] as? Number)?.toLong() ?: -1] ?: continue
                entries.getOrPut(parent) { HashMap() }[key] = (r[2] as? String) ?: continue
            }
        }

        class MV(val uid: Long, val property: Int, val label: String?, val value: String?)
        val byPerson = HashMap<Long, MutableList<MV>>()
        if ("ABMultiValue" in tables) {
            val mc = db.columns("ABMultiValue")
            val uid = if ("UID" in mc) "UID" else "ROWID"
            for (r in db.query("SELECT $uid, record_id, property, label, value FROM ABMultiValue ORDER BY $uid")) {
                val person = (r[1] as? Number)?.toLong() ?: continue
                byPerson.getOrPut(person) { ArrayList() } += MV(
                    uid = (r[0] as Number).toLong(),
                    property = (r[2] as? Number)?.toInt() ?: -1,
                    label = cleanLabel(labels[(r[3] as? Number)?.toLong() ?: -1]),
                    value = (r[4] as? String)?.trim(),
                )
            }
        }

        return people.map { r ->
            val id = (r[0] as Number).toLong()
            val mvs = byPerson[id].orEmpty()
            fun s(i: Int) = (r[i] as? String)?.trim()?.ifEmpty { null }
            fun labeled(prop: Int) = mvs.filter { it.property == prop && !it.value.isNullOrBlank() }.map { Labeled(it.label, it.value!!) }
            Contact(
                id = id, first = s(1), last = s(2), middle = s(3), prefix = s(4), suffix = s(5), nickname = s(6),
                organization = s(7), department = s(8), jobTitle = s(9), note = s(10),
                birthdayMs = parseAppleDate(r[11]),
                phones = labeled(PHONE), emails = labeled(EMAIL), urls = labeled(URL),
                addresses = mvs.filter { it.property == ADDRESS }.mapNotNull { mv ->
                    val e = entries[mv.uid] ?: return@mapNotNull null
                    Address(mv.label, e["Street"], e["City"], e["State"], e["ZIP"], e["Country"])
                },
            )
        }.filter { it.displayName != "(no name)" || it.phones.isNotEmpty() || it.emails.isNotEmpty() }
            .sortedBy { (it.last ?: it.first ?: it.displayName).lowercase() }
    }

    /** ABPerson.Birthday is a CFAbsoluteTime stored as text or a number. */
    private fun parseAppleDate(v: Any?): Long? {
        val secs = when (v) {
            is Number -> v.toDouble()
            is String -> v.trim().toDoubleOrNull()
            else -> null
        } ?: return null
        return APPLE_EPOCH_MS + (secs * 1000).toLong()
    }
}

object VCard {
    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "")
        .replace(",", "\\,").replace(";", "\\;")

    private fun type(label: String?, kind: Int): String {
        val l = label?.lowercase() ?: return ""
        val t = when {
            kind == AddressBook.PHONE && (l == "mobile" || l == "iphone") -> "CELL"
            kind == AddressBook.PHONE && l.contains("fax") -> if (l.contains("work")) "WORK,FAX" else "HOME,FAX"
            kind == AddressBook.PHONE && l == "pager" -> "PAGER"
            kind == AddressBook.PHONE && l == "main" -> "MAIN"
            l == "home" -> "HOME"
            l == "work" -> "WORK"
            else -> null
        }
        return if (t != null) ";TYPE=$t" else ""
    }

    /** RFC 2425 line folding: lines over 75 characters continue with a leading space. */
    private fun fold(line: String): String {
        if (line.length <= 75) return line
        val sb = StringBuilder(line.substring(0, 75))
        var i = 75
        while (i < line.length) {
            sb.append("\r\n ").append(line, i, minOf(i + 74, line.length))
            i += 74
        }
        return sb.toString()
    }

    fun write(contacts: List<AddressBook.Contact>): String {
        val out = StringBuilder()
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        for (c in contacts) {
            val lines = ArrayList<String>()
            lines += "BEGIN:VCARD"
            lines += "VERSION:3.0"
            lines += "N:" + listOf(c.last, c.first, c.middle, c.prefix, c.suffix).joinToString(";") { esc(it ?: "") }
            lines += "FN:" + esc(c.displayName)
            c.nickname?.let { lines += "NICKNAME:" + esc(it) }
            if (c.organization != null || c.department != null)
                lines += "ORG:" + esc(c.organization ?: "") + (c.department?.let { ";" + esc(it) } ?: "")
            c.jobTitle?.let { lines += "TITLE:" + esc(it) }
            // Birthdays are stored at local midnight; +12h keeps the date right in any time zone.
            c.birthdayMs?.let { lines += "BDAY:" + day.format(Date(it + 12 * 3600 * 1000)) }
            c.phones.forEach { lines += "TEL${type(it.label, AddressBook.PHONE)}:" + esc(it.value) }
            c.emails.forEach { lines += "EMAIL;TYPE=INTERNET${type(it.label, AddressBook.EMAIL).replace(";TYPE=", ",")}:" + esc(it.value) }
            c.addresses.forEach { a ->
                lines += "ADR${type(a.label, AddressBook.ADDRESS)}:;;" +
                    listOf(a.street, a.city, a.state, a.zip, a.country).joinToString(";") { esc(it ?: "") }
            }
            c.urls.forEach { lines += "URL:" + esc(it.value) }
            c.note?.let { lines += "NOTE:" + esc(it) }
            lines += "END:VCARD"
            lines.forEach { out.append(fold(it)).append("\r\n") }
        }
        return out.toString()
    }
}
