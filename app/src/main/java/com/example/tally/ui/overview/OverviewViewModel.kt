package com.example.tally.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.tally.TallyApp
import com.example.tally.data.model.CategorySpending
import com.example.tally.data.model.Kind
import com.example.tally.data.model.MonthTotals
import com.example.tally.data.model.TxnRow
import com.example.tally.ui.tallyApp
import com.example.tally.util.bounds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth

data class OverviewState(
    val month: YearMonth,
    val totals: MonthTotals = MonthTotals(0, 0),
    /** Sum of every expense category's budget. */
    val budgetCents: Long = 0,
    /** Spending in categories that have a budget, to compare against [budgetCents]. */
    val budgetedSpentCents: Long = 0,
    val topCategories: List<CategorySpending> = emptyList(),
    val uncategorizedCents: Long = 0,
    val recent: List<TxnRow> = emptyList(),
    val transactionCount: Int = 0,
    val reviewCount: Int = 0,
    val loaded: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class OverviewViewModel(private val app: TallyApp) : ViewModel() {
    private val db = app.database

    val state: StateFlow<OverviewState> = app.selectedMonth.flatMapLatest { month ->
        val (start, end) = month.bounds()
        combine(
            db.txnDao().observeTotals(start, end),
            db.categoryDao().observeSpending(start, end),
            db.txnDao().observeRows(start, end),
            db.txnDao().observeUncategorizedSpent(start, end),
            db.txnDao().observeReviewCount(),
        ) { totals, spending, rows, uncategorized, reviewCount ->
            val expenses = spending.filter { it.kind == Kind.EXPENSE }
            val budgeted = expenses.filter { (it.monthlyBudgetCents ?: 0) > 0 }
            OverviewState(
                month = month,
                totals = totals,
                budgetCents = budgeted.sumOf { it.monthlyBudgetCents ?: 0 },
                budgetedSpentCents = budgeted.sumOf { it.totalCents },
                topCategories = expenses.filter { it.totalCents > 0 }.sortedByDescending { it.totalCents }.take(5),
                uncategorizedCents = uncategorized,
                recent = rows.take(5),
                transactionCount = rows.size,
                reviewCount = reviewCount,
                loaded = true,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewState(app.selectedMonth.value))

    fun setMonth(month: YearMonth) {
        app.selectedMonth.value = month
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { OverviewViewModel(tallyApp()) }
        }
    }
}
