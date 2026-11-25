package us.dannyhand.ipabackup.music

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

class AndroidSqlDb(private val db: SQLiteDatabase) : SqlDb {
    override fun tables(): Set<String> =
        db.rawQuery("SELECT name FROM sqlite_master WHERE type IN ('table','view')", null).use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(0)) }
        }

    override fun columns(table: String): Set<String> =
        db.rawQuery(tableInfoSql(table), null).use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(1)) }
        }

    override fun exec(sql: String) = db.execSQL(sql)

    override fun query(sql: String): List<List<Any?>> = db.rawQuery(sql, null).use { c ->
        val rows = ArrayList<List<Any?>>()
        while (c.moveToNext()) {
            rows += List(c.columnCount) { i ->
                when (c.getType(i)) {
                    Cursor.FIELD_TYPE_NULL -> null
                    Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                    Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                    Cursor.FIELD_TYPE_BLOB -> c.getBlob(i)
                    else -> c.getString(i)
                }
            }
        }
        rows
    }
}
