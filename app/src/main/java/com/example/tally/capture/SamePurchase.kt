package com.example.tally.capture

/** What one app's alert says about a purchase, as far as matching it with another app's alert goes. */
data class Sighting(
    val appLabel: String,
    /** The alert's title and text. */
    val text: String,
    val amountCents: Long,
    /** Empty when the alert didn't name one. */
    val merchant: String,
)

/**
 * Tells when two apps' alerts are about one purchase. A tap to pay is reported by the wallet and by
 * the card's bank, and a PayPal purchase by PayPal and by the card behind it.
 *
 * The caller checks the rest: that the alerts are from different apps, of the same kind, and arrived
 * within [WINDOW_MS] of each other.
 */
object SamePurchase {
    /** A bank's alert usually follows the wallet's within seconds, but it can lag. */
    const val WINDOW_MS = 5 * 60_000L

    fun matches(a: Sighting, b: Sighting): Boolean =
        paidThrough(card = a, app = b) || paidThrough(card = b, app = a) ||
            (a.amountCents == b.amountCents && sameMerchant(a.merchant, b.merchant))

    /**
     * Whether [card]'s alert shows the purchase went through [app], as "PAYPAL *CORNER BOOKS" on a
     * card's alert does for PayPal. The two amounts can differ then: PayPal states the price in the
     * seller's currency and the card states what it was charged.
     */
    fun paidThrough(card: Sighting, app: Sighting): Boolean {
        val label = app.appLabel.trim()
        if (label.length < 3) return false
        return Regex("""\b${Regex.escape(label)}\s?\*""", RegexOption.IGNORE_CASE).containsMatchIn(card.text)
    }

    /**
     * An alert without a merchant agrees with any. Two names agree when they start alike, since the
     * bank often adds a city or a store number: "Tim Hortons" and "Tim Hortons Toronto".
     */
    private fun sameMerchant(a: String, b: String): Boolean {
        val x = Categorizer.merchantKey(a)
        val y = Categorizer.merchantKey(b)
        return x.isEmpty() || y.isEmpty() || x.startsWith(y) || y.startsWith(x) || x.commonPrefixWith(y).length >= 6
    }
}
