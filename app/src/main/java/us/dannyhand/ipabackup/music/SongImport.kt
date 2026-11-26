package us.dannyhand.ipabackup.music

import us.dannyhand.ipabackup.util.AudioInfo
import kotlin.random.Random

/**
 * Preparing songs, podcasts, audiobooks, videos and ringtones to add to the device: which files iOS can
 * play, which are already there, where each one goes, and fresh IDs for the new library rows. Nothing
 * here touches the device.
 *
 * Everything except ringtones goes into the music library database (with a different media kind) and
 * its file into the Music folders below. Ringtones are separate: /iTunes_Control/Ringtones/XXXX.m4r,
 * listed in Ringtones.plist; they must be AAC and at most 40 seconds long (iOS's limit).
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
    const val RINGTONES_DIR = "/iTunes_Control/Ringtones"
    const val RINGTONE_MAX_MS = 40_000L
    val VIDEO_EXT = setOf("m4v", "mp4", "mov")

    /** What the user is adding. [stik] is iTunes' media-kind tag value for it. */
    enum class AddKind(val plural: String, val stik: Int, val isVideo: Boolean = false) {
        MUSIC("Songs", 1),
        PODCAST("Podcast episodes", 21),
        AUDIOBOOK("Audiobooks", 2),
        MOVIE("Movies", 9, true),
        TV_SHOW("TV shows", 10, true),
        MUSIC_VIDEO("Music videos", 6, true),
        RINGTONE("Ringtones", 14),
        ;
        val isLibrary get() = this != RINGTONE
    }
    val PLAYABLE = setOf("mp3", "m4a", "m4b", "wav", "aif", "aiff")
    val UNSUPPORTED = setOf("flac", "ogg", "oga", "opus", "wma", "ape", "wv", "mka", "aac", "amr", "3gp", "mid", "midi")

    enum class Status(val label: String, val ready: Boolean) {
        READY("ready", true),
        DRM("iTunes Store DRM: plays only on an authorized device", true),
        DUPLICATE("already on the device", false),
        UNSUPPORTED("iOS 6 can't play this format", false),
        UNREADABLE("not a file this app can read", false),
        IS_VIDEO("this is a video: add it under Synced Videos", false),
        NO_VIDEO("no video in this file", false),
        WONT_PLAY("this device can't play it", false),
        NOT_AAC("ringtones must be AAC (.m4r/.m4a)", false),
        TOO_LONG("longer than 40 seconds, the limit for ringtones", false),
    }

    class Candidate(
        val sourceName: String,
        val ext: String,
        val size: Long,
        val info: AudioInfo.Info?,
        var status: Status,
        val kind: AddKind = AddKind.MUSIC,
    ) {
        /** Extra detail for the status (e.g. why a video won't play). */
        var note: String? = null
        /** Title, artist and album with the file name as a fallback, the way iTunes fills in missing tags. */
        val title get() = info?.title ?: sourceName.substringBeforeLast('.')
        /** For TV episodes the show stands in for the artist (that's how iTunes lists them too). */
        val artist get() = (if (kind == AddKind.TV_SHOW) info?.show else null) ?: info?.artist ?: info?.albumArtist ?: MusicLibrary.UNKNOWN_ARTIST
        val album get() = info?.album ?: (if (kind == AddKind.TV_SHOW) info?.show else null) ?: MusicLibrary.UNKNOWN_ALBUM
    }

    /** First pass, from the file alone: format and DRM, for what's being added. */
    fun classify(ext: String, info: AudioInfo.Info?, kind: AddKind = AddKind.MUSIC): Status = when {
        kind.isVideo -> when {
            ext !in VIDEO_EXT -> Status.UNSUPPORTED
            info == null -> Status.UNREADABLE
            info.video == null -> Status.NO_VIDEO
            info.format == AudioInfo.Format.PROTECTED_VIDEO -> Status.DRM
            info.format == AudioInfo.Format.OTHER_VIDEO -> Status.UNSUPPORTED
            else -> Status.READY
        }
        info?.format?.isVideo == true || ext in VIDEO_EXT -> Status.IS_VIDEO
        kind == AddKind.RINGTONE -> when {
            ext !in setOf("m4r", "m4a") -> if (ext in UNSUPPORTED || ext in PLAYABLE) Status.NOT_AAC else Status.UNSUPPORTED
            info == null -> Status.UNREADABLE
            info.format != AudioInfo.Format.AAC -> Status.NOT_AAC
            info.durationMs > RINGTONE_MAX_MS -> Status.TOO_LONG
            else -> Status.READY
        }
        ext in UNSUPPORTED -> Status.UNSUPPORTED
        info == null && ext in setOf("wav", "aif", "aiff") -> Status.READY // no tags to read, but playable
        info == null -> Status.UNREADABLE
        info.format == AudioInfo.Format.PROTECTED_AAC -> Status.DRM
        ext !in PLAYABLE && ext != "m4p" -> Status.UNSUPPORTED
        else -> Status.READY
    }

    /**
     * What video a device can play (Apple's published specs for each generation):
     *  - original iPhone, 3G, 3GS and iPod touch 1–3: H.264 Baseline up to 640×480
     *  - iPhone 4, iPod touch 4, iPad 1: H.264 Main up to 1280×720 (level 3.1)
     *  - iPhone 4S and later, iPod touch 5 and later, iPad 2 and later: H.264 High up to 1920×1080
     *  - all of them: MPEG-4 video up to 640×480
     * Portrait videos are compared with the sides swapped.
     */
    data class VideoLimit(val maxW: Int, val maxH: Int, val maxProfile: Int, val maxLevel: Int, val name: String)

    fun videoLimit(productType: String?): VideoLimit? {
        val t = productType ?: return null
        val gen = Regex("^(iPhone|iPod|iPad)(\\d+),").find(t) ?: return null
        val (family, n) = gen.destructured.let { it.component1() to it.component2().toInt() }
        return when (family) {
            "iPhone" -> when { n <= 2 -> VGA; n == 3 -> HD720; else -> HD1080 }
            "iPod" -> when { n <= 3 -> VGA; n == 4 -> HD720; else -> HD1080 }
            else -> if (n == 1) HD720 else HD1080
        }
    }
    private val VGA = VideoLimit(640, 480, 66, 30, "H.264 Baseline up to 640×480")
    private val HD720 = VideoLimit(1280, 720, 77, 31, "H.264 Main up to 1280×720")
    private val HD1080 = VideoLimit(1920, 1080, 100, 41, "H.264 High up to 1920×1080")

    /** Null if the device can play [v], else why not. */
    fun videoProblem(v: AudioInfo.Video, limit: VideoLimit): String? {
        val long = maxOf(v.width, v.height)
        val short = minOf(v.width, v.height)
        return when (v.codec) {
            "mp4v" -> if (long > 640 || short > 480) "MPEG-4 video is limited to 640×480 (this one is ${v.width}×${v.height})" else null
            "avc1", "avc3" -> when {
                long > limit.maxW || short > limit.maxH -> "${v.width}×${v.height} is too big (${limit.name})"
                v.profile != 0 && v.profile != 66 && v.profile > limit.maxProfile -> "H.264 ${v.profileName} profile isn't supported (${limit.name})"
                v.level > limit.maxLevel -> "H.264 level %.1f is too high (${limit.name})".format(v.level / 10.0)
                else -> null
            }
            else -> "${v.codec} video isn't supported"
        }
    }

    /** A free "XXXX.m4r" name in the Ringtones folder (iTunes names them the same way as songs). */
    fun planRingtonePath(existing: MutableSet<String>, rnd: Random = Random.Default): String {
        while (true) {
            val name = (1..4).map { LETTERS[rnd.nextInt(LETTERS.length)] }.joinToString("") + ".m4r"
            if (existing.none { it.equals(name, ignoreCase = true) }) {
                existing += name
                return "$RINGTONES_DIR/$name"
            }
        }
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
