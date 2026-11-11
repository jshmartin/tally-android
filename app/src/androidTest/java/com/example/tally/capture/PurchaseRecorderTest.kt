package com.example.tally.capture

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.tally.data.AppDatabase
import com.example.tally.data.StarterCategories
import com.example.tally.data.model.Kind
import com.example.tally.data.model.Source
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PurchaseRecorderTest {
    private lateinit var db: AppDatabase
    private lateinit var recorder: PurchaseRecorder

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .addCallback(AppDatabase.SEED_ON_CREATE)
            .build()
        recorder = PurchaseRecorder(db, clock = { 1_000L })
    }

    @After
    fun tearDown() = db.close()

    private fun notification(
        text: String,
        key: String = "0|com.chase|1|null|10123",
        packageName: String = "com.chase",
    ) = CapturedNotification(
        packageName = packageName,
        appLabel = "Chase",
        key = key,
        title = "Chase",
        text = text,
        isMessage = false,
        postedAt = 5_000L,
    )

    private suspend fun categoryId(name: String, kind: Kind = Kind.EXPENSE) =
        db.categoryDao().findByName(kind, name)!!.id

    @Test
    fun recordsAPurchaseForReviewWithAGuessedCategory() = runBlocking {
        val id = recorder.record(notification("You spent \$12.34 at STARBUCKS"))
        val txn = db.txnDao().get(id!!)!!
        assertEquals(1234L, txn.amountCents)
        assertEquals("Starbucks", txn.merchant)
        assertEquals(categoryId(StarterCategories.DINING), txn.categoryId)
        assertEquals(Source.NOTIFICATION, txn.source)
        assertTrue(txn.needsReview)
        assertEquals(5_000L, txn.occurredAt)

        val app = db.automationDao().getApp("com.chase")!!
        assertEquals(1, app.capturedCount)
        assertTrue(app.enabled)
    }

    @Test
    fun ignoresWhatIsNotAPurchase() = runBlocking {
        assertNull(recorder.record(notification("Your statement is ready")))
        assertNull(db.automationDao().getApp("com.chase"))
    }

    @Test
    fun theSameNotificationPostedTwiceIsRecordedOnce() = runBlocking {
        assertNotNull(recorder.record(notification("You spent \$12.34 at STARBUCKS")))
        assertNull(recorder.record(notification("You spent \$12.34 at STARBUCKS")))
        assertEquals(1, db.txnDao().observeReviewCount().first())
        assertEquals(1, db.automationDao().getApp("com.chase")!!.capturedCount)
    }

    @Test
    fun anIdenticalPurchaseInANewNotificationIsRecordedAgain() = runBlocking {
        assertNotNull(recorder.record(notification("You spent \$4.50 at STARBUCKS", key = "a")))
        assertNotNull(recorder.record(notification("You spent \$4.50 at STARBUCKS", key = "b")))
        assertEquals(2, db.txnDao().observeReviewCount().first())
    }

    @Test
    fun aDiscardedCaptureDoesNotComeBack() = runBlocking {
        val id = recorder.record(notification("You spent \$12.34 at STARBUCKS"))!!
        recorder.discard(db.txnDao().get(id)!!)
        assertNull(recorder.record(notification("You spent \$12.34 at STARBUCKS")))
        assertEquals(0, db.txnDao().observeReviewCount().first())
    }

    @Test
    fun restoringADiscardLetsItBack() = runBlocking {
        val id = recorder.record(notification("You spent \$12.34 at STARBUCKS"))!!
        val txn = db.txnDao().get(id)!!
        recorder.discard(txn)
        recorder.restore(txn)
        assertNotNull(db.txnDao().get(id))
        assertNull(recorder.record(notification("You spent \$12.34 at STARBUCKS")))
    }

    @Test
    fun aTurnedOffAppIsIgnored() = runBlocking {
        recorder.record(notification("You spent \$1.00 at STARBUCKS", key = "a"))
        db.automationDao().setAppEnabled("com.chase", false)
        assertNull(recorder.record(notification("You spent \$2.00 at STARBUCKS", key = "b")))
    }

    @Test
    fun aLearnedRuleBeatsTheKeywordGuess() = runBlocking {
        val shopping = categoryId(StarterCategories.SHOPPING)
        recorder.learn("Starbucks", shopping)
        val id = recorder.record(notification("You spent \$12.34 at STARBUCKS"))!!
        assertEquals(shopping, db.txnDao().get(id)!!.categoryId)
    }

    @Test
    fun aRuleForTheWrongKindIsIgnored() = runBlocking {
        recorder.learn("Jordan Lee", categoryId(StarterCategories.OTHER_INCOME, Kind.INCOME))
        val id = recorder.record(notification("You paid Jordan Lee \$20.00", packageName = "com.venmo"))!!
        assertNull(db.txnDao().get(id)!!.categoryId)
    }
}
