package com.example.tally.ui.transactions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.tally.TallyApp
import com.example.tally.data.model.Category
import com.example.tally.data.model.Kind
import com.example.tally.data.model.Txn
import com.example.tally.ui.tallyApp
import com.example.tally.util.Money
import com.example.tally.util.toLocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class TxnForm(
    val kind: Kind = Kind.EXPENSE,
    val amount: String = "",
    val merchant: String = "",
    val note: String = "",
    val categoryId: Long? = null,
    /** Once the user picks a category, typing a merchant stops suggesting one. */
    val categoryPicked: Boolean = false,
    val date: LocalDate = LocalDate.now(),
    val amountError: Boolean = false,
)

class TxnEditViewModel(private val app: TallyApp, handle: SavedStateHandle) : ViewModel() {
    private val db = app.database
    private val id: Long? = handle.get<Long>("id")?.takeIf { it > 0 }

    val isNew: Boolean get() = id == null

    var form by mutableStateOf(
        TxnForm(kind = handle.get<String>("kind")?.let { runCatching { Kind.valueOf(it) }.getOrNull() } ?: Kind.EXPENSE)
    )
        private set

    /** The saved transaction, once loaded. Null while loading and for a new one. */
    var original by mutableStateOf<Txn?>(null)
        private set
    var sourceAppLabel by mutableStateOf<String?>(null)
        private set
    var loaded by mutableStateOf(id == null)
        private set

    /** Set when the transaction was deleted elsewhere, so the screen can close. */
    var missing by mutableStateOf(false)
        private set

    val categories: StateFlow<List<Category>> = db.categoryDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var suggestJob: Job? = null

    init {
        if (id != null) {
            viewModelScope.launch {
                val txn = db.txnDao().get(id)
                if (txn == null) {
                    missing = true
                    return@launch
                }
                original = txn
                sourceAppLabel = txn.sourceApp?.let { db.automationDao().getApp(it)?.label ?: it }
                form = TxnForm(
                    kind = txn.kind,
                    amount = Money.toInput(txn.amountCents),
                    merchant = txn.merchant,
                    note = txn.note,
                    categoryId = txn.categoryId,
                    categoryPicked = txn.categoryId != null,
                    date = txn.occurredAt.toLocalDate(),
                )
                loaded = true
            }
        }
    }

    fun setKind(kind: Kind) {
        if (form.kind == kind) return
        form = form.copy(kind = kind, categoryId = null, categoryPicked = false)
        suggestCategory()
    }

    fun setAmount(text: String) {
        form = form.copy(amount = text.filter { it.isDigit() || it == '.' || it == ',' }.take(12), amountError = false)
    }

    fun setMerchant(text: String) {
        form = form.copy(merchant = text)
        suggestCategory()
    }

    fun pickCategory(categoryId: Long?) {
        form = form.copy(categoryId = categoryId, categoryPicked = categoryId != null)
    }

    fun setDate(date: LocalDate) {
        form = form.copy(date = date)
    }

    fun setNote(text: String) {
        form = form.copy(note = text)
    }

    /** Fills in the category the merchant usually gets, until the user picks one themselves. */
    private fun suggestCategory() {
        if (form.categoryPicked) return
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch {
            delay(300)
            val suggestion = app.recorder.categoryFor(form.kind, form.merchant.trim(), null)
            if (!form.categoryPicked) form = form.copy(categoryId = suggestion?.id)
        }
    }

    fun save(onSaved: () -> Unit) {
        val cents = Money.parseInput(form.amount)
        if (cents == null || cents <= 0) {
            form = form.copy(amountError = true)
            return
        }
        val f = form
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val base = original
            val txn = base?.copy(
                amountCents = cents,
                kind = f.kind,
                merchant = f.merchant.trim(),
                note = f.note.trim(),
                categoryId = f.categoryId,
                occurredAt = occurredAt(f.date, base.occurredAt, now),
                needsReview = false,
            ) ?: Txn(
                amountCents = cents,
                kind = f.kind,
                merchant = f.merchant.trim(),
                note = f.note.trim(),
                categoryId = f.categoryId,
                occurredAt = occurredAt(f.date, null, now),
                createdAt = now,
            )
            if (base == null) db.txnDao().insert(txn) else db.txnDao().update(txn)
            app.recorder.learn(txn.merchant, txn.categoryId)
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val txn = original ?: return
        viewModelScope.launch {
            app.recorder.discard(txn)
            onDeleted()
        }
    }

    /** Keeps the saved time when the day didn't change; a new entry for today is stamped now. */
    private fun occurredAt(date: LocalDate, previous: Long?, now: Long): Long {
        val zone = ZoneId.systemDefault()
        return when {
            previous != null && previous.toLocalDate(zone) == date -> previous
            date == now.toLocalDate(zone) -> now
            else -> date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { TxnEditViewModel(tallyApp(), createSavedStateHandle()) }
        }
    }
}
