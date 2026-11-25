package us.dannyhand.ipabackup.personal

import us.dannyhand.ipabackup.music.SqlDb
import java.io.IOException
import java.text.DateFormat
import java.util.Date

/**
 * Reads /var/mobile/Library/SMS/sms.db (needs AFC2) in the iOS 6+ layout: chat, message, handle,
 * chat_message_join, attachment, message_attachment_join. Dates are seconds since 2001
 * (nanoseconds on iOS 11+). iOS 5 and older used a different single-table layout and aren't supported.
 * Confirmed working on iOS 6.1.6. Attachment paths are stored as "~/Library/SMS/Attachments/…";
 * some may be gone from the device, and the export notes how many.
 */
class MessagesDb(private val db: SqlDb) {
    companion object { const val DB_PATH = "/var/mobile/Library/SMS/sms.db" }

    data class Attachment(val id: Long, val devicePath: String?, val mime: String?, val name: String, val size: Long) {
        val isImage get() = mime?.startsWith("image/") == true
    }
    data class Message(val id: Long, val dateMs: Long, val fromMe: Boolean, val text: String, val sender: String?, val service: String?, val attachments: List<Attachment>)
    data class Chat(val id: Long, val identifier: String, val displayName: String?, val service: String?, val participants: List<String>, val messages: List<Message>) {
        val lastMs get() = messages.lastOrNull()?.dateMs ?: 0L
    }

    private fun appleMs(v: Any?): Long {
        val d = (v as? Number)?.toDouble() ?: return 0L
        val secs = if (d > 1e11) d / 1e9 else d
        return AddressBook.APPLE_EPOCH_MS + (secs * 1000).toLong()
    }

    /** "~/Library/SMS/..." -> "/var/mobile/Library/SMS/..." */
    fun devicePath(p: String?): String? = p?.trim()?.ifEmpty { null }?.let { if (it.startsWith("~/")) "/var/mobile/" + it.removePrefix("~/") else it }

    fun load(): List<Chat> {
        val t = db.tables()
        for (needed in listOf("message", "chat", "chat_message_join", "handle"))
            if (needed !in t) throw IOException("Unsupported messages database (no $needed table; iOS 5 or older?)")
        val mc = db.columns("message")
        fun m(n: String) = if (n in mc) "m.$n" else "NULL"

        val handles = db.query("SELECT ROWID, id FROM handle").associate { (it[0] as Number).toLong() to (it[1] as? String).orEmpty() }

        val attachments = HashMap<Long, MutableList<Attachment>>()
        if ("attachment" in t && "message_attachment_join" in t) {
            val ac = db.columns("attachment")
            fun a(n: String) = if (n in ac) "a.$n" else "NULL"
            for (r in db.query("SELECT j.message_id, a.ROWID, ${a("filename")}, ${a("mime_type")}, ${a("transfer_name")}, ${a("total_bytes")} " +
                "FROM message_attachment_join j JOIN attachment a ON a.ROWID = j.attachment_id ORDER BY a.ROWID")) {
                val path = devicePath(r[2] as? String)
                attachments.getOrPut((r[0] as Number).toLong()) { ArrayList() } += Attachment(
                    id = (r[1] as Number).toLong(),
                    devicePath = path,
                    mime = r[3] as? String,
                    name = (r[4] as? String)?.ifEmpty { null } ?: path?.substringAfterLast('/') ?: "attachment",
                    size = (r[5] as? Number)?.toLong() ?: 0L,
                )
            }
        }

        val byChat = HashMap<Long, MutableList<Message>>()
        for (r in db.query("SELECT j.chat_id, m.ROWID, ${m("date")}, ${m("is_from_me")}, ${m("text")}, ${m("handle_id")}, ${m("service")}, ${m("subject")} " +
            "FROM chat_message_join j JOIN message m ON m.ROWID = j.message_id")) {
            val id = (r[1] as Number).toLong()
            val text = listOfNotNull((r[7] as? String)?.trim()?.ifEmpty { null }, (r[4] as? String))
                .joinToString("\n").replace("￼", "").trim() // U+FFFC marks where an attachment sat
            byChat.getOrPut((r[0] as Number).toLong()) { ArrayList() } += Message(
                id = id,
                dateMs = appleMs(r[2]),
                fromMe = ((r[3] as? Number)?.toInt() ?: 0) != 0,
                text = text,
                sender = handles[(r[5] as? Number)?.toLong() ?: -1],
                service = r[6] as? String,
                attachments = attachments[id].orEmpty(),
            )
        }

        val participants = HashMap<Long, MutableList<String>>()
        if ("chat_handle_join" in t) for (r in db.query("SELECT chat_id, handle_id FROM chat_handle_join")) {
            val h = handles[(r[1] as? Number)?.toLong() ?: -1] ?: continue
            participants.getOrPut((r[0] as Number).toLong()) { ArrayList() } += h
        }

        val cc = db.columns("chat")
        fun c(n: String) = if (n in cc) n else "NULL"
        return db.query("SELECT ROWID, ${c("chat_identifier")}, ${c("display_name")}, ${c("service_name")} FROM chat").mapNotNull { r ->
            val id = (r[0] as Number).toLong()
            val msgs = byChat[id]?.sortedWith(compareBy({ it.dateMs }, { it.id })) ?: return@mapNotNull null
            Chat(id, (r[1] as? String).orEmpty(), (r[2] as? String)?.trim()?.ifEmpty { null }, r[3] as? String,
                participants[id].orEmpty().ifEmpty { listOfNotNull((r[1] as? String)?.ifEmpty { null }) }, msgs)
        }.sortedByDescending { it.lastMs }
    }
}

