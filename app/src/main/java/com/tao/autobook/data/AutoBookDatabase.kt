package com.tao.autobook.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class EnumConverters {
    @TypeConverter fun paymentAppToString(value: PaymentApp): String = value.name
    @TypeConverter fun stringToPaymentApp(value: String): PaymentApp = PaymentApp.valueOf(value)
    @TypeConverter fun sourceTypeToString(value: SourceType): String = value.name
    @TypeConverter fun stringToSourceType(value: String): SourceType = SourceType.valueOf(value)
    @TypeConverter fun screenshotSourceTypeToString(value: ScreenshotSourceType): String = value.name
    @TypeConverter fun stringToScreenshotSourceType(value: String): ScreenshotSourceType = ScreenshotSourceType.valueOf(value)
    @TypeConverter fun screenshotStatusToString(value: ScreenshotStatus): String = value.name
    @TypeConverter fun stringToScreenshotStatus(value: String): ScreenshotStatus = ScreenshotStatus.valueOf(value)
    @TypeConverter fun transactionTypeToString(value: TransactionType): String = value.name
    @TypeConverter fun stringToTransactionType(value: String): TransactionType = TransactionType.valueOf(value)
    @TypeConverter fun notificationMatchTypeToString(value: NotificationMatchType): String = value.name
    @TypeConverter fun stringToNotificationMatchType(value: String): NotificationMatchType = NotificationMatchType.valueOf(value)
}

@Database(
    entities = [
        TransactionEntity::class,
        ScreenshotCaptureEntity::class,
        CategoryEntity::class,
        MerchantRuleEntity::class,
        RawCaptureEntity::class,
        AutoBookLogEntry::class,
        NotificationRuleEntity::class,
        ChatMessage::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(EnumConverters::class)
abstract class AutoBookDatabase : RoomDatabase() {
    abstract fun dao(): AutoBookDao
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile private var instance: AutoBookDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN type TEXT NOT NULL DEFAULT 'EXPENSE'")
                db.execSQL("ALTER TABLE categories ADD COLUMN type TEXT NOT NULL DEFAULT 'EXPENSE'")
                db.execSQL("ALTER TABLE categories ADD COLUMN isDefault INTEGER NOT NULL DEFAULT 1")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_type ON transactions(type)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_categories_type_sortOrder ON categories(type, sortOrder)")
                BuiltInCategories.defaults.forEach { category ->
                    db.execSQL(
                        "INSERT OR IGNORE INTO categories(id, name, icon, color, sortOrder, type, isDefault) VALUES(?, ?, ?, ?, ?, ?, ?)",
                        arrayOf(category.id, category.name, category.icon, category.color, category.sortOrder, category.type.name, if (category.isDefault) 1 else 0)
                    )
                }
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS auto_book_logs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, createdAt INTEGER NOT NULL, source TEXT NOT NULL, method TEXT NOT NULL, content TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_auto_book_logs_createdAt ON auto_book_logs(createdAt)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS notification_rules (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, keyword TEXT NOT NULL, categoryId TEXT NOT NULL, paymentApp TEXT NOT NULL, matchType TEXT NOT NULL DEFAULT 'CONTAINS', enabled INTEGER NOT NULL DEFAULT 1, createdByUser INTEGER NOT NULL DEFAULT 1, createdAt INTEGER NOT NULL DEFAULT 0)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_notification_rules_keyword_paymentApp ON notification_rules(keyword, paymentApp)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS chat_messages (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, role TEXT NOT NULL, content TEXT NOT NULL, timestamp INTEGER NOT NULL, operation TEXT)")
            }
        }

        fun get(context: Context): AutoBookDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context, AutoBookDatabase::class.java, "autobook.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
                .also { instance = it }
        }
    }
}