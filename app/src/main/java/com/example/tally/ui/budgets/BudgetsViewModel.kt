package com.example.tally.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.tally.TallyApp
import com.example.tally.data.StarterCategories
import com.example.tally.data.model.Category
import com.example.tally.data.model.CategorySpending
import com.example.tally.data.model.Kind
import com.example.tally.ui.tallyApp
import com.example.tally.util.bounds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth

data class BudgetsState(
    val month: YearMonth,
    val expenses: List<CategorySpending> = emptyList(),
    val income: List<CategorySpending> = emptyList(),
    val uncategorizedCents: Long = 0,
    val budgetCents: Long = 0,
    val budgetedSpentCents: Long = 0,
    val loaded: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetsViewModel(private val app: TallyApp) : ViewModel() {
    private val db = app.database

    val state: StateFlow<BudgetsState> = app.selectedMonth.flatMapLatest { month ->
        val (start, end) = month.bounds()
        combine(
            db.categoryDao().observeSpending(start, end),
            db.txnDao().observeUncategorizedSpent(start, end),
        ) { spending, uncategorized ->
            val expenses = spending.filter { it.kind == Kind.EXPENSE }
            val budgeted = expenses.filter { (it.monthlyBudgetCents ?: 0) > 0 }
            BudgetsState(
                month = month,
                expenses = expenses,
                income = spending.filter { it.kind == Kind.INCOME },
                uncategorizedCents = uncategorized,
                budgetCents = budgeted.sumOf { it.monthlyBudgetCents ?: 0 },
                budgetedSpentCents = budgeted.sumOf { it.totalCents },
                loaded = true,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetsState(app.selectedMonth.value))

    fun setMonth(month: YearMonth) {
        app.selectedMonth.value = month
    }

    suspend fun load(id: Long): Category? = db.categoryDao().get(id)

    /** Saves a new category when [id] is null. A budget of zero or less means no budget. */
    fun save(id: Long?, name: String, kind: Kind, budgetCents: Long?, iconKey: String) {
        val budget = budgetCents?.takeIf { it > 0 && kind == Kind.EXPENSE }
        viewModelScope.launch {
            val dao = db.categoryDao()
            val existing = id?.let { dao.get(it) }
            if (existing != null) {
                dao.update(existing.copy(name = name, monthlyBudgetCents = budget, iconKey = iconKey))
            } else {
                val palette = StarterCategories.PALETTE
                dao.insert(
                    Category(
                        name = name,
                        kind = kind,
                        iconKey = iconKey,
                        color = palette[dao.count() % palette.size],
                        monthlyBudgetCents = budget,
                        sortOrder = dao.nextSortOrder(kind),
                    )
                )
            }
        }
    }

    /** Its transactions become uncategorized, and merchant rules for it go with it. */
    fun delete(category: Category) {
        viewModelScope.launch { db.categoryDao().delete(category) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { BudgetsViewModel(tallyApp()) }
        }
    }
}
