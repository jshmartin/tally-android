package com.example.tally.capture

import com.example.tally.data.model.Kind
import java.math.BigDecimal
import java.math.RoundingMode

/** What Tally reads from a notification. */
data class NotificationText(
    val title: String?,
    val text: String?,
    /** Chat and SMS notifications only count when they read like a bank alert. */
    val isMessage: Boolean = false,
    /** The posting app's name. A title that only repeats it isn't a merchant. */
    val appLabel: String? = null,
)

data class ParsedPurchase(
    val amountCents: Long,
    val kind: Kind,
    val merchant: String?,
)

/**
 * Picks a purchase, payment or deposit out of a notification's text, or says it isn't one.
 *
 * It works from wording rather than from a list of apps, so it handles banks it has never seen as
 * long as they write alerts the usual way: an amount with a currency sign, a word like "spent",
 * "purchase", "paid" or "received", and usually "at", "with", "to" or "from" before the merchant.
 * Promotions, declines, bills that are due and payment confirmations are ignored.
 */
object PurchaseParser {
    private const val NUMBER = """(?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d{1,2})?"""
    private const val SYMBOL = """(?:(?:US|CA|C|A)?\$|£|€|₹|\bRs\.?|\b(?:USD|EUR|GBP|CAD|AUD|INR)\b)"""

