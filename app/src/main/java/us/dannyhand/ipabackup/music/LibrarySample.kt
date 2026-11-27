package us.dannyhand.ipabackup.music

import java.io.Writer

/**
 * A small SQL dump of a music library database, for working out how to add songs without the whole
 * (often 100+ MB) file: the complete schema (tables, indexes, triggers), every row of the small tables
 * (albums, artists, genres, sort map…, capped per table) and, for tables keyed by item_pid, only the
 * rows of a sample of songs. It loads into any sqlite3 to recreate a faithful miniature library.
 */
object LibrarySample {
    private const val ROW_CAP = 25_000
    private const val BLOB_CAP = 64 * 1024

    fun write(db: SqlDb, out: Writer, sampleSize: Int = 25) {
        out.write("-- Music library sample made by IPA Backup\n")
        db.query("PRAGMA user_version").firstOrNull()?.let { out.write("-- user_version ${it[0]}\n") }
        db.query("PRAGMA page_size").firstOrNull()?.let { out.write("-- page_size ${it[0]}\n") }
        db.query("SELECT sqlite_version()").firstOrNull()?.let { out.write("-- dumped with sqlite ${it[0]}\n") }

        val master = db.query("SELECT type, name, tbl_name, sql FROM sqlite_master WHERE sql IS NOT NULL ORDER BY CASE type WHEN 'table' THEN 0 ELSE 1 END, rowid")
        // Virtual tables (e.g. item_to_container on iOS 6) need Apple's own SQLite modules; they can't be read or recreated.
        fun isVirtual(r: List<Any?>) = (r[3] as? String)?.trimStart()?.startsWith("CREATE VIRTUAL TABLE", ignoreCase = true) == true
        val tables = master.filter { it[0] == "table" && !(it[1] as String).startsWith("sqlite_") && !isVirtual(it) }.map { it[1] as String }
        out.write("\n-- Row counts\n")
        val counts = tables.associateWith { t -> runCatching { (db.query("SELECT count(*) FROM \"$t\"").first()[0] as Number).toLong() }.getOrDefault(-1L) }
        counts.forEach { (t, n) -> out.write("--   $t: $n\n") }

        // Sample songs: a spread of media types (music, podcasts, videos…) plus random music.
        val sample = LinkedHashSet<Long>()
        if ("item" in tables && "item_pid" in db.columns("item")) {
            val typeCol = if ("media_type" in db.columns("item")) "media_type" else null
            if (typeCol != null) for (r in db.query("SELECT $typeCol, min(item_pid) FROM item GROUP BY $typeCol")) (r[1] as? Number)?.let { sample += it.toLong() }
            for (r in db.query("SELECT item_pid FROM item ORDER BY random() LIMIT $sampleSize")) (r[0] as? Number)?.let { sample += it.toLong() }
        }
        out.write("\n-- Sampled item_pid: ${sample.joinToString(",")}\n\nPRAGMA foreign_keys=OFF;\nBEGIN TRANSACTION;\n")

        for (row in master.filter { it[0] == "table" && !(it[1] as String).startsWith("sqlite_") }) {
            if (isVirtual(row)) out.write("-- virtual table (needs Apple's own SQLite module, so not recreated):\n" +
                (row[3] as String).lines().joinToString("\n") { "--   $it" } + "\n")
            else out.write("${row[3]};\n")
        }

        for (t in tables) try {
            val cols = db.query("PRAGMA table_info(\"$t\")").map { it[1] as String }
            val where = when {
                "item_pid" in cols && sample.isNotEmpty() -> " WHERE item_pid IN (${sample.joinToString(",")})"
                else -> ""
            }
            val limit = if (where.isEmpty()) " LIMIT $ROW_CAP" else ""
            val rows = db.query("SELECT ${cols.joinToString(",") { "\"$it\"" }} FROM \"$t\"$where$limit")
            if (where.isEmpty() && (counts[t] ?: 0) > ROW_CAP) out.write("-- $t: only the first $ROW_CAP of ${counts[t]} rows\n")
            for (r in rows) {
                out.write("INSERT INTO \"$t\" VALUES(")
                r.forEachIndexed { i, v -> if (i > 0) out.write(","); out.write(literal(v)) }
                out.write(");\n")
            }
        } catch (e: Exception) {
            out.write("-- $t: couldn't read rows (${e.message?.lineSequence()?.firstOrNull()})\n")
        }
        for (row in master.filter { it[0] != "table" }) out.write("${row[3]};\n")
        out.write("COMMIT;\n")
    }

    fun literal(v: Any?): String = when (v) {
        null -> "NULL"
        is Number -> v.toString()
        is ByteArray -> if (v.size > BLOB_CAP) "NULL /* blob ${v.size} bytes left out */" else "X'" + v.joinToString("") { "%02X".format(it) } + "'"
        else -> "'" + v.toString().replace("'", "''") + "'"
    }
}
