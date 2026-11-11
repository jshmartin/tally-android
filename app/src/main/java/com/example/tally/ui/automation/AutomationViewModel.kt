package com.example.tally.ui.automation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.tally.TallyApp
import com.example.tally.capture.PurchaseRecorder
import com.example.tally.data.model.RuleRow
import com.example.tally.data.model.SourceApp
import com.example.tally.ui.tallyApp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface TestResult {
    data object Empty : TestResult
    data object NotAPurchase : TestResult
    data class Purchase(val preview: PurchaseRecorder.Preview) : TestResult
}

@OptIn(ExperimentalCoroutinesApi::class)
class AutomationViewModel(private val app: TallyApp) : ViewModel() {
    private val dao = app.database.automationDao()

    val apps: StateFlow<List<SourceApp>> = dao.observeApps()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val rules: StateFlow<List<RuleRow>> = dao.observeRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val reviewCount: StateFlow<Int> = app.database.txnDao().observeReviewCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Notification text pasted in to see what Tally would make of it. */
    var testInput by mutableStateOf("")
        private set

    val testResult: StateFlow<TestResult> = snapshotFlow { testInput }
        .mapLatest { text ->
            if (text.isBlank()) TestResult.Empty
            else app.recorder.preview(text)?.let { TestResult.Purchase(it) } ?: TestResult.NotAPurchase
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TestResult.Empty)

    fun onTestInputChange(text: String) {
        testInput = text
    }

    fun setAppEnabled(app: SourceApp, enabled: Boolean) {
        viewModelScope.launch { dao.setAppEnabled(app.packageName, enabled) }
    }

    fun deleteRule(rule: RuleRow) {
        viewModelScope.launch { dao.deleteRule(rule.merchantKey) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { AutomationViewModel(tallyApp()) }
        }
    }
}
