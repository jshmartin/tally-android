package com.example.tally.data.model

/** A transaction joined with its category and source app, for lists. */
data class TxnRow(
    val id: Long,
    val amountCents: Long,
    val kind: Kind,
    val merchant: String,
    val note: String,
    val occurredAt: Long,
    val source: Source,
    val sourceApp: String?,
    val sourceAppLabel: String?,
    val sourceText: String?,
    val needsReview: Boolean,
    /**
     * Whether it counts toward totals and budgets. A capture from an app the user hasn't kept one from
     * yet doesn't, until it's kept.
     */
    val counted: Boolean,
    val categoryId: Long?,
    val categoryName: String?,
    val categoryIcon: String?,
    val categoryColor: Int?,
)

/** A category with what went through it in one month. */
data class CategorySpending(
    val id: Long,
    val name: String,
    val kind: Kind,
    val iconKey: String,
    val color: Int,
    val monthlyBudgetCents: Long?,
    val sortOrder: Int,
    val totalCents: Long,
    val count: Int,
)

data class MonthTotals(
    val spentCents: Long,
    val incomeCents: Long,
)

data class RuleRow(
    val merchantKey: String,
    val merchant: String,
    val categoryId: Long,
    val categoryName: String,
    val iconKey: String,
    val color: Int,
)