    private val AMOUNT = Regex(
        """$SYMBOL\s?($NUMBER)|\b($NUMBER)\s?(?:USD|EUR|GBP|CAD|AUD|INR)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** An alert that only states an amount and the card, as tap-to-pay wallets do. */
    private val CARD_TAP = Regex("""^(?:paid\s+)?$SYMBOL\s?$NUMBER\s+(?:with|on|using|via)\b""", RegexOption.IGNORE_CASE)

    // Matched against lowercased text.
    private val REJECT = Regex(
        """\b(?:declined|denied|not approved|was blocked|requested|requesting|verification|one[- ]time|otp|""" +
            """passcode|security code|coupons?|promo(?:tion)?s?|deals?|offers?|earn(?:ed)?|rewards?|points|""" +
            """cash ?back|free shipping|on sale|is due|due (?:on|by|date|soon)|minimum payment|""" +
            """thanks? (?:you )?for your payment|payment (?:was |has been |is )?(?:received|posted|scheduled|processing)|""" +
            """received your payment|auto-?pay|will be (?:charged|debited|paid|withdrawn|deducted)|upcoming|""" +
            """scheduled|reminder)\b|%\s*off|\boff (?:your|at|on)\b"""
    )
    private val STRONG_INCOME = Regex(
        """\b(?:paid you|sent you|you received|you got|refund(?:ed|s)?|revers(?:al|ed)|direct deposit)\b"""
    )
    private val WEAK_INCOME = Regex("""\b(?:received|deposit(?:ed|s)?|credited)\b""")
    private val EXPENSE = Regex(
        """\b(?:spent|spend|purchased?|purchases|charged?|charges|transaction|txn|debit(?:ed)?|paid|sent|""" +
            """payment|withdrawn|withdrawal|bought|used (?:your|at)|card was used)\b"""
    )
    private val BANK_WORDS = Regex(
        """\b(?:card|acct|account|a/c|ending|debited|credited|transaction|txn|purchase|upi|bank|spent|charged)\b"""
    )

    // Merchant patterns, matched against the original text. NAME stops at END.
    private const val NAME = """([^,;!:()\n]+?)"""
    private const val END =
        """(?=\s+(?:on|using|with|was|were|has|have|is|for|from|via|ending|card|at)\b|\.(?:\s|$)|[,;!:()\n]|\s+-\s|\s*$)"""
    private val AT = Regex("""\b(?:at|@)\s+$NAME$END""", RegexOption.IGNORE_CASE)
    private val WITH = Regex("""\b(?:transaction|purchase|payment)\s+with\s+$NAME$END""", RegexOption.IGNORE_CASE)
    private val PAID_NAME = Regex("""\bpaid\s+(?!$SYMBOL)([^,;!:()\n£€₹$]+?)\s*(?=$SYMBOL)""", RegexOption.IGNORE_CASE)
    private val TO = Regex(
        """\b(?:sent|paid|payment|transfer(?:red)?|transaction)\b.*?\bto\s+$NAME$END""",
        RegexOption.IGNORE_CASE,
    )
    private val FROM = Regex("""\bfrom\s+$NAME$END""", RegexOption.IGNORE_CASE)
    private val PAID_YOU = Regex("""^$NAME\s+(?:paid|sent)\s+you\b""", RegexOption.IGNORE_CASE)

    private val EXPENSE_MERCHANT = listOf(AT, WITH, PAID_NAME, TO)
    private val INCOME_MERCHANT = listOf(PAID_YOU, FROM, AT)

    /** Card processors' prefixes, as in "SQ *BLUE DOOR CAFE". */
    private val PREFIX = Regex("""^(?:sq|tst|sp|pp|paypal|dd|ic|py|apl|gpay|google)\s?\*\s*""", RegexOption.IGNORE_CASE)
    /** Store numbers, as in "STARBUCKS #1234" or "TARGET 00012345". */
    private val STORE_NUMBER = Regex(
        """(?:\s*#\s*\d+.*|\s+(?:store\s+)?(?:no\.?\s*)?\d{3,}.*|\s+[a-z]{1,2}-\d{3,}.*)$""",
        RegexOption.IGNORE_CASE,
    )
    private val NOT_A_MERCHANT = Regex(
        """^(?:(?:your|you|my|account|acct|card|xx|this|that)\b|the (?:card|account)\b|a/c\b|x{2,}\d|\*+\d|""" +
            """\d{1,2}(?::\d{2})?\s?(?:am|pm)\b)""",
        RegexOption.IGNORE_CASE,
    )
    private val GENERIC_TITLE = Regex(
        """\b(?:alerts?|transactions?|purchases?|payments?|paid|cards?|accounts?|bank|banking|notifications?|""" +
            """deposits?|refunds?|spent|received|charges?|activity|security|update|heads up|you|your|new|money|""" +
            """wallet|pay)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val ODD_SPACES = Regex("""[    ]""")
    private val LINE_BREAKS = Regex("""\s*\n+\s*""")
    private val SPACES = Regex("""[ \t]+""")

    fun parse(notification: NotificationText): ParsedPurchase? {
        val title = tidy(notification.title)
        val text = tidy(notification.text)
        if (title.isEmpty() && text.isEmpty()) return null
        val lower = listOf(title, text).filter { it.isNotEmpty() }.joinToString("\n").lowercase()

        if (REJECT.containsMatchIn(lower)) return null
        val kind = kindOf(lower) ?: if (CARD_TAP.containsMatchIn(text)) Kind.EXPENSE else return null
        if (notification.isMessage && !BANK_WORDS.containsMatchIn(lower)) return null

        val amount = AMOUNT.find(text) ?: AMOUNT.find(title) ?: return null
        val cents = toCents(amount.groupValues[1].ifEmpty { amount.groupValues[2] }) ?: return null
        if (cents <= 0) return null

        val patterns = if (kind == Kind.INCOME) INCOME_MERCHANT else EXPENSE_MERCHANT
        val merchant = findMerchant(text, patterns)
            ?: findMerchant(title, patterns)
            ?: merchantFromTitle(notification, title)
        return ParsedPurchase(cents, kind, merchant)
    }

    /**
     * Refunds and "paid you" always mean money in. Otherwise a plain "received" or "credited" only
     * wins when it comes before the first spending word, so "debited from ... credited to" is spending.
     */
    private fun kindOf(lower: String): Kind? {
        if (STRONG_INCOME.containsMatchIn(lower)) return Kind.INCOME
        val income = WEAK_INCOME.find(lower)?.range?.first
        val expense = EXPENSE.find(lower)?.range?.first
        return when {
            income != null && (expense == null || income < expense) -> Kind.INCOME
            expense != null -> Kind.EXPENSE
            else -> null
        }
    }

    private fun findMerchant(text: String, patterns: List<Regex>): String? {
        if (text.isEmpty()) return null
        for (pattern in patterns) {
            for (match in pattern.findAll(text)) {
                cleanMerchant(match.groupValues[1])?.let { return it }
            }
        }
        return null
    }

    /** Wallets often put the merchant in the title and only the amount and card in the text. */
    private fun merchantFromTitle(notification: NotificationText, title: String): String? {
        if (notification.isMessage || title.isEmpty() || title.length > 40) return null
        val app = notification.appLabel?.trim().orEmpty()
        if (app.isNotEmpty() && (app.contains(title, ignoreCase = true) || title.contains(app, ignoreCase = true))) {
            return null
        }
        if (GENERIC_TITLE.containsMatchIn(title) || AMOUNT.containsMatchIn(title)) return null
        return cleanMerchant(title)
    }

    private fun cleanMerchant(raw: String): String? {
        var name = PREFIX.replace(raw.trim(), "")
        name = STORE_NUMBER.replace(name, "")
        name = name.trim().trimEnd('.', '-', '*', ' ').trimStart('*', ' ')
        if (name.length < 2 || name.none { it.isLetter() }) return null
        if (NOT_A_MERCHANT.containsMatchIn(name)) return null
        if (name.length > 40) name = name.take(40).trimEnd()
        return if (name.any { it.isLowerCase() }) name else titleCase(name)
    }

    private fun titleCase(name: String): String =
        name.lowercase().split(' ').joinToString(" ") { word -> word.replaceFirstChar { it.titlecase() } }

    private fun toCents(number: String): Long? = try {
        BigDecimal(number.replace(",", "")).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
    } catch (_: ArithmeticException) {
        null
    } catch (_: NumberFormatException) {
        null
    }

    private fun tidy(s: String?): String = s.orEmpty()
        .replace(ODD_SPACES, " ")
        .replace(LINE_BREAKS, "\n")
        .replace(SPACES, " ")
        .trim()
}