/** Turns phone numbers / emails into contact names using the address book. */
class ContactNames(contacts: List<AddressBook.Contact>) {
    private val byKey = HashMap<String, String>()
    init {
        for (c in contacts) {
            c.phones.forEach { key(it.value)?.let { k -> byKey.putIfAbsent(k, c.displayName) } }
            c.emails.forEach { byKey.putIfAbsent(it.value.trim().lowercase(), c.displayName) }
        }
    }
    /** Phones match on their last 10 digits, so +1 (228) 555-0100 and 2285550100 are the same. */
    private fun key(phone: String): String? = phone.filter { it.isDigit() }.takeLast(10).takeIf { it.length >= 7 }

    fun name(handle: String?): String? {
        if (handle.isNullOrBlank()) return null
        return if ('@' in handle) byKey[handle.trim().lowercase()] else key(handle)?.let { byKey[it] }
    }

    fun display(handle: String?): String = name(handle) ?: handle ?: "Unknown"
}

object MessagesHtml {
    private fun esc(s: String) = NotesExport.escape(s)
    private fun body(s: String) = esc(s).replace("\n", "<br>")

    private const val CSS = "body{font-family:-apple-system,Roboto,sans-serif;max-width:46em;margin:0 auto;padding:1em;background:#fff;color:#111}" +
        "h1{font-size:1.3em}h2{font-size:1.1em;margin-top:2em;border-bottom:1px solid #ddd;padding-bottom:.3em}" +
        ".day{text-align:center;color:#888;font-size:.8em;margin:1.2em 0 .4em}" +
        ".m{display:flex;margin:.15em 0}.me{justify-content:flex-end}" +
        ".b{max-width:75%;padding:.45em .8em;border-radius:1.1em;line-height:1.35;word-wrap:break-word}" +
        ".me .b{background:#1e88e5;color:#fff}.them .b{background:#e9e9eb}.sms.me .b{background:#43a047}" +
        ".who{font-size:.72em;color:#888;margin:.5em .9em 0}.t{font-size:.7em;opacity:.65;margin-top:.15em}" +
        ".b img{max-width:100%;border-radius:.6em;display:block;margin:.2em 0}.att{font-size:.9em}" +
        "a{color:inherit}ul.chats{list-style:none;padding:0}ul.chats li{padding:.6em 0;border-bottom:1px solid #eee}" +
        ".sub{color:#888;font-size:.85em}"

    fun title(chat: MessagesDb.Chat, names: ContactNames): String =
        chat.displayName ?: chat.participants.joinToString(", ") { names.display(it) }.ifEmpty { chat.identifier }

    /** Bubbles for one conversation. [href] gives a link for an attachment, or null to show just its name. */
    fun conversation(chat: MessagesDb.Chat, names: ContactNames, dayFmt: DateFormat, timeFmt: DateFormat, href: (MessagesDb.Attachment) -> String?): String = buildString {
        val group = chat.participants.size > 1
        var lastDay = ""
        var lastSender: String? = "\u0000"
        for (m in chat.messages) {
            val day = dayFmt.format(Date(m.dateMs))
            if (day != lastDay) { append("<div class=\"day\">").append(esc(day)).append("</div>\n"); lastDay = day; lastSender = "\u0000" }
            val sender = if (m.fromMe) null else m.sender
            if (group && !m.fromMe && sender != lastSender) append("<div class=\"who\">").append(esc(names.display(sender))).append("</div>\n")
            lastSender = sender
            val sms = if (m.service.equals("SMS", true)) " sms" else ""
            append("<div class=\"m ${if (m.fromMe) "me" else "them"}$sms\"><div class=\"b\">")
            if (m.text.isNotEmpty()) append(body(m.text))
            for (a in m.attachments) {
                val link = href(a)
                when {
                    link != null && a.isImage -> append("<a href=\"").append(esc(link)).append("\"><img loading=\"lazy\" src=\"").append(esc(link)).append("\" alt=\"").append(esc(a.name)).append("\"></a>")
                    link != null -> append("<div class=\"att\">📎 <a href=\"").append(esc(link)).append("\">").append(esc(a.name)).append("</a></div>")
                    else -> append("<div class=\"att\">📎 ").append(esc(a.name)).append("</div>")
                }
            }
            append("<div class=\"t\">").append(esc(timeFmt.format(Date(m.dateMs)))).append("</div></div></div>\n")
        }
    }

    fun page(title: String, content: String) =
        "<!DOCTYPE html>\n<html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<title>${esc(title)}</title><style>$CSS</style></head><body>\n$content</body></html>\n"

    fun index(deviceName: String, chats: List<MessagesDb.Chat>, names: ContactNames, fileOf: (MessagesDb.Chat) -> String, dayFmt: DateFormat): String = buildString {
        append("<h1>Messages from ").append(esc(deviceName)).append("</h1>\n<p class=\"sub\">")
        append(chats.size).append(" conversations, ").append(chats.sumOf { it.messages.size }).append(" messages</p>\n<ul class=\"chats\">\n")
        for (c in chats) {
            append("<li><a href=\"").append(esc(fileOf(c))).append("\">").append(esc(title(c, names))).append("</a><div class=\"sub\">")
            append(c.messages.size).append(" messages · last ").append(esc(dayFmt.format(Date(c.lastMs)))).append("</div></li>\n")
        }
        append("</ul>\n")
    }
}
