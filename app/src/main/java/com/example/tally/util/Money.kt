package com.example.tally.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Amounts are stored as whole cents in a Long and shown in the phone's currency. Tally doesn't
 * convert between currencies: a captured "€12.00" is stored as 1200 like any other amount.
 */
object Money {
    fun format(cents: Long): String = formatter().format(BigDecimal.valueOf(cents, 2))

    /** Drops ".00", for budgets: "$400" rather than "$400.00". */
    fun formatShort(cents: Long): String =
        if (cents % 100 == 0L) formatter().apply { maximumFractionDigits = 0 }.format(cents / 100) else format(cents)

    val symbol: String
        get() = runCatching { Currency.getInstance(Locale.getDefault()).getSymbol(Locale.getDefault()) }
            .getOrDefault("$")

    /**
     * Reads what the user typed: "12", "12.5", "1,234.56", or "12,50" where a comma is the decimal
     * separator. Returns null for anything else, including more than two decimal places.
     */
    fun parseInput(input: String): Long? {
        var s = input.filter { it.isDigit() || it == '.' || it == ',' }
        if (s.isEmpty()) return null
        val lastDot = s.lastIndexOf('.')
        val lastComma = s.lastIndexOf(',')
        s = when {
            lastDot >= 0 && lastComma >= 0 ->
                if (lastDot > lastComma) s.replace(",", "") else s.replace(".", "").replace(',', '.')
            lastComma >= 0 -> {
                val decimals = s.length - lastComma - 1
                if (decimals in 1..2 && s.count { it == ',' } == 1) s.replace(',', '.') else s.replace(",", "")
            }
            else -> s
        }
        if (s.count { it == '.' } > 1 || s == ".") return null
        if (s.substringAfter('.', "").length > 2) return null
        return try {
            BigDecimal(s).movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
        } catch (_: ArithmeticException) {
            null
        } catch (_: NumberFormatException) {
            null
        }
    }

    /** The text an edit field starts with: "12" or "12.50". */
    fun toInput(cents: Long): String =
        if (cents % 100 == 0L) (cents / 100).toString() else BigDecimal.valueOf(cents, 2).toPlainString()

    private fun formatter(): NumberFormat = NumberFormat.getCurrencyInstance(Locale.getDefault())
}
