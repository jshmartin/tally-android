package com.example.tally.data.backup

import com.example.tally.ui.settings.describeBackup
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupScheduleTest {
    private val day = TimeUnit.DAYS.toMillis(1)
    private val now = 100 * day

    @Test
    fun theFirstBackupIsDueStraightAway() {
        assertEquals(0, BackupManager.delayUntilDue(null, BackupFrequency.WEEKLY, now))
    }

    @Test
    fun theNextBackupIsOneFrequencyAfterTheLast() {
        assertEquals(4 * day, BackupManager.delayUntilDue(now - 3 * day, BackupFrequency.WEEKLY, now))
        assertEquals(day, BackupManager.delayUntilDue(now, BackupFrequency.DAILY, now))
    }

    @Test
    fun anOverdueBackupIsDueStraightAway() {
        // Switching from monthly to daily ten days after the last backup.
        assertEquals(0, BackupManager.delayUntilDue(now - 10 * day, BackupFrequency.DAILY, now))
    }

    @Test
    fun describesWhatsInABackupWithPlurals() {
        assertEquals(
            "120 transactions, 14 categories and 30 merchant rules",
            describeBackup(BackupSummary(transactions = 120, categories = 14, rules = 30)),
        )
        assertEquals(
            "1 transaction, 1 category and 0 merchant rules",
            describeBackup(BackupSummary(transactions = 1, categories = 1, rules = 0)),
        )
    }
}
