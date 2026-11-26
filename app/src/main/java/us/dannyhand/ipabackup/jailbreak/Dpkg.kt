package us.dannyhand.ipabackup.jailbreak

import us.dannyhand.ipabackup.util.HtmlPage

/**
 * Cydia's package database: /var/lib/dpkg/status lists every package dpkg knows, one stanza per package
 * ("Field: value" lines, continuation lines start with a space). Repos ("sources") are apt lines in
 * the .list files in /etc/apt/sources.list.d, e.g. "deb http://apt.thebigboss.org/repofiles/cydia/ stable main".
 */
object Dpkg {
    const val STATUS = "/var/lib/dpkg/status"
    const val SOURCES_DIR = "/etc/apt/sources.list.d"
    const val SOURCES_FILE = "/etc/apt/sources.list"

    data class Package(val id: String, val name: String, val version: String, val section: String, val author: String, val description: String)

    fun parseStatus(text: String): List<Package> {
        val out = ArrayList<Package>()
        for (stanza in text.split(Regex("\\n\\s*\\n"))) {
            val f = HashMap<String, String>()
            var last: String? = null
            for (line in stanza.lines()) {
                if (line.startsWith(" ") || line.startsWith("\t")) { last?.let { f[it] = f[it] + "\n" + line.trim() }; continue }
                val c = line.indexOf(':')
                if (c <= 0) continue
                last = line.substring(0, c)
                f[last] = line.substring(c + 1).trim()
            }
            val id = f["Package"] ?: continue
            // Only what's actually installed (not removed or half-configured leftovers).
            if (f["Status"]?.endsWith(" installed") != true) continue
            out += Package(
                id = id,
                name = f["Name"] ?: id,
                version = f["Version"] ?: "",
                section = f["Section"] ?: "",
                author = (f["Author"] ?: f["Maintainer"] ?: "").substringBefore('<').trim(),
                description = f["Description"]?.lineSequence()?.firstOrNull() ?: "",
            )
        }
        return out.sortedBy { it.name.lowercase() }
    }

    /** Repo URLs from apt source lists. */
    fun parseSources(text: String): List<String> =
        text.lines().map { it.trim() }.filter { it.startsWith("deb ") }
            .mapNotNull { it.split(Regex("\\s+")).getOrNull(1) }

    /**
     * Apple's and the jailbreak's own base packages (firmware, base, cydia, apt…) clutter the list;
     * they're always there, so the "tweaks" view leaves them out. Their section is usually one of these.
     */
    private val SYSTEM_SECTIONS = setOf("System", "Packaging", "Terminal_Support", "Development", "Repositories")
    private val SYSTEM_IDS = Regex("^(gsc\\.|cy\\+|firmware|base|essential|apr|apt|bash|coreutils|cydia|darwintools|debianutils|diffutils|dpkg|findutils|gnupg|grep|gzip|lzma|ncurses|profile\\.d|readline|sed|shell-cmds|system-cmds|tar|uikittools|berkeleydb|com\\.saurik\\.substrate\\.safemode|mobilesubstrate|pam|pcre|libgcrypt|libgpg-error|bzip2|xz|zlib|openssl|org\\.thebigboss\\.repo\\.icons|com\\.ipodtouchfans|cydia-lproj)")

    fun isSystem(p: Package) = p.section in SYSTEM_SECTIONS || SYSTEM_IDS.containsMatchIn(p.id)

    fun html(device: String, packages: List<Package>, sources: List<String>, date: String): String {
        val tweaks = packages.filter { !isSystem(it) }
        val system = packages.filter { isSystem(it) }
        fun rows(list: List<Package>) = list.map { p ->
            listOf(HtmlPage.esc(p.name), "<code>${HtmlPage.esc(p.id)}</code>", HtmlPage.esc(p.version), HtmlPage.esc(p.section),
                HtmlPage.esc(p.author), HtmlPage.esc(p.description))
        }
        val h = listOf("Name", "Package ID", "Version", "Section", "Author", "Description")
        val body = buildString {
            append("<p class=\"sub\">$date · ${tweaks.size} tweaks and apps, ${system.size} system packages, ${sources.size} sources</p>\n")
            append("<h2>Sources</h2>\n<ul>\n")
            sources.forEach { append("<li><a href=\"").append(HtmlPage.esc(it)).append("\">").append(HtmlPage.esc(it)).append("</a></li>\n") }
            append("</ul>\n<h2>Tweaks and apps</h2>\n").append(HtmlPage.table(h, rows(tweaks)))
            append("<h2>System packages</h2>\n").append(HtmlPage.table(h, rows(system)))
        }
        return HtmlPage.page("Cydia packages on $device", body)
    }

    /** Plain list: sources, then one package ID per line (handy for reinstalling after a restore). */
    fun text(packages: List<Package>, sources: List<String>): String = buildString {
        append("# Sources\n"); sources.forEach { append(it).append('\n') }
        append("\n# Tweaks and apps (package IDs)\n"); packages.filter { !isSystem(it) }.forEach { append(it.id).append("  # ").append(it.name).append(' ').append(it.version).append('\n') }
    }
}
