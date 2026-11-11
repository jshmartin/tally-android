package com.example.tally.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.tally.data.dao.AutomationDao
import com.example.tally.data.dao.CategoryDao
import com.example.tally.data.dao.TxnDao
import com.example.tally.data.model.Category
import com.example.tally.data.model.DismissedCapture
import com.example.tally.data.model.MerchantRule
import com.example.tally.data.model.SourceApp
import com.example.tally.data.model.Txn

@Database(
    entities = [Category::class, Txn::class, MerchantRule::class, SourceApp::class, DismissedCapture::class],
    version = 1,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun txnDao(): TxnDao
    abstract fun categoryDao(): CategoryDao
    abstract fun automationDao(): AutomationDao

    companion object {
        /** Fills a new database with the starter categories. */
        val SEED_ON_CREATE = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                StarterCategories.ALL.forEachIndexed { index, c ->
                    db.execSQL(
                        """
                        INSERT INTO categories (name, kind, iconKey, color, monthlyBudgetCents, sortOrder)
                        VALUES (?, ?, ?, ?, NULL, ?)
                        """,
                        arrayOf<Any>(c.name, c.kind.name, c.iconKey, c.color, index),
                    )
                }
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "tally.db")
                .addCallback(SEED_ON_CREATE)
                .build()
    }
}
