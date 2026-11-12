package com.example.tally.capture

import androidx.room.withTransaction
import com.example.tally.data.AppDatabase
import com.example.tally.data.model.Category
import com.example.tally.data.model.DismissedCapture
import com.example.tally.data.model.Kind
import com.example.tally.data.model.MerchantRule
import com.example.tally.data.model.Source
import com.example.tally.data.model.SourceApp
import com.example.tally.data.model.Txn

/** A notification as the listener saw it. */
data class CapturedNotification(
    val packageName: String,
    val appLabel: String,
    /** [android.service.notification.StatusBarNotification.getKey]. */
    val key: String,
    val title: String?,
    val text: String?,
    val isMessage: Boolean,
    val postedAt: Long,
)

/** Turns notifications into transactions waiting for review, and learns categories per merchant. */
class PurchaseRecorder(
    private val db: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class Preview(val purchase: ParsedPurchase, val categoryName: String?)

    /** What [record] would make of [text], without saving anything. */
    suspend fun preview(text: String): Preview? {
        val parsed = PurchaseParser.parse(NotificationText(title = null, text = text)) ?: return null
        return Preview(parsed, categoryFor(parsed.kind, parsed.merchant, text)?.name)
    }

    /**
     * Records [notification] if it's a purchase, payment or deposit. Returns the id of the transaction
     * it became, or null when it isn't one, its app is turned off, or it was already recorded or dismissed.
     *
     * When another app already reported the same purchase, the two are merged: the notification is
     * added to that transaction instead of becoming a second one. See [SamePurchase].
     */
    suspend fun record(notification: CapturedNotification): Long? {
        val parsed = PurchaseParser.parse(
            NotificationText(notification.title, notification.text, notification.isMessage, notification.appLabel)
        ) ?: return null
        val raw = listOf(notification.title, notification.text)
            .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
            .joinToString("\n")
        val dedupeKey = "${notification.packageName}|${notification.key}|${raw.hashCode()}"
        val apps = db.automationDao()

        return db.withTransaction {
            val app = apps.getApp(notification.packageName)
            if (app?.enabled == false || apps.isDismissed(dedupeKey)) return@withTransaction null
            if (db.txnDao().hasCapture(dedupeKey)) return@withTransaction null
            val now = clock()
            val sighting = Sighting(notification.appLabel, raw, parsed.amountCents, parsed.merchant.orEmpty())
            val twin = findTwin(notification, parsed.kind, sighting)
            val id = if (twin != null) {
                db.txnDao().update(merge(twin.first, twin.second, sighting, dedupeKey))
                twin.first.id
            } else {
                db.txnDao().insert(
                    Txn(
                        amountCents = parsed.amountCents,
                        kind = parsed.kind,
                        merchant = parsed.merchant.orEmpty(),
                        categoryId = categoryFor(parsed.kind, parsed.merchant, raw)?.id,
                        occurredAt = notification.postedAt,
                        source = Source.NOTIFICATION,
                        sourceApp = notification.packageName,
                        sourceText = raw,
                        needsReview = true,
                        dedupeKey = dedupeKey,
                        createdAt = now,
                    )
                )
            }
            if (id == -1L) return@withTransaction null
            if (app == null) {
                apps.insertApp(
                    SourceApp(
                        packageName = notification.packageName,
                        label = notification.appLabel,
                        enabled = true,
                        firstSeenAt = now,
                        lastSeenAt = now,
                        capturedCount = 1,
                    )
                )
            } else {
                apps.noteCapture(notification.packageName, notification.appLabel, now)
            }
            id
        }
    }

    /**
     * The transaction another app's alert already made of this purchase, with what that alert said,
     * or null when this is the first alert about it.
     */
    private suspend fun findTwin(notification: CapturedNotification, kind: Kind, sighting: Sighting): Pair<Txn, Sighting>? {
        val at = notification.postedAt
        val window = SamePurchase.WINDOW_MS
        for (txn in db.txnDao().capturesNear(kind, notification.packageName, at - window, at + window, at)) {
            // One purchase gets one alert from each app. A second one from this app is another purchase.
            if (txn.mergedKeys.lines().any { it.startsWith("${notification.packageName}|") }) continue
            val label = txn.sourceApp?.let { db.automationDao().getApp(it)?.label }.orEmpty()
            val other = Sighting(label, txn.sourceText.orEmpty(), txn.amountCents, txn.merchant)
            if (SamePurchase.matches(sighting, other)) return txn to other
        }
        return null
    }

    /** [txn], which [first] made, with the second app's alert added to it. */
    private suspend fun merge(txn: Txn, first: Sighting, second: Sighting, dedupeKey: String): Txn {
        val text = listOfNotNull(txn.sourceText, "${second.appLabel}:\n${second.text}").joinToString("\n\n")
        val merged = txn.copy(
            sourceText = text,
            mergedKeys = (txn.mergedKeys.lines().filter { it.isNotEmpty() } + dedupeKey).joinToString("\n"),
        )
        // What the user has already checked stays as they left it.
        if (!txn.needsReview) return merged

        // A card's alert has the amount the card was charged. The app that was paid through knows the
        // merchant by name, where the card may only show "PAYPAL *CORNERBOOKS".
        val (card, app) = when {
            SamePurchase.paidThrough(card = second, app = first) -> second to first
            SamePurchase.paidThrough(card = first, app = second) -> first to second
            else -> first to first
        }
        val merchant = app.merchant.ifEmpty { first.merchant }.ifEmpty { second.merchant }
        return merged.copy(
            amountCents = card.amountCents,
            merchant = merchant,
            categoryId = txn.categoryId ?: categoryFor(txn.kind, merchant, text)?.id,
        )
    }

    /** The user's rule for [merchant] if there is one, otherwise a keyword guess. */
    suspend fun categoryFor(kind: Kind, merchant: String?, rawText: String?): Category? {
        if (!merchant.isNullOrBlank()) {
            val key = Categorizer.merchantKey(merchant)
            val rule = if (key.isEmpty()) null else db.automationDao().getRule(key)
            if (rule != null) {
                val category = db.categoryDao().get(rule.categoryId)
                if (category?.kind == kind) return category
            }
        }
        val name = Categorizer.suggest(kind, merchant, rawText) ?: return null
        return db.categoryDao().findByName(kind, name)
    }

    /** Remembers [categoryId] for [merchant], so later purchases there get it without asking. */
    suspend fun learn(merchant: String, categoryId: Long?) {
        val key = Categorizer.merchantKey(merchant)
        if (key.isEmpty() || categoryId == null) return
        db.automationDao().upsertRule(MerchantRule(key, merchant.trim(), categoryId, clock()))
    }

    /** Deletes a transaction. A captured one is remembered so its notifications aren't recorded again. */
    suspend fun discard(txn: Txn) = db.withTransaction {
        txn.captureKeys().forEach { db.automationDao().dismiss(DismissedCapture(it, clock())) }
        db.txnDao().delete(txn)
    }

    /** Undoes [discard]. */
    suspend fun restore(txn: Txn) = db.withTransaction {
        txn.captureKeys().forEach { db.automationDao().undismiss(it) }
        db.txnDao().insert(txn)
    }

    /** The dedupe keys of every notification that went into this transaction. */
    private fun Txn.captureKeys(): List<String> = listOfNotNull(dedupeKey) + mergedKeys.lines().filter { it.isNotEmpty() }
}
