package com.example.tally.data

import com.example.tally.data.model.Kind

/**
 * Categories a new install starts with. They're ordinary categories the user can rename or delete.
 * [com.example.tally.capture.Categorizer] files purchases under these names when it can.
 */
object StarterCategories {
    data class Starter(val name: String, val kind: Kind, val iconKey: String, val color: Int)

    const val GROCERIES = "Groceries"
    const val DINING = "Dining"
    const val TRANSPORT = "Transport"
    const val SHOPPING = "Shopping"
    const val BILLS = "Bills"
    const val ENTERTAINMENT = "Entertainment"
    const val HEALTH = "Health"
    const val TRAVEL = "Travel"
    const val OTHER = "Other"
    const val PAYCHECK = "Paycheck"
    const val REFUNDS = "Refunds"
    const val OTHER_INCOME = "Other income"

    val ALL = listOf(
        Starter(GROCERIES, Kind.EXPENSE, "groceries", 0xFF4E9A3E.toInt()),
        Starter(DINING, Kind.EXPENSE, "dining", 0xFFE07A3F.toInt()),
        Starter(TRANSPORT, Kind.EXPENSE, "transport", 0xFF3F7CC4.toInt()),
        Starter(SHOPPING, Kind.EXPENSE, "shopping", 0xFFC2508A.toInt()),
        Starter(BILLS, Kind.EXPENSE, "bills", 0xFFD4A62A.toInt()),
        Starter(ENTERTAINMENT, Kind.EXPENSE, "entertainment", 0xFF7B61C9.toInt()),
        Starter(HEALTH, Kind.EXPENSE, "health", 0xFFCF5151.toInt()),
        Starter(TRAVEL, Kind.EXPENSE, "travel", 0xFF3A9BB0.toInt()),
        Starter(OTHER, Kind.EXPENSE, "other", 0xFF8A6E5A.toInt()),
        Starter(PAYCHECK, Kind.INCOME, "paycheck", 0xFF2E8B6E.toInt()),
        Starter(REFUNDS, Kind.INCOME, "refund", 0xFF5C6BC0.toInt()),
        Starter(OTHER_INCOME, Kind.INCOME, "savings", 0xFF26A69A.toInt()),
    )

    /** Colors handed to categories the user creates, in turn. */
    val PALETTE = ALL.map { it.color }
}
