package com.example.tally.data.backup

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.tally.data.AppDatabase
import java.io.File
import java.io.InputStream

/** What's in a backup, shown before it's restored. */
data class BackupSummary(
    val transactions: Int,
    val categories: Int,
    val rules: Int,
)

/**
 * A backup is a copy of the app's database in a single file. Because it's the same database, a
 * backup made by an older version of the app opens through the same migrations as the app's own.
 */
object DatabaseBackup {
    /** Tables every database keeps for itself, which a copy mustn't overwrite. */
    private val OWN_TABLES = setOf("android_metadata", "room_master_table")

    private const val TABLES_QUERY = "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'"

    private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    private const val TAG = "Backup"

    /** Writes everything in [source] to a new database file at [dest]. */
    fun write(context: Context, source: AppDatabase, dest: File) {
        dest.parentFile?.mkdirs()
        deleteDatabaseFiles(dest)
        val copy = open(context, dest)
        try {
            val from = source.openHelper.writableDatabase
            val to = copy.openHelper.writableDatabase
            // A transaction on each side, so the copy can't catch a change half made.
            copy.runInTransaction { source.runInTransaction { replaceAllRows(from, to) } }
        } finally {
            copy.close()
        }
    }

    /**
     * Checks that [file] is a backup this version of the app can read, upgrading it in place if it's
     * from an older version. [appVersion] is the app's own database version. Returns what's in the
     * backup, or null if it isn't one the app can restore.
     */
    fun inspect(context: Context, file: File, appVersion: Int): BackupSummary? {
        if (!file.inputStream().use(::startsLikeSqlite)) return null
        val version = try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                val tables = allTableNames(db.rawQuery(TABLES_QUERY, null))
                if ("room_master_table" in tables && "transactions" in tables) db.version else null
            }
        } catch (_: SQLiteException) {
            null
        }
        if (version == null || version !in 1..appVersion) return null
        return try {
            val backup = open(context, file)
            try {
                summarize(backup.openHelper.readableDatabase)
            } finally {
                backup.close()
            }
        } catch (e: RuntimeException) {
            // Room refuses a database whose tables don't match what its version should have.
            Log.w(TAG, "Not a usable backup", e)
            null
        }
    }

    /** Replaces everything in [live] with the backup in [file], which [inspect] has accepted. */
    fun restore(context: Context, file: File, live: AppDatabase) {
        val backup = open(context, file)
        try {
            val from = backup.openHelper.readableDatabase
            val to = live.openHelper.writableDatabase
            live.runInTransaction { replaceAllRows(from, to) }
        } finally {
            backup.close()
        }
    }

    /** True if [input] begins with the header every SQLite database file starts with. */
    fun startsLikeSqlite(input: InputStream): Boolean {
        val header = ByteArray(SQLITE_HEADER.size)
        var read = 0
        while (read < header.size) {
            val n = input.read(header, read, header.size - read)
            if (n < 0) return false
            read += n
        }
        return header.contentEquals(SQLITE_HEADER)
    }

    fun deleteDatabaseFiles(file: File) {
        listOf("", "-journal", "-wal", "-shm").forEach { File(file.path + it).delete() }
    }

    /** The tables holding the app's data in [db]. */
    internal fun tableNames(db: AppDatabase): Set<String> = tableNames(db.openHelper.readableDatabase.query(TABLES_QUERY))

    /** Opens the database file at [file] the way the app opens its own, without seeding a new one. */
    private fun open(context: Context, file: File): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, file.absolutePath)
            // One file and no write-ahead log, so the file is complete whenever it's closed.
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()

    /** Empties every table [to] shares with [from] and fills it with [from]'s rows. Run in a transaction on [to]. */
    private fun replaceAllRows(from: SupportSQLiteDatabase, to: SupportSQLiteDatabase) {
        val tables = tableNames(from.query(TABLES_QUERY)).intersect(tableNames(to.query(TABLES_QUERY)))
        // Tables fill one at a time, so a row can arrive before the row it points at.
        to.execSQL("PRAGMA defer_foreign_keys = ON")
        tables.forEach { to.delete(it, null, null) }
        tables.forEach { copyRows(from, to, it) }
    }

    private fun copyRows(from: SupportSQLiteDatabase, to: SupportSQLiteDatabase, table: String) {
        from.query("SELECT * FROM `$table`").use { cursor ->
            val values = ContentValues()
            while (cursor.moveToNext()) {
                values.clear()
                for (i in 0 until cursor.columnCount) {
                    val column = cursor.getColumnName(i)
                    when (cursor.getType(i)) {
                        Cursor.FIELD_TYPE_NULL -> values.putNull(column)
                        Cursor.FIELD_TYPE_INTEGER -> values.put(column, cursor.getLong(i))
                        Cursor.FIELD_TYPE_FLOAT -> values.put(column, cursor.getDouble(i))
                        Cursor.FIELD_TYPE_BLOB -> values.put(column, cursor.getBlob(i))
                        else -> values.put(column, cursor.getString(i))
                    }
                }
                to.insert(table, SQLiteDatabase.CONFLICT_ABORT, values)
            }
        }
    }

    private fun summarize(db: SupportSQLiteDatabase) = BackupSummary(
        transactions = count(db, "transactions"),
        categories = count(db, "categories"),
        rules = count(db, "merchant_rules"),
    )

    private fun count(db: SupportSQLiteDatabase, table: String): Int =
        db.query("SELECT COUNT(*) FROM `$table`").use { it.moveToFirst(); it.getInt(0) }

    /** The tables holding data, leaving out each database's own bookkeeping. */
    private fun tableNames(cursor: Cursor): Set<String> = allTableNames(cursor) - OWN_TABLES

    private fun allTableNames(cursor: Cursor): Set<String> = cursor.use {
        buildSet { while (it.moveToNext()) add(it.getString(0)) }
    }
}
