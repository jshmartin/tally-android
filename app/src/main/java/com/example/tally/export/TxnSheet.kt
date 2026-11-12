package com.example.tally.export

import com.example.tally.data.model.Kind
import com.example.tally.data.model.Source
import com.example.tally.data.model.TxnRow
import com.example.tally.util.bounds
import com.example.tally.util.label
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Which transactions to export. */
sealed interface ExportPeriod {
    data class Month(val month: YearMonth) : ExportPeriod

    data class Year(val year: Int) : ExportPeriod

    data object Everything : ExportPeriod

    /** [start, end) in epoch millis. */
    fun bounds(zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> = when (this) {
        is Month -> month.bounds(zone)
        is Year -> LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli() to
            LocalDate.of(year + 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        Everything -> Long.MIN_VALUE to Long.MAX_VALUE
    }

    /** As the export dialog lists it. */
    val label: String
        get() = when (this) {
            is Month -> month.label()
            is Year -> "All of $year"
            Everything -> "Everything"
        }

    /** What the save dialog suggests, such as "Tally November 2026.xlsx". */
    fun fileName(today: LocalDate = LocalDate.now()): String = when (this) {
        is Month -> "Tally ${month.label()}.xlsx"
        is Year -> "Tally $year.xlsx"
        Everything -> "Tally all transactions $today.xlsx"
    }

    /** A string that [fromKey] turns back into this, for keeping a pending export across a restart. */
    val key: String
        get() = when (this) {
            is Month -> "month:$month"
            is Year -> "year:$year"
            Everything -> "all"
        }

    companion object {
        fun fromKey(key: String): ExportPeriod? = runCatching {
            when {
                key == "all" -> Everything
                key.startsWith("month:") -> Month(YearMonth.parse(key.removePrefix("month:")))
                key.startsWith("year:") -> Year(key.removePrefix("year:").toInt())
                else -> null
            }
        }.getOrNull()
    }
}

/**
 * Lays out transactions as a spreadsheet, oldest first. Spending and income get a column each, as on
 * a bank statement, so both add up to positive totals. Captures that don't count toward the app's totals
 * yet are still listed, marked "Not counted yet" under Status.
 */
object TxnSheet {
    val COLUMNS = listOf(
        Column("Date", 12),
        Column("Time", 10),
        Column("Merchant", 28),
        Column("Category", 16),
        Column("Spent", 12),
        Column("Income", 12),
        Column("Note", 28),
        Column("Source", 16),
        Column("Status", 16),
    )

    fun build(rows: List<TxnRow>, zone: ZoneId = ZoneId.systemDefault()): Sheet =
        Sheet("Transactions", COLUMNS, rows.map { row(it, zone) })

    private fun row(txn: TxnRow, zone: ZoneId): List<Cell> {
        val at = Instant.ofEpochMilli(txn.occurredAt).atZone(zone).toLocalDateTime()
        val amount = Cell.Amount(txn.amountCents)
        return listOf(
            Cell.Date(at.toLocalDate()),
            Cell.Time(at.toLocalTime().truncatedTo(ChronoUnit.MINUTES)),
            text(txn.merchant),
            Cell.Text(txn.categoryName ?: "Uncategorized"),
            if (txn.kind == Kind.EXPENSE) amount else Cell.Empty,
            if (txn.kind == Kind.INCOME) amount else Cell.Empty,
            text(txn.note),
            Cell.Text(
                when (txn.source) {
                    Source.MANUAL -> "Added by hand"
                    Source.NOTIFICATION -> txn.sourceAppLabel ?: txn.sourceApp ?: "Notification"
                }
            ),
            when {
                !txn.needsReview -> Cell.Empty
                txn.counted -> Cell.Text("To review")
                else -> Cell.Text("Not counted yet")
            },
        )
    }

    private fun text(value: String): Cell = if (value.isBlank()) Cell.Empty else Cell.Text(value.trim())
}
