package com.example.tally.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class Kind { EXPENSE, INCOME }

/** Where a transaction came from. */
enum class Source { MANUAL, NOTIFICATION }

@Entity(tableName = "categories", indices = [Index("kind")])
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: Kind,
    /** A key into the UI's icon set, so the database doesn't depend on icon classes. */
    val iconKey: String,
    /** ARGB. */
    val color: Int,
    /** The same budget applies every month. Only expense categories have one; null means no budget. */
    val monthlyBudgetCents: Long?,
    val sortOrder: Int,
)

/**
 * One expense or income. Amounts are always positive; [kind] says which way the money went.
 *
 * Purchases captured from notifications start with [needsReview] set, and keep the notification's
 * text in [sourceText] so the user can check what Tally read.
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("categoryId"),
        Index("occurredAt"),
        Index("needsReview"),
        // Null for manual entries; SQLite lets any number of rows share a null.
        Index(value = ["dedupeKey"], unique = true),
    ],
)
data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountCents: Long,
    val kind: Kind,
    /** Empty when there isn't one. */
    val merchant: String,
    val note: String = "",
    val categoryId: Long?,
    val occurredAt: Long,
    val source: Source = Source.MANUAL,
    /** Package name of the app whose notification this came from. */
    val sourceApp: String? = null,
    val sourceText: String? = null,
    val needsReview: Boolean = false,
    /** Identifies the notification, so a re-posted notification isn't recorded twice. */
    val dedupeKey: String? = null,
    val createdAt: Long,
)

/** The category the user last chose for a merchant. Purchases there get it automatically. */
@Entity(
    tableName = "merchant_rules",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("categoryId")],
)
data class MerchantRule(
    /** The merchant's name lowercased with everything but letters and digits removed. */
    @PrimaryKey val merchantKey: String,
    /** The name as it was last shown, for display. */
    val merchant: String,
    val categoryId: Long,
    val updatedAt: Long,
)

/**
 * A captured purchase the user said wasn't one. Remembered so the same notification, if the app
 * posts it again, doesn't come back.
 */
@Entity(tableName = "dismissed_captures")
data class DismissedCapture(
    @PrimaryKey val dedupeKey: String,
    val dismissedAt: Long,
)

/** An app Tally has captured a purchase from. Turning one off stops Tally reading its notifications. */
@Entity(tableName = "source_apps")
data class SourceApp(
    @PrimaryKey val packageName: String,
    val label: String,
    val enabled: Boolean,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val capturedCount: Int,
)
