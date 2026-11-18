package com.example.tally.data.backup

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.tally.data.AppDatabase
import com.example.tally.data.model.Category
import com.example.tally.data.model.DismissedCapture
import com.example.tally.data.model.Kind
import com.example.tally.data.model.MerchantRule
import com.example.tally.data.model.SourceApp
import com.example.tally.data.model.Txn
import java.io.File
import java.util.Random
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Backs up and restores real database files, the way the app does. */
@RunWith(AndroidJUnit4::class)
class DatabaseBackupTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dir = File(context.cacheDir, "database-backup-test")
    private val opened = mutableListOf<AppDatabase>()
    private val names = mutableListOf<String>()

    @Before
    fun setUp() {
        dir.deleteRecursively()
        dir.mkdirs()
    }

    @After
    fun tearDown() {
        opened.forEach { it.close() }
        names.forEach { context.deleteDatabase(it) }
        dir.deleteRecursively()
    }

    /** A database file like the app's own, with the starter categories a new install gets. */
    private fun newAppDatabase(): AppDatabase {
        val name = "backup-test-${System.nanoTime()}.db"
        names += name
        return AppDatabase.build(context, name).also { opened += it }
    }

    private fun appVersion(db: AppDatabase) = db.openHelper.readableDatabase.version

    private suspend fun seed(db: AppDatabase) {
        val category = db.categoryDao().insert(
            Category(name = "Test pets", kind = Kind.EXPENSE, iconKey = "pets", color = 0x112233, monthlyBudgetCents = 5_000, sortOrder = 99),
        )
        db.txnDao().insert(Txn(amountCents = 1_234, kind = Kind.EXPENSE, merchant = "Test Pet Shop", categoryId = category, occurredAt = 1, createdAt = 1))
        db.txnDao().insert(
            Txn(
                amountCents = 999, kind = Kind.EXPENSE, merchant = "Test Cafe", categoryId = null, occurredAt = 2, createdAt = 2,
                needsReview = true, dedupeKey = "key-1", mergedKeys = "key-2",
            ),
        )
        db.automationDao().upsertRule(MerchantRule("testpetshop", "Test Pet Shop", category, 3))
        db.automationDao().insertApp(SourceApp("com.example.bank", "Test Bank", enabled = false, firstSeenAt = 1, lastSeenAt = 2, capturedCount = 5))
        db.automationDao().dismiss(DismissedCapture("key-3", 4))
    }

    /** Every row of every table, sorted, for comparing two databases. */
    private fun contents(db: AppDatabase): Map<String, List<String>> {
        val sql = db.openHelper.readableDatabase
        return DatabaseBackup.tableNames(db).associateWith { table ->
            sql.query("SELECT * FROM `$table`").use { row ->
                buildList {
                    while (row.moveToNext()) {
                        add((0 until row.columnCount).joinToString("|") { "${row.getColumnName(it)}=${value(row, it)}" })
                    }
                }.sorted()
            }
        }
    }

    private fun value(row: Cursor, i: Int): String = when (row.getType(i)) {
        Cursor.FIELD_TYPE_NULL -> "null"
        Cursor.FIELD_TYPE_BLOB -> row.getBlob(i).contentToString()
        else -> row.getString(i)
    }

    private fun backUp(db: AppDatabase): File = File(dir, "backup-${System.nanoTime()}.db").also {
        DatabaseBackup.write(context, db, it)
    }

    @Test
    fun restoringABackupReplacesEverythingWithWhatWasBackedUp() = runBlocking {
        val source = newAppDatabase()
        seed(source)
        val file = backUp(source)
        val target = newAppDatabase()
        target.txnDao().insert(Txn(amountCents = 1, kind = Kind.INCOME, merchant = "Only on the new phone", categoryId = null, occurredAt = 9, createdAt = 9))

        val summary = DatabaseBackup.inspect(context, file, appVersion(target))
        DatabaseBackup.restore(context, file, target)

        assertEquals(contents(source), contents(target))
        val categories = contents(source).getValue("categories").size
        assertEquals(BackupSummary(transactions = 2, categories = categories, rules = 1), summary)
    }

    @Test
    fun everyTableIsInTheBackup() = runBlocking {
        val source = newAppDatabase()
        seed(source)
        // A new table that the backup misses would come back empty here.
        contents(source).forEach { (table, rows) -> assertTrue(table, rows.isNotEmpty()) }
    }

    @Test
    fun aFileThatIsntABackupIsRefused() {
        val version = appVersion(newAppDatabase())
        val random = File(dir, "random.db").apply { writeBytes(ByteArray(8192).also { Random(5).nextBytes(it) }) }
        val empty = File(dir, "empty.db").apply { writeBytes(ByteArray(0)) }
        val otherApp = File(dir, "other.db")
        SQLiteDatabase.openOrCreateDatabase(otherApp, null).use { it.execSQL("CREATE TABLE notes (text TEXT)") }

        listOf(random, empty, otherApp).forEach { assertNull(it.name, DatabaseBackup.inspect(context, it, version)) }
    }

    @Test
    fun aBackupFromANewerVersionOfTheAppIsRefused() {
        val db = newAppDatabase()
        val file = backUp(db)
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { it.version = appVersion(db) + 1 }

        assertNull(DatabaseBackup.inspect(context, file, appVersion(db)))
    }

    @Test
    fun aWrittenBackupIsOneCompleteFile() {
        val file = backUp(newAppDatabase())

        assertTrue(file.length() > 0)
        assertFalse(File(file.path + "-wal").exists())
        assertNotNull(DatabaseBackup.inspect(context, file, appVersion(newAppDatabase())))
    }
}
