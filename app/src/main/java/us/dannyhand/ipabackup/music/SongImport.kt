package us.dannyhand.ipabackup.music

import us.dannyhand.ipabackup.util.AudioInfo
import kotlin.random.Random

/**
 * Preparing songs to add to the device's music library: which files iOS can play, which are already
 * there, where each one goes, and fresh IDs for the new library rows. Nothing here touches the device.
 *
 * Where iTunes puts songs: /iTunes_Control/Music/F00 … F49 (the number of folders varies by device;
 * use the ones that exist), each file renamed to four random capital letters plus its extension, e.g.
 * F17/QWER.mp3. The library database maps those names back to titles, so the names carry no meaning.
 *
 * What iOS 6's Music app plays: MP3, AAC and Apple Lossless (in .m4a/.m4b), WAV and AIFF. Not FLAC, Ogg,
 * Opus or WMA (iTunes converts those before syncing). Protected .m4p files from the iTunes Store only
 * play on devices authorized for the account that bought them.
 */
object SongImport {
    const val MUSIC_DIR = "/iTunes_Control/Music"
    val PLAYABLE = setOf("mp3", "m4a", "m4b", "wav", "aif", "aiff")
    val UNSUPPORTED = setOf("flac", "ogg", "oga", "opus", "wma", "ape", "wv", "mka", "aac", "amr", "3gp", "mid", "midi")

    enum class Status(val label: String, val ready: Boolean) {
        READY("ready", true),
        DRM("iTunes Store DRM: plays only on an authorized device", true),
        DUPLICATE("already on the device", false),
        UNSUPPORTED("iOS 6 can't play this format", false),
        UNREADABLE("not an audio file this app can read", false),
    }

    class Candidate(
        val sourceName: String,
        val ext: String,
        val size: Long,
        val info: AudioInfo.Info?,
        var status: Status,
    ) {
        /** Title, artist and album with the file name as a fallback, the way iTunes fills in missing tags. */
        val title get() = info?.title ?: sourceName.substringBeforeLast('.')
        val artist get() = info?.artist ?: info?.albumArtist ?: MusicLibrary.UNKNOWN_ARTIST
        val album get() = info?.album ?: MusicLibrary.UNKNOWN_ALBUM
    }

    /** First pass, from the file alone: format and DRM. */
    fun classify(ext: String, info: AudioInfo.Info?): Status = when {
        ext in UNSUPPORTED -> Status.UNSUPPORTED
        info == null && ext in setOf("wav", "aif", "aiff") -> Status.READY // no tags to read, but playable
        info == null -> Status.UNREADABLE
        info.format == AudioInfo.Format.PROTECTED_AAC -> Status.DRM
        ext !in PLAYABLE && ext != "m4p" -> Status.UNSUPPORTED
        else -> Status.READY
    }

    /**
     * Key for spotting a song that's already in the library: title and artist (album too when both
     * sides have one), compared case-insensitively, ignoring punctuation, extra spaces and a leading
     * "The ", so "The Beatles - Help!" matches "beatles - help".
     */
    fun matchKey(title: String, artist: String, album: String? = null): String {
        fun norm(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().removePrefix("the ").trim()
        return norm(title) + "|" + norm(artist) + if (album != null && album != MusicLibrary.UNKNOWN_ALBUM) "|" + norm(album) else ""
    }

    /** Marks candidates already in the library, and repeats within the same batch. */
    fun markDuplicates(candidates: List<Candidate>, library: List<MusicLibrary.Track>) {
        val withAlbum = library.map { matchKey(it.title, it.artist, it.album) }.toHashSet()
        val noAlbum = library.map { matchKey(it.title, it.artist) }.toHashSet()
        val batch = HashSet<String>()
        for (c in candidates) {
            if (!c.status.ready) continue
            val hasAlbum = c.album != MusicLibrary.UNKNOWN_ALBUM
            val key = matchKey(c.title, c.artist, if (hasAlbum) c.album else null)
            // Without an album on our side, any album on the device counts as a match.
            val known = if (hasAlbum) key in withAlbum else matchKey(c.title, c.artist) in noAlbum
            if (known || !batch.add(key)) c.status = Status.DUPLICATE
        }
    }

    private const val LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"

    /**
     * Device paths for new files, iTunes style: a random existing Fxx folder and a 4-letter name not
     * already used in it. [existing] is folder name → file names in it (e.g. "F07" → {"ABCD.mp3"}),
     * and is updated so a batch never reuses a name. With no folders yet, F00 is used.
     */
    fun planPath(ext: String, existing: MutableMap<String, MutableSet<String>>, rnd: Random = Random.Default): String {
        if (existing.isEmpty()) existing["F00"] = HashSet()
        val folders = existing.keys.sorted()
        val folder = folders[rnd.nextInt(folders.size)]
        val used = existing.getValue(folder)
        while (true) {
            val name = (1..4).map { LETTERS[rnd.nextInt(LETTERS.length)] }.joinToString("") + "." + ext
            if (used.none { it.equals(name, ignoreCase = true) }) {
                used += name
                return "$MUSIC_DIR/$folder/$name"
            }
        }
    }

    /** Folder names that look like iTunes music folders (F00, F01…). */
    fun isMusicFolder(name: String) = name.matches(Regex("F\\d{2}"))

    /**
     * New persistent IDs for library rows. MediaLibrary uses random signed 64-bit numbers (like iTunes'
     * own persistent IDs); 0 is avoided, as are IDs already in use.
     */
    fun newPids(count: Int, inUse: Set<Long>, rnd: Random = Random.Default): List<Long> {
        val out = LinkedHashSet<Long>()
        while (out.size < count) {
            val id = rnd.nextLong()
            if (id != 0L && id !in inUse) out += id
        }
        return out.toList()
    }

    /** "3:07" or "1:02:03". */
    fun duration(ms: Long): String {
        val s = ms / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }
}
