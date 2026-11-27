package us.dannyhand.ipabackup.device

/**
 * WinterBoard (the jailbreak theming tweak) replaces the wallpapers with files from the active themes:
 *  - its settings, /var/mobile/Library/Preferences/com.saurik.WinterBoard.plist, list the installed
 *    themes under "Themes" as {Name, Active}, highest priority first
 *  - each theme is a folder in /Library/Themes, named "<Name>.theme" or just "<Name>"
 *  - the home screen wallpaper is Wallpaper.* and the lock screen's LockBackground.*: a picture (PNG, JPEG),
 *    a video (MP4/MOV), or an HTML page for animated ones, which can load other files from the theme
 *  - for each wallpaper, the first active theme that has one wins
 */
object WinterBoard {
    const val PREFS = "/var/mobile/Library/Preferences/com.saurik.WinterBoard.plist"
    const val THEMES_DIR = "/Library/Themes"

    enum class Kind(val base: String, val label: String) {
        HOME("Wallpaper", "Home Screen Wallpaper"),
        LOCK("LockBackground", "Lock Screen Wallpaper"),
    }

    /** Names of the active themes, highest priority first. */
    fun activeThemes(prefs: Any?): List<String> =
        ((prefs as? Map<*, *>)?.get("Themes") as? List<*>).orEmpty().mapNotNull { t ->
            val m = t as? Map<*, *> ?: return@mapNotNull null
            (m["Name"] as? String)?.takeIf { m["Active"] == true }
        }

    /** Candidate folders for a theme name. */
    fun themeDirs(name: String) = listOf("$THEMES_DIR/$name.theme", "$THEMES_DIR/$name")

    private val EXT_ORDER = listOf("html", "htm", "mp4", "m4v", "mov", "gif", "png", "jpg", "jpeg")

    /**
     * The file in a theme folder that WinterBoard would use for [kind], if any: animated ones (HTML,
     * video, GIF) before still pictures, and the Retina (@2x) picture before the plain one.
     */
    fun pick(files: List<String>, kind: Kind): String? {
        val re = Regex("^${kind.base}(@2x)?(~iphone)?\\.(\\w+)$", RegexOption.IGNORE_CASE)
        return files.mapNotNull { f -> re.find(f)?.let { m -> Triple(f, m.groupValues[1].isNotEmpty(), m.groupValues[3].lowercase()) } }
            .filter { it.third in EXT_ORDER }
            .sortedWith(compareBy({ EXT_ORDER.indexOf(it.third) }, { if (it.second) 0 else 1 }))
            .firstOrNull()?.first
    }

    fun isAnimatedPage(file: String) = file.substringAfterLast('.').lowercase() in setOf("html", "htm")
}
