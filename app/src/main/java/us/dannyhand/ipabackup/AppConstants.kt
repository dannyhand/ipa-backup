package us.dannyhand.ipabackup

/** Constants shared by MainActivity and its feature files. */

internal const val ACTION_USB_PERMISSION = "us.dannyhand.ipabackup.USB_PERMISSION"
/** Device path of an upload in progress; set while copying, cleared when done. */
internal const val PREF_PARTIAL_UPLOAD = "partial_upload"
/** Root-filesystem AFC, installed by the "Apple File Conduit 2" jailbreak package. */
internal const val AFC2_SERVICE = "com.apple.afc2"
internal const val FILES_SUBFOLDER = "Files"
internal const val MUSIC_SUBFOLDER = "Music"
internal const val PHOTOS_SUBFOLDER = "Photos"
internal const val MEMOS_SUBFOLDER = "Voice Memos"
internal const val BOOKS_SUBFOLDER = "Books"
internal const val PERSONAL_SUBFOLDER = "Personal"
internal const val CRASH_SUBFOLDER = "Crash Reports"
internal const val LOGS_SUBFOLDER = "Logs"
internal const val PLAYLISTS_SUBFOLDER = "Playlists"
internal const val MUSICLIB_SUBFOLDER = "Music Library Backups"
internal const val LIB_DIR = "/iTunes_Control/iTunes"
internal const val ART_DIR = "/iTunes_Control/Artwork"
/** Name of a book copied in by Books › Add (a 16-hex-digit Persistent ID). */
internal val BOOK_COPY = Regex("^[0-9A-F]{16}\\.(epub|pdf)$")
internal const val THUMB_PX = 200
/** Formats we can write song info into (DRM .m4p is left untouched). */
internal val TAGGABLE = setOf("m4a", "m4b", "m4r", "aac", "mp3")
