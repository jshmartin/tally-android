package com.example.tally.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.tally.TallyApp
import com.example.tally.data.model.Kind
import com.example.tally.data.model.MonthTotals
import com.example.tally.data.model.TxnRow
import com.example.tally.ui.tallyApp
import com.example.tally.util.bounds
import com.example.tally.util.toLocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
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

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModel(private val app: TallyApp) : ViewModel() {
    private val dao = app.database.txnDao()

    val state: StateFlow<TransactionsState> = app.selectedMonth.flatMapLatest { month ->
        val (start, end) = month.bounds()
        combine(dao.observeRows(start, end), dao.observeTotals(start, end), dao.observeReviewCount()) { rows, totals, review ->
            TransactionsState(
                month = month,
                totals = totals,
                days = rows.groupBy { it.occurredAt.toLocalDate() }.map { (date, dayRows) ->
                    Day(date, dayRows.filter { it.kind == Kind.EXPENSE }.sumOf { it.amountCents }, dayRows)
                },
                reviewCount = review,
                loaded = true,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsState(app.selectedMonth.value))

    fun setMonth(month: YearMonth) {
        app.selectedMonth.value = month
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { TransactionsViewModel(tallyApp()) }
        }
    }
}
