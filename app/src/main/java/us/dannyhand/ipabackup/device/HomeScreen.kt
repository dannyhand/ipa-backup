package us.dannyhand.ipabackup.device

/**
 * SpringBoard's icon state (from springboardservices getIconState, formatVersion 2): a list of pages,
 * the first being the dock; each page is a list of icons, and a folder is a dict with "listType" =
 * "folder", a displayName and its own "iconLists". Restoring sends the same structure back with
 * setIconState; SpringBoard drops icons for apps that aren't installed and adds new apps at the end.
 */
object HomeScreen {
    private fun label(icon: Map<*, *>): String =
        (icon["displayName"] as? String)?.takeIf { it.isNotBlank() }
            ?: (icon["displayIdentifier"] as? String) ?: (icon["bundleIdentifier"] as? String) ?: "?"

    private fun isFolder(icon: Map<*, *>) = icon["listType"] == "folder" || icon["iconLists"] != null

    /** Readable outline of the layout. */
    fun summary(state: List<*>): String = buildString {
        state.forEachIndexed { i, page ->
            append(if (i == 0) "Dock" else "Page $i").append('\n')
            for (icon in page as? List<*> ?: emptyList<Any>()) {
                val m = icon as? Map<*, *> ?: continue
                if (isFolder(m)) {
                    append("  📁 ").append(label(m)).append('\n')
                    (m["iconLists"] as? List<*>)?.forEach { sub ->
                        (sub as? List<*>)?.forEach { f -> (f as? Map<*, *>)?.let { append("      ").append(label(it)).append('\n') } }
                    }
                } else append("  ").append(label(m)).append('\n')
            }
        }
    }

    /** Pages (not counting the dock), folders and apps. */
    fun counts(state: List<*>): Triple<Int, Int, Int> {
        var folders = 0
        var apps = 0
        fun walk(list: List<*>) {
            for (icon in list) {
                val m = icon as? Map<*, *> ?: continue
                if (isFolder(m)) { folders++; (m["iconLists"] as? List<*>)?.forEach { (it as? List<*>)?.let(::walk) } } else apps++
            }
        }
        state.forEach { (it as? List<*>)?.let(::walk) }
        return Triple((state.size - 1).coerceAtLeast(0), folders, apps)
    }

    /** A sanity check before sending a picked file to SpringBoard. */
    fun looksValid(state: Any?): Boolean =
        state is List<*> && state.isNotEmpty() && state.all { it is List<*> } &&
            state.flatMap { it as List<*> }.all { it is Map<*, *> }
}
