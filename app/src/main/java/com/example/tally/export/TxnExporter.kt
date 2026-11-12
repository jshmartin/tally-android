package com.example.tally.export

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import com.example.tally.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.ZoneId

/** Saves transactions as a spreadsheet to a file the user picked. */
class TxnExporter(
    private val db: AppDatabase,
    private val resolver: ContentResolver,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    suspend fun count(period: ExportPeriod): Int {
        val (start, end) = period.bounds(zone())
        return db.txnDao().count(start, end)
    }

    /**
     * Writes [period]'s transactions to [uri] and returns how many there were. If writing fails, the
     * half-written file is deleted where the app it's saved in allows that, and the error is rethrown.
     */
    suspend fun export(period: ExportPeriod, uri: Uri): Int {
        val zone = zone()
        val (start, end) = period.bounds(zone)
        val rows = db.txnDao().rowsOldestFirst(start, end)
        val sheet = TxnSheet.build(rows, zone)
        withContext(Dispatchers.IO) {
            try {
                // "wt" truncates, in case the user picked an existing file to replace.
                val out = resolver.openOutputStream(uri, "wt") ?: throw IOException("Couldn't open $uri")
                Xlsx.write(sheet, out)
            } catch (e: Exception) {
                runCatching { DocumentsContract.deleteDocument(resolver, uri) }
                throw e
            }
        }
        return rows.size
    }
}
