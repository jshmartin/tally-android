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
import org.junit.Assert.assertFalse
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

    // A tap to pay, as Samsung Wallet and then the card's bank report it.

    private fun wallet(text: String = "CANADIAN TIRE #123 \$21.05", key: String = "w1", postedAt: Long = 5_000L) =
        CapturedNotification("com.samsung.android.spay", "Samsung Wallet", key, "CIBC Costco Mastercard", text, false, postedAt)

    private fun bank(
        merchant: String = "CANADIAN TIRE #123",
        amount: String = "\$21.05",
        key: String = "b1",
        postedAt: Long = 9_000L,
    ) = CapturedNotification(
        "com.cibc.android.mobi", "CIBC", key, null, "CIBC Costco World Mastercard, 1234\n$merchant\n$amount", false, postedAt,
    )

    private fun payPal(title: String = "Your purchase with PayPal", postedAt: Long = 9_000L) = CapturedNotification(
        "com.paypal.android.p2pmobile", "PayPal", "p1", title, "Your \$9.90 payment was completed. Tap for more details.",
        false, postedAt,
    )

    private suspend fun count() = db.txnDao().observeRows(0, Long.MAX_VALUE).first().size

    private suspend fun rows() = db.txnDao().observeRows(0, Long.MAX_VALUE).first()

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
    fun aWalletAndItsBankReportOnePurchase() = runBlocking {
        val first = recorder.record(wallet())!!
        assertEquals(first, recorder.record(bank()))

        assertEquals(1, count())
        val txn = db.txnDao().get(first)!!
        assertEquals(2105L, txn.amountCents)
        assertEquals("Canadian Tire", txn.merchant)
        assertEquals("com.samsung.android.spay", txn.sourceApp)
        assertEquals(5_000L, txn.occurredAt)
        assertTrue(txn.needsReview)
        assertTrue(txn.sourceText!!.endsWith("CIBC:\nCIBC Costco World Mastercard, 1234\nCANADIAN TIRE #123\n\$21.05"))
        // Both apps show up as sources, so either can be turned off.
        assertEquals(1, db.automationDao().getApp("com.cibc.android.mobi")!!.capturedCount)
    }

    @Test
    fun theBankFirstThenTheWalletIsAlsoOnePurchase() = runBlocking {
        val first = recorder.record(bank(postedAt = 5_000L))!!
        assertEquals(first, recorder.record(wallet(postedAt = 9_000L)))
        assertEquals(1, count())
    }

    @Test
    fun aSecondAlertFromTheSameBankIsAnotherPurchase() = runBlocking {
        recorder.record(wallet())
        recorder.record(bank(key = "b1"))
        // The same amount again, this time paid with the card itself, so only the bank reports it.
        assertNotNull(recorder.record(bank(key = "b2", postedAt = 20_000L)))
        assertEquals(2, count())
    }

    @Test
    fun twoTapsInARowAreTwoPurchases() = runBlocking {
        recorder.record(wallet(key = "w1", postedAt = 5_000L))
        recorder.record(wallet(key = "w2", postedAt = 6_000L))
        recorder.record(bank(key = "b1", postedAt = 9_000L))
        recorder.record(bank(key = "b2", postedAt = 10_000L))
        assertEquals(2, count())
    }

    @Test
    fun aDifferentAmountIsAnotherPurchase() = runBlocking {
        recorder.record(wallet())
        assertNotNull(recorder.record(bank(amount = "\$4.47")))
        assertEquals(2, count())
    }

    @Test
    fun theSameAmountSomewhereElseIsAnotherPurchase() = runBlocking {
        recorder.record(wallet())
        assertNotNull(recorder.record(bank(merchant = "SHELL")))
        assertEquals(2, count())
    }

    @Test
    fun anAlertLongAfterwardsIsAnotherPurchase() = runBlocking {
        recorder.record(wallet(postedAt = 5_000L))
        assertNotNull(recorder.record(bank(postedAt = 5_000L + SamePurchase.WINDOW_MS + 1)))
        assertEquals(2, count())
    }

    @Test
    fun aMergedAlertPostedAgainIsIgnored() = runBlocking {
        recorder.record(wallet())
        recorder.record(bank())
        assertNull(recorder.record(bank()))
        assertEquals(1, count())
        assertEquals(1, db.automationDao().getApp("com.cibc.android.mobi")!!.capturedCount)
    }

    @Test
    fun discardingAMergedPurchaseForgetsBothAlerts() = runBlocking {
        val id = recorder.record(wallet())!!
        recorder.record(bank())
        val txn = db.txnDao().get(id)!!
        recorder.discard(txn)
        assertNull(recorder.record(wallet()))
        assertNull(recorder.record(bank()))
        assertEquals(0, count())

        recorder.restore(txn)
        assertNull(recorder.record(bank()))
        assertEquals(1, count())
    }

    @Test
    fun aReviewedPurchaseKeepsWhatTheUserChecked() = runBlocking {
        val id = recorder.record(payPal(postedAt = 5_000L))!!
        val shopping = categoryId(StarterCategories.SHOPPING)
        db.txnDao().update(db.txnDao().get(id)!!.copy(categoryId = shopping, needsReview = false))
        assertEquals(id, recorder.record(bank(merchant = "PAYPAL *CORNER BOOKS", amount = "\$14.70")))
        val txn = db.txnDao().get(id)!!
        assertEquals(990L, txn.amountCents)
        assertEquals("", txn.merchant)
        assertEquals(shopping, txn.categoryId)
        assertFalse(txn.needsReview)
        assertEquals(1, count())
    }

    @Test
    fun payPalAndTheCardBehindItAreOnePurchaseAtTheCardsAmount() = runBlocking {
        // PayPal states the price in US dollars; the card was charged in Canadian dollars.
        val first = recorder.record(bank(merchant = "PAYPAL *CORNER BOOKS", amount = "\$14.70", postedAt = 5_000L))!!
        assertEquals(first, recorder.record(payPal(postedAt = 9_000L)))
        val txn = db.txnDao().get(first)!!
        assertEquals(1470L, txn.amountCents)
        assertEquals("Corner Books", txn.merchant)
        assertEquals(1, count())
    }

    @Test
    fun payPalFirstTakesTheCardsAmountAndKeepsPayPalsMerchant() = runBlocking {
        val first = recorder.record(payPal(title = "Your purchase at Northwind Games LLC", postedAt = 5_000L))!!
        assertEquals(first, recorder.record(bank(merchant = "PAYPAL *NORTHWINDGA", amount = "\$14.70", postedAt = 9_000L)))
        val txn = db.txnDao().get(first)!!
        assertEquals(1470L, txn.amountCents)
        assertEquals("Northwind Games LLC", txn.merchant)
        assertEquals(1, count())
    }

    @Test
    fun aRuleForTheWrongKindIsIgnored() = runBlocking {
        recorder.learn("Jordan Lee", categoryId(StarterCategories.OTHER_INCOME, Kind.INCOME))
        val id = recorder.record(notification("You paid Jordan Lee \$20.00", packageName = "com.venmo"))!!
        assertNull(db.txnDao().get(id)!!.categoryId)
    }
}
