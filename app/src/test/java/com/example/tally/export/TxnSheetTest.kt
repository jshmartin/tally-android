package com.example.tally.export

import com.example.tally.data.model.Kind
import com.example.tally.data.model.Source
import com.example.tally.data.model.TxnRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

class TxnSheetTest {
    private val zone = ZoneId.of("America/Toronto")

    private fun at(text: String) = ZonedDateTime.of(java.time.LocalDateTime.parse(text), zone).toInstant().toEpochMilli()

    private fun row(
        amountCents: Long = 1_250,
        kind: Kind = Kind.EXPENSE,
        merchant: String = "Corner Books",
        note: String = "",
        occurredAt: Long = at("2026-11-11T08:05:42"),
        source: Source = Source.MANUAL,
        sourceApp: String? = null,
        sourceAppLabel: String? = null,
        needsReview: Boolean = false,
        counted: Boolean = true,
        categoryName: String? = "Shopping",
    ) = TxnRow(
        id = 1,
        amountCents = amountCents,
        kind = kind,
        merchant = merchant,
        note = note,
        occurredAt = occurredAt,
        source = source,
        sourceApp = sourceApp,
        sourceAppLabel = sourceAppLabel,
        sourceText = "CIBC Dividend Visa Card, 9999",
        needsReview = needsReview,
        counted = counted,
        categoryId = null,
        categoryName = categoryName,
        categoryIcon = null,
        categoryColor = null,
    )

    @Test
    fun `an expense goes in the Spent column`() {
        val cells = TxnSheet.build(listOf(row(note = " gift for Sam ")), zone).rows.single()
        assertEquals(
            listOf(
                Cell.Date(LocalDate.of(2026, 11, 11)),
                Cell.Time(LocalTime.of(8, 5)),
                Cell.Text("Corner Books"),
                Cell.Text("Shopping"),
                Cell.Amount(1_250),
                Cell.Empty,
                Cell.Text("gift for Sam"),
                Cell.Text("Added by hand"),
                Cell.Empty,
            ),
            cells,
        )
    }

    @Test
    fun `income goes in the Income column`() {
        val cells = TxnSheet.build(listOf(row(kind = Kind.INCOME, amountCents = 200_000)), zone).rows.single()
        assertEquals(Cell.Empty, cells[4])
        assertEquals(Cell.Amount(200_000), cells[5])
    }

    @Test
    fun `a capture names its app and whether it still needs review`() {
        val cells = TxnSheet.build(
            listOf(
                row(
                    source = Source.NOTIFICATION,
                    sourceApp = "com.cibc.android.mobi",
                    sourceAppLabel = "CIBC",
                    needsReview = true,
                    merchant = " ",
                    categoryName = null,
                ),
            ),
            zone,
        ).rows.single()
        assertEquals(Cell.Empty, cells[2])
        assertEquals(Cell.Text("Uncategorized"), cells[3])
        assertEquals(Cell.Text("CIBC"), cells[7])
        assertEquals(Cell.Text("To review"), cells[8])
    }

    @Test
    fun `a capture that doesn't count yet says so`() {
        val cells = TxnSheet.build(listOf(row(source = Source.NOTIFICATION, needsReview = true, counted = false)), zone).rows.single()
        assertEquals(Cell.Text("Not counted yet"), cells[8])
    }

    @Test
    fun `a capture from an app Tally has no name for shows its package`() {
        val cells = TxnSheet.build(listOf(row(source = Source.NOTIFICATION, sourceApp = "com.bank")), zone).rows.single()
        assertEquals(Cell.Text("com.bank"), cells[7])
    }

    @Test
    fun `the notification's text isn't exported`() {
        val texts = TxnSheet.build(listOf(row()), zone).rows.single().filterIsInstance<Cell.Text>().map { it.value }
        assertEquals(emptyList<String>(), texts.filter { "Visa" in it })
    }

    @Test
    fun `dates use the phone's time zone`() {
        // 11:30 PM in Toronto is already the next day in UTC.
        val cells = TxnSheet.build(listOf(row(occurredAt = at("2026-11-11T23:30:00"))), zone).rows.single()
        assertEquals(Cell.Date(LocalDate.of(2026, 11, 11)), cells[0])
        assertEquals(Cell.Time(LocalTime.of(23, 30)), cells[1])
    }

    @Test
    fun `a year runs from its first midnight to the next year's`() {
        val (start, end) = ExportPeriod.Year(2026).bounds(zone)
        assertEquals(at("2026-01-01T00:00:00"), start)
        assertEquals(at("2027-01-01T00:00:00"), end)
    }

    @Test
    fun `periods survive being saved as keys`() {
        listOf(ExportPeriod.Month(YearMonth.of(2026, 11)), ExportPeriod.Year(2026), ExportPeriod.Everything).forEach {
            assertEquals(it, ExportPeriod.fromKey(it.key))
        }
        assertNull(ExportPeriod.fromKey("month:November"))
        assertNull(ExportPeriod.fromKey("week:3"))
    }

    @Test
    fun `file names say what's in them`() {
        val today = LocalDate.of(2026, 11, 11)
        assertEquals("Tally November 2026.xlsx", ExportPeriod.Month(YearMonth.of(2026, 11)).fileName(today))
        assertEquals("Tally 2026.xlsx", ExportPeriod.Year(2026).fileName(today))
        assertEquals("Tally all transactions 2026-11-11.xlsx", ExportPeriod.Everything.fileName(today))
    }
}
