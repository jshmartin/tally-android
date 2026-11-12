package com.example.tally.ui.transactions

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.tally.TallyApp
import com.example.tally.data.model.Kind
import com.example.tally.data.model.MonthTotals
import com.example.tally.data.model.TxnRow
import com.example.tally.export.ExportPeriod
import com.example.tally.export.TxnExporter
import com.example.tally.ui.tallyApp
import com.example.tally.util.bounds
import com.example.tally.util.toLocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class Day(val date: LocalDate, val spentCents: Long, val rows: List<TxnRow>)

data class TransactionsState(
    val month: YearMonth,
    val totals: MonthTotals = MonthTotals(0, 0),
    val days: List<Day> = emptyList(),
    val reviewCount: Int = 0,
    val loaded: Boolean = false,
)

sealed interface ExportResult {
    data class Saved(val uri: Uri, val count: Int) : ExportResult
    data object Failed : ExportResult
}

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModel(private val app: TallyApp) : ViewModel() {
    private val dao = app.database.txnDao()
    private val exporter = TxnExporter(app.database, app.contentResolver)
    private val exportResults = Channel<ExportResult>(Channel.BUFFERED)

    val state: StateFlow<TransactionsState> = app.selectedMonth.flatMapLatest { month ->
        val (start, end) = month.bounds()
        combine(dao.observeRows(start, end), dao.observeTotals(start, end), dao.observeReviewCount()) { rows, totals, review ->
            TransactionsState(
                month = month,
                totals = totals,
                days = rows.groupBy { it.occurredAt.toLocalDate() }.map { (date, dayRows) ->
                    Day(date, dayRows.filter { it.kind == Kind.EXPENSE && it.counted }.sumOf { it.amountCents }, dayRows)
                },
                reviewCount = review,
                loaded = true,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsState(app.selectedMonth.value))

    /** One result per [export], kept until the screen collects it. */
    val exports: Flow<ExportResult> = exportResults.receiveAsFlow()

    fun setMonth(month: YearMonth) {
        app.selectedMonth.value = month
    }

    suspend fun exportCount(period: ExportPeriod): Int = exporter.count(period)

    /** Runs in the view model so leaving the screen doesn't stop it halfway through the file. */
    fun export(period: ExportPeriod, uri: Uri) {
        viewModelScope.launch {
            val result = try {
                ExportResult.Saved(uri, exporter.export(period, uri))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Tally", "Export failed", e)
                ExportResult.Failed
            }
            exportResults.send(result)
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { TransactionsViewModel(tallyApp()) }
        }
    }
}
