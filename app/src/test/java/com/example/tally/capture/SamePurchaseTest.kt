package com.example.tally.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SamePurchaseTest {
    private val wallet = Sighting("Samsung Wallet", "CIBC Costco Mastercard\nCANADIAN TIRE #123 \$21.05", 2105, "Canadian Tire")
    private val bank = Sighting("CIBC", "CIBC Costco World Mastercard, 1234\nCANADIAN TIRE #123\n\$21.05", 2105, "Canadian Tire")
    private val payPal = Sighting("PayPal", "Your purchase with PayPal\nYour \$9.90 payment was completed.", 990, "")
    private val cardBehindPayPal = Sighting("CIBC", "CIBC Dividend Visa Card, 1234\nPAYPAL *CORNER BOOKS\n\$14.70", 1470, "Corner Books")

    @Test
    fun sameAmountAndMerchant() = assertTrue(SamePurchase.matches(wallet, bank))

    @Test
    fun differentAmounts() = assertFalse(SamePurchase.matches(wallet, bank.copy(amountCents = 2106)))

    @Test
    fun sameAmountAtAnotherMerchant() = assertFalse(SamePurchase.matches(wallet, bank.copy(merchant = "Shell")))

    @Test
    fun bankAddsACityToTheName() = assertTrue(SamePurchase.matches(wallet, bank.copy(merchant = "Canadian Tire Calgary")))

    @Test
    fun namesThatOnlyStartAlike() =
        assertFalse(SamePurchase.matches(wallet.copy(merchant = "Uber Trip"), bank.copy(merchant = "Uber Eats")))

    @Test
    fun oneAlertWithoutAMerchant() = assertTrue(SamePurchase.matches(wallet.copy(merchant = ""), bank))

    @Test
    fun cardAlertNamesTheAppItWasPaidThrough() {
        assertTrue(SamePurchase.paidThrough(card = cardBehindPayPal, app = payPal))
        assertFalse(SamePurchase.paidThrough(card = payPal, app = cardBehindPayPal))
        // The amounts differ, as they do when PayPal charges in another currency.
        assertTrue(SamePurchase.matches(payPal, cardBehindPayPal))
        assertTrue(SamePurchase.matches(cardBehindPayPal, payPal))
    }

    @Test
    fun bankNamedOnTheWalletsCardIsNotPaidThrough() {
        // "CIBC" is in the wallet's text as part of the card's name, not as "CIBC *MERCHANT".
        assertFalse(SamePurchase.paidThrough(card = wallet, app = bank))
        assertFalse(SamePurchase.matches(wallet, bank.copy(amountCents = 500)))
    }
}
