package com.example.tally.export

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.tally.data.AppDatabase
import com.example.tally.data.model.Kind
import com.example.tally.data.model.Txn
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.zip.ZipFile

@RunWith(AndroidJUnit4::class)
class TxnExporterTest {
    private val zone = ZoneId.of("America/Toronto")
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDatabase
    private lateinit var exporter: TxnExporter
    private lateinit var file: File

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(AppDatabase.SEED_ON_CREATE)
            .build()
        exporter = TxnExporter(db, context.contentResolver, zone = { zone })
        file = File(context.cacheDir, "export-test.xlsx").apply { delete() }
    }

    @After
    fun tearDown() {
        db.close()
        file.delete()
    }

    private suspend fun add(merchant: String, at: String, cents: Long = 1_000, kind: Kind = Kind.EXPENSE) {
        val millis = LocalDateTime.parse(at).atZone(zone).toInstant().toEpochMilli()
        db.txnDao().insert(Txn(amountCents = cents, kind = kind, merchant = merchant, categoryId = null, occurredAt = millis, createdAt = millis))
    }

    private fun sheetXml(): String = ZipFile(file).use { zip ->
        zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml")).readBytes().toString(Charsets.UTF_8)
    }

    @Test
    fun exportsOnlyThePeriodOldestFirst() = runBlocking {
        add("Late October", "2026-10-31T23:59:00")
        add("Second", "2026-11-20T09:00:00")
        add("First", "2026-11-01T00:00:00")
        add("Paycheck", "2026-11-15T12:00:00", cents = 250_000, kind = Kind.INCOME)
        add("December", "2026-12-01T00:00:00")

        val period = ExportPeriod.Month(YearMonth.of(2026, 11))
        assertEquals(3, exporter.count(period))
        assertEquals(3, exporter.export(period, Uri.fromFile(file)))

        val xml = sheetXml()
        val first = xml.indexOf(">First<")
        val paycheck = xml.indexOf(">Paycheck<")
        val second = xml.indexOf(">Second<")
        assertTrue(xml, first in 0 until paycheck && paycheck < second)
        assertTrue(xml, "Late October" !in xml && "December" !in xml)
        assertTrue(xml, xml.contains("<v>2500.00</v>"))
    }

    @Test
    fun everythingExportsAllOfIt() = runBlocking {
        add("Old", "1999-05-01T10:00:00")
        add("New", "2026-11-11T10:00:00")
        assertEquals(2, exporter.export(ExportPeriod.Everything, Uri.fromFile(file)))
        assertTrue(sheetXml().contains(">Old<"))
    }

    @Test
    fun replacingAFileLeavesNoneOfTheOldOne() = runBlocking {
        file.writeText("x".repeat(500_000))
        add("Coffee", "2026-11-11T10:00:00")
        exporter.export(ExportPeriod.Year(2026), Uri.fromFile(file))
        assertTrue(file.length() < 10_000)
        assertTrue(sheetXml().contains(">Coffee<"))
    }
}
