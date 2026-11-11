package com.example.tally.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.example.tally.data.model.Category
import com.example.tally.data.model.CategorySpending
import com.example.tally.data.model.DismissedCapture
import com.example.tally.data.model.Kind
import com.example.tally.data.model.MerchantRule
import com.example.tally.data.model.MonthTotals
import com.example.tally.data.model.RuleRow
import com.example.tally.data.model.SourceApp
import com.example.tally.data.model.Txn
import com.example.tally.data.model.TxnRow
import kotlinx.coroutines.flow.Flow

private const val ROW_SELECT = """
    SELECT t.id, t.amountCents, t.kind, t.merchant, t.note, t.occurredAt, t.source, t.sourceApp,
        a.label AS sourceAppLabel, t.sourceText, t.needsReview, t.categoryId,
        c.name AS categoryName, c.iconKey AS categoryIcon, c.color AS categoryColor
    FROM transactions t
    LEFT JOIN categories c ON c.id = t.categoryId
    LEFT JOIN source_apps a ON a.packageName = t.sourceApp
"""

@Dao
interface TxnDao {
    /** Transactions with [start] <= occurredAt < [end], newest first. */
    @Query("$ROW_SELECT WHERE t.occurredAt >= :start AND t.occurredAt < :end ORDER BY t.occurredAt DESC, t.id DESC")
    fun observeRows(start: Long, end: Long): Flow<List<TxnRow>>

    @Query("$ROW_SELECT WHERE t.needsReview = 1 ORDER BY t.occurredAt DESC, t.id DESC")
    fun observeReviewRows(): Flow<List<TxnRow>>

    @Query("SELECT COUNT(*) FROM transactions WHERE needsReview = 1")
    fun observeReviewCount(): Flow<Int>

    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN kind = 'EXPENSE' THEN amountCents ELSE 0 END), 0) AS spentCents,
            COALESCE(SUM(CASE WHEN kind = 'INCOME' THEN amountCents ELSE 0 END), 0) AS incomeCents
        FROM transactions WHERE occurredAt >= :start AND occurredAt < :end
        """
    )
    fun observeTotals(start: Long, end: Long): Flow<MonthTotals>

    @Query(
        """
        SELECT COALESCE(SUM(amountCents), 0) FROM transactions
        WHERE categoryId IS NULL AND kind = 'EXPENSE' AND occurredAt >= :start AND occurredAt < :end
        """
    )
    fun observeUncategorizedSpent(start: Long, end: Long): Flow<Long>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: Long): Txn?

    /** Returns -1 when a transaction with the same dedupe key already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(txn: Txn): Long

    @Update
    suspend fun update(txn: Txn)

    @Delete
    suspend fun delete(txn: Txn)

    @Query("UPDATE transactions SET needsReview = 0 WHERE id = :id")
    suspend fun markReviewed(id: Long)

    @Query("UPDATE transactions SET needsReview = 0 WHERE needsReview = 1")
    suspend fun markAllReviewed()

    @Query("UPDATE transactions SET categoryId = :categoryId WHERE id = :id")
    suspend fun setCategory(id: Long, categoryId: Long?)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY kind, sortOrder, name")
    fun observeAll(): Flow<List<Category>>

    /** Every category with its total for [start] <= occurredAt < [end], including categories with none. */
    @Query(
        """
        SELECT c.id, c.name, c.kind, c.iconKey, c.color, c.monthlyBudgetCents, c.sortOrder,
            COALESCE(SUM(t.amountCents), 0) AS totalCents, COUNT(t.id) AS count
        FROM categories c
        LEFT JOIN transactions t ON t.categoryId = c.id AND t.kind = c.kind
            AND t.occurredAt >= :start AND t.occurredAt < :end
        GROUP BY c.id
        ORDER BY c.kind, c.sortOrder, c.name
        """
    )
    fun observeSpending(start: Long, end: Long): Flow<List<CategorySpending>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun get(id: Long): Category?

    @Query("SELECT * FROM categories WHERE kind = :kind AND name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(kind: Kind, name: String): Category?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM categories WHERE kind = :kind")
    suspend fun nextSortOrder(kind: Kind): Int

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Insert
    suspend fun insert(category: Category): Long

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)
}

@Dao
interface AutomationDao {
    @Query("SELECT * FROM source_apps ORDER BY lastSeenAt DESC")
    fun observeApps(): Flow<List<SourceApp>>

    @Query("SELECT * FROM source_apps WHERE packageName = :packageName")
    suspend fun getApp(packageName: String): SourceApp?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertApp(app: SourceApp)

    @Query(
        """
        UPDATE source_apps SET label = :label, lastSeenAt = :at, capturedCount = capturedCount + 1
        WHERE packageName = :packageName
        """
    )
    suspend fun noteCapture(packageName: String, label: String, at: Long)

    @Query("UPDATE source_apps SET enabled = :enabled WHERE packageName = :packageName")
    suspend fun setAppEnabled(packageName: String, enabled: Boolean)

    @Query(
        """
        SELECT r.merchantKey, r.merchant, r.categoryId, c.name AS categoryName, c.iconKey, c.color
        FROM merchant_rules r JOIN categories c ON c.id = r.categoryId
        ORDER BY r.merchant COLLATE NOCASE
        """
    )
    fun observeRules(): Flow<List<RuleRow>>

    @Query("SELECT * FROM merchant_rules WHERE merchantKey = :merchantKey")
    suspend fun getRule(merchantKey: String): MerchantRule?

    @Upsert
    suspend fun upsertRule(rule: MerchantRule)

    @Query("DELETE FROM merchant_rules WHERE merchantKey = :merchantKey")
    suspend fun deleteRule(merchantKey: String)

    @Query("SELECT EXISTS(SELECT 1 FROM dismissed_captures WHERE dedupeKey = :dedupeKey)")
    suspend fun isDismissed(dedupeKey: String): Boolean

    @Upsert
    suspend fun dismiss(capture: DismissedCapture)

    @Query("DELETE FROM dismissed_captures WHERE dedupeKey = :dedupeKey")
    suspend fun undismiss(dedupeKey: String)
}
