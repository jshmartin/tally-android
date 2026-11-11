package com.example.tally.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.tally.TallyApp
import com.example.tally.data.model.Category
import com.example.tally.data.model.Txn
import com.example.tally.data.model.TxnRow
import com.example.tally.ui.tallyApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReviewViewModel(private val app: TallyApp) : ViewModel() {
    private val db = app.database

    /** Null until loaded. */
    val rows: StateFlow<List<TxnRow>?> = db.txnDao().observeReviewRows()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val categories: StateFlow<List<Category>> = db.categoryDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun keep(row: TxnRow) {
        viewModelScope.launch { db.txnDao().markReviewed(row.id) }
    }

    fun keepAll() {
        viewModelScope.launch { db.txnDao().markAllReviewed() }
    }

    /** Changing a category teaches Tally the merchant. */
    fun setCategory(row: TxnRow, categoryId: Long) {
        viewModelScope.launch {
            db.txnDao().setCategory(row.id, categoryId)
            app.recorder.learn(row.merchant, categoryId)
        }
    }

    /** Returns what was discarded, for undo. */
    suspend fun discard(row: TxnRow): Txn? {
        val txn = db.txnDao().get(row.id) ?: return null
        app.recorder.discard(txn)
        return txn
    }

    fun restore(txn: Txn) {
        viewModelScope.launch { app.recorder.restore(txn) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { ReviewViewModel(tallyApp()) }
        }
    }
}
