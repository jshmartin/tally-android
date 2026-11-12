package com.example.tally.capture

import com.example.tally.data.model.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PurchaseParserTest {
    private fun parse(text: String, title: String? = null, app: String? = null, message: Boolean = false) =
        PurchaseParser.parse(NotificationText(title = title, text = text, isMessage = message, appLabel = app))

    private fun assertPurchase(expected: ParsedPurchase, actual: ParsedPurchase?) = assertEquals(expected, actual)

    // Card and bank alerts

    @Test
    fun transactionWithMerchant() = assertPurchase(
        ParsedPurchase(1234, Kind.EXPENSE, "Starbucks"),
        parse("You made a \$12.34 transaction with STARBUCKS", title = "Chase", app = "Chase"),
    )

    @Test
    fun longAlertStopsAtTheDateAndIgnoresTheTime() = assertPurchase(
        ParsedPurchase(1234, Kind.EXPENSE, "Starbucks"),
        parse("You made a \$12.34 transaction with STARBUCKS STORE 12345 on Nov 10, 2026 at 3:42 PM ET."),
    )

    @Test
    fun purchaseAtMerchantWithStoreNumber() = assertPurchase(
        ParsedPurchase(4567, Kind.EXPENSE, "Target"),
        parse("A purchase of \$45.67 at TARGET 00012345 was charged to your account ending in 1234."),
    )

    @Test
    fun merchantBeforeForClause() = assertPurchase(
        ParsedPurchase(8210, Kind.EXPENSE, "Whole Foods Market"),
        parse("Purchase at Whole Foods Market for \$82.10 on card ending 1234"),
    )

    @Test
    fun squarePrefixIsStripped() = assertPurchase(
        ParsedPurchase(625, Kind.EXPENSE, "Blue Door Cafe"),
        parse("You spent \$6.25 at SQ *BLUE DOOR CAFE"),
    )

    @Test
    fun thousandsSeparatorAndNoCents() = assertPurchase(
        ParsedPurchase(120000, Kind.EXPENSE, "Best Buy"),
        parse("You spent \$1,200 at BEST BUY"),
    )

    @Test
    fun timeAfterAtIsNotAMerchant() = assertPurchase(
        ParsedPurchase(300, Kind.EXPENSE, null),
        parse("You spent \$3.00 at 10:42 AM", title = "Card alert"),
    )

    @Test
    fun balanceAfterThePurchaseIsIgnored() = assertPurchase(
        ParsedPurchase(1234, Kind.EXPENSE, "Starbucks"),
        parse("You spent \$12.34 at Starbucks. Available balance: \$1,000.00."),
    )

    @Test
    fun authorizationIsSpending() = assertPurchase(
        ParsedPurchase(2500, Kind.EXPENSE, "Tim Hortons"),
        parse("An authorization of \$25.00 was made on your credit card ending in 1234 at TIM HORTONS #4521."),
    )

    @Test
    fun notAuthorizedIsIgnored() =
        assertNull(parse("A transaction of \$25.00 at TIM HORTONS was not authorized on your card ending in 1234."))

    @Test
    fun rupeesDebitedIsSpending() = assertPurchase(
        ParsedPurchase(50000, Kind.EXPENSE, "Swiggy"),
        parse("Rs. 500.00 debited from a/c XX1234 at SWIGGY on 10-11-26"),
    )

    @Test
    fun debitedThenCreditedToMerchantIsSpending() = assertEquals(
        Kind.EXPENSE,
        parse("Rs 250 debited from A/c XX1234 and credited to ZOMATO via UPI")?.kind,
    )

    @Test
    fun amountUnderADollarWithoutALeadingZero() = assertPurchase(
        ParsedPurchase(99, Kind.EXPENSE, "Dollarama"),
        parse("You spent \$.99 at DOLLARAMA"),
    )

    // Alerts with no wording, only the card, the merchant and the amount (CIBC)

    @Test
    fun cardMerchantAndAmountOnALineEach() = assertPurchase(
        ParsedPurchase(447, Kind.EXPENSE, "Canadian Tire"),
        // CIBC indents the second and third lines by a space.
        parse("CIBC Costco World Mastercard, 1234\n CANADIAN TIRE #608\n \$4.47", app = "CIBC"),
    )

    @Test
    fun cardAlertUnderADollar() = assertPurchase(
        ParsedPurchase(67, Kind.EXPENSE, "Pixel Games"),
        parse("CIBC Dividend Visa Card, 1234\nGOOGLE *PIXEL GAMES\n\$.67", app = "CIBC"),
    )

    @Test
    fun cardAlertKeepsAMerchantThatReadsLikeWording() = assertPurchase(
        ParsedPurchase(1200, Kind.EXPENSE, "Paid Parking Refund Depot"),
        parse("CIBC Dividend Visa Card, 1234\nPAID PARKING REFUND DEPOT\n\$12.00", app = "CIBC"),
    )

    @Test
    fun cardAlertWithItsLinesRunTogether() = assertPurchase(
        ParsedPurchase(1470, Kind.EXPENSE, "Corner Books"),
        parse("CIBC Dividend Visa Card, 1234 PAYPAL *CORNER BOOKS \$14.70", app = "CIBC"),
    )

    @Test
    fun cardAlertWithAMinusSignIsMoneyBack() = assertPurchase(
        ParsedPurchase(1470, Kind.INCOME, "Corner Books"),
        parse("CIBC Dividend Visa Card, 1234\nPAYPAL *CORNER BOOKS\n-\$14.70", app = "CIBC"),
    )

    @Test
    fun cardAlertForACashBackCardIsNotAPromotion() = assertPurchase(
        ParsedPurchase(4000, Kind.EXPENSE, "Shell"),
        parse("Maple Cash Back Visa Card, 1234\nSHELL\n\$40.00"),
    )

    @Test
    fun declinedCardAlertIsIgnored() =
        assertNull(parse("CIBC Dividend Visa Card, 1234\nSHELL\n\$12.00", title = "Transaction declined"))

    @Test
    fun cardLineWithoutAnAmountLineIsIgnored() =
        assertNull(parse("CIBC Dividend Visa Card, 1234\nYour statement is ready\nBalance \$450.00"))

    @Test
    fun sentenceAboutACardIsNotACardAlert() =
        assertNull(parse("Your Visa card ending in 1234 has a balance of \$500.00"))

    // Wallets and payment apps

    @Test
    fun walletPutsMerchantInTheTitle() = assertPurchase(
        ParsedPurchase(575, Kind.EXPENSE, "Blue Bottle Coffee"),
        parse("\$5.75 with Visa •••• 4242", title = "Blue Bottle Coffee", app = "Google Wallet"),
    )

    @Test
    fun titleThatRepeatsTheAppIsNotAMerchant() = assertPurchase(
        ParsedPurchase(575, Kind.EXPENSE, null),
        parse("\$5.75 with Visa •••• 4242", title = "Wallet", app = "Google Wallet"),
    )

    // Samsung Wallet: the card's name as the title, then the merchant and the amount

    @Test
    fun walletPutsTheCardInTheTitle() = assertPurchase(
        ParsedPurchase(2105, Kind.EXPENSE, "Canadian Tire"),
        parse("CANADIAN TIRE #123 \$21.05", title = "CIBC Costco Mastercard", app = "Samsung Wallet"),
    )

    @Test
    fun cardNamedForItsCashBackIsNotAPromotion() = assertPurchase(
        ParsedPurchase(4000, Kind.EXPENSE, "Shell"),
        parse("SHELL \$40.00", title = "Maple Cash Back Visa", app = "Samsung Wallet"),
    )

    @Test
    fun debitCardTitleKeepsTheMerchant() = assertPurchase(
        ParsedPurchase(899, Kind.EXPENSE, "Corner Market"),
        parse("CORNER MARKET \$8.99", title = "Maple Debit Card", app = "Samsung Wallet"),
    )

    @Test
    fun declineUnderACardTitleIsIgnored() =
        assertNull(parse("SHELL \$12.00", title = "Card declined", app = "Samsung Wallet"))

    @Test
    fun balanceUnderACardTitleIsIgnored() =
        assertNull(parse("Your balance is \$450.00", title = "Maple Visa Card"))

    @Test
    fun sentenceUnderACardTitleKeepsItsMerchant() = assertPurchase(
        ParsedPurchase(1234, Kind.EXPENSE, "Starbucks"),
        parse("Purchase at Starbucks for \$12.34", title = "Maple Visa Card"),
    )

    // PayPal

    @Test
    fun payPalPutsTheMerchantInTheTitle() = assertPurchase(
        ParsedPurchase(4250, Kind.EXPENSE, "Northwind Games LLC"),
        parse(
            "Your \$42.50 payment was completed. Tap for more details.",
            title = "Your purchase at Northwind Games LLC",
            app = "PayPal",
        ),
    )

    @Test
    fun payPalIsNotItsOwnMerchant() = assertPurchase(
        ParsedPurchase(990, Kind.EXPENSE, null),
        parse(
            "Your \$9.90 payment was completed. Tap for more details.",
            title = "Your purchase with PayPal",
            app = "PayPal",
        ),
    )

    @Test
    fun youPaidSomeone() = assertPurchase(
        ParsedPurchase(2000, Kind.EXPENSE, "Jordan Lee"),
        parse("You paid Jordan Lee \$20.00", title = "Venmo", app = "Venmo"),
    )

    @Test
    fun someonePaidYou() = assertPurchase(
        ParsedPurchase(1550, Kind.INCOME, "Jordan Lee"),
        parse("Jordan Lee paid you \$15.50", title = "Venmo", app = "Venmo"),
    )

    @Test
    fun receivedFrom() = assertPurchase(
        ParsedPurchase(4000, Kind.INCOME, "Sam"),
        parse("You received \$40 from Sam", app = "Cash App"),
    )

    @Test
    fun sentToWithCurrencyCode() = assertPurchase(
        ParsedPurchase(2500, Kind.EXPENSE, "Alex Kim"),
        parse("You sent \$25.00 USD to Alex Kim", app = "PayPal"),
    )

    @Test
    fun paymentToBiller() = assertPurchase(
        ParsedPurchase(5000, Kind.EXPENSE, "Comcast"),
        parse("Payment of \$50 to COMCAST was successful"),
    )

    // Money coming in

    @Test
    fun refundIsIncome() = assertPurchase(
        ParsedPurchase(1999, Kind.INCOME, "Amazon.com"),
        parse("A refund of \$19.99 from AMAZON.COM has been credited to your card"),
    )

    @Test
    fun refundForAPurchaseIsStillIncome() = assertEquals(
        Kind.INCOME,
        parse("Your purchase of \$12.00 at Target was refunded")?.kind,
    )

    @Test
    fun directDeposit() = assertPurchase(
        ParsedPurchase(125000, Kind.INCOME, null),
        parse("Direct deposit of \$1,250.00 received", title = "Ally Bank", app = "Ally"),
    )

    // Not purchases

    @Test
    fun declinedIsIgnored() = assertNull(parse("Your card was declined for \$12.00 at SHELL"))

    @Test
    fun promotionIsIgnored() = assertNull(parse("Get \$10 off your next purchase at Starbucks!"))

    @Test
    fun percentOffIsIgnored() = assertNull(parse("Save 20% off when you spend \$50 or more"))

    @Test
    fun balanceIsIgnored() = assertNull(parse("Your available balance is \$1,234.56"))

    @Test
    fun billDueIsIgnored() = assertNull(parse("Your payment of \$50.00 is due on Nov 15"))

    @Test
    fun paymentThanksIsIgnored() = assertNull(parse("Thank you for your payment of \$500.00"))

    @Test
    fun requestIsIgnored() = assertNull(parse("Jordan requested \$20.00", app = "Venmo"))

    @Test
    fun cashBackIsIgnored() = assertNull(parse("You earned \$1.20 cash back on your purchase"))

    @Test
    fun scheduledChargeIsIgnored() = assertNull(parse("Your subscription renews tomorrow and you will be charged \$9.99"))

    @Test
    fun noAmountIsIgnored() = assertNull(parse("Your order has shipped", title = "Amazon"))

    @Test
    fun amountWithoutAVerbIsIgnored() = assertNull(parse("Your order total: \$45.67", title = "Order confirmed"))

    // Chats and SMS

    @Test
    fun chatAboutMoneyIsIgnored() = assertNull(parse("I paid \$40 for the tickets", title = "Sam", message = true))

    @Test
    fun bankTextMessageCounts() = assertPurchase(
        ParsedPurchase(850, Kind.EXPENSE, "Chipotle"),
        parse("Chase: Debit card purchase of \$8.50 at CHIPOTLE 1234", title = "72166", message = true),
    )

    // Anyone can type a wordless alert's layout into a chat.

    @Test
    fun cardLayoutInAChatIsIgnored() =
        assertNull(parse("CIBC Dividend Visa Card, 1234\nSHELL\n\$40.00", title = "Sam", message = true))

    @Test
    fun runTogetherCardLayoutInAChatIsIgnored() =
        assertNull(parse("CIBC Dividend Visa Card, 1234 SHELL \$40.00", title = "Sam", message = true))

    @Test
    fun walletLayoutInAChatIsIgnored() =
        assertNull(parse("SHELL \$40.00", title = "Maple Visa Card", message = true))

    // Long text

    @Test
    fun alertAtTheLengthLimitIsRead() = assertPurchase(
        ParsedPurchase(1234, Kind.EXPENSE, "Starbucks"),
        parse(("You spent \$12.34 at STARBUCKS. " + "Tap for details. ".repeat(100)).take(PurchaseParser.MAX_LENGTH)),
    )

    @Test
    fun textOverTheLengthLimitIsIgnored() =
        assertNull(parse("You spent \$12.34 at STARBUCKS. " + "Tap for details. ".repeat(100)))

    /**
     * Text built to make the patterns try every way to split it, at the length limit and at 5,120
     * characters, the most a notification's text normally holds. Before the limit, the first shape
     * took seconds.
     */
    @Test(timeout = 2_000)
    fun textBuiltToBeSlowIsReadQuickly() {
        val shapes = listOf("card, 1234 ", "visa card ", "-\$1 card, 1234 ", "paid to ", "at at ", "\$1 ", "SQ *# 1234 ")
        for (shape in shapes) {
            for (length in listOf(PurchaseParser.MAX_LENGTH, 5_120)) {
                val text = shape.repeat(length / shape.length + 1).take(length)
                parse(text)
                parse(text.dropLast(20), title = "Maple Visa Card")
                parse(text.replace(' ', '\n'))
                parse(text, message = true)
            }
        }
    }
}
