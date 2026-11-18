package com.example.tally

import android.app.Application
import com.example.tally.capture.PurchaseRecorder
import com.example.tally.data.AppDatabase
import com.example.tally.data.backup.BackupManager
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.YearMonth

class TallyApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.build(this) }
    val recorder: PurchaseRecorder by lazy { PurchaseRecorder(database) }
    val backup: BackupManager by lazy { BackupManager(this, database) }

    /** The month every tab shows. Changing it on one tab changes it on all of them. */
    val selectedMonth = MutableStateFlow(YearMonth.now())
}
