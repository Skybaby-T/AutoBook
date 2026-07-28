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
    version = 8,
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
                createLogTable(db)
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

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categories ADD COLUMN parentId TEXT DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_categories_parentId ON categories(parentId)")
                // 插入内置二级分类
                val subs = listOf(
                    BuiltInCategories.FOOD_BREAKFAST to ("早餐" to BuiltInCategories.FOOD),
                    BuiltInCategories.FOOD_LUNCH to ("午餐" to BuiltInCategories.FOOD),
                    BuiltInCategories.FOOD_DINNER to ("晚餐" to BuiltInCategories.FOOD),
                    BuiltInCategories.FOOD_TAKEOUT to ("外卖" to BuiltInCategories.FOOD),
                    BuiltInCategories.FOOD_SNACK to ("零食" to BuiltInCategories.FOOD),
                    BuiltInCategories.FOOD_FRUIT to ("水果" to BuiltInCategories.FOOD),
                    BuiltInCategories.TRANSPORT_BUS to ("公交" to BuiltInCategories.TRANSPORT),
                    BuiltInCategories.TRANSPORT_TAXI to ("打车" to BuiltInCategories.TRANSPORT),
                    BuiltInCategories.TRANSPORT_METRO to ("地铁" to BuiltInCategories.TRANSPORT),
                    BuiltInCategories.TRANSPORT_FUEL to ("加油" to BuiltInCategories.TRANSPORT),
                    BuiltInCategories.TRANSPORT_PARKING to ("停车" to BuiltInCategories.TRANSPORT),
                    BuiltInCategories.BILLS_WATER to ("水费" to BuiltInCategories.BILLS),
                    BuiltInCategories.BILLS_ELECTRIC to ("电费" to BuiltInCategories.BILLS),
                    BuiltInCategories.BILLS_GAS to ("燃气" to BuiltInCategories.BILLS),
                    BuiltInCategories.BILLS_RENT to ("房租" to BuiltInCategories.BILLS),
                    BuiltInCategories.BILLS_INTERNET to ("网费" to BuiltInCategories.BILLS),
                    BuiltInCategories.ENTERTAINMENT_GAME to ("游戏" to BuiltInCategories.ENTERTAINMENT),
                    BuiltInCategories.ENTERTAINMENT_MOVIE to ("电影" to BuiltInCategories.ENTERTAINMENT),
                    BuiltInCategories.ENTERTAINMENT_SPORT to ("运动" to BuiltInCategories.ENTERTAINMENT),
                    BuiltInCategories.SHOPPING_CLOTHES to ("服装" to BuiltInCategories.SHOPPING),
                    BuiltInCategories.SHOPPING_DAILY to ("日用品" to BuiltInCategories.SHOPPING),
                    BuiltInCategories.SHOPPING_DIGITAL to ("数码" to BuiltInCategories.SHOPPING),
                )
                for ((id, pair) in subs) {
                    val (name, parentId) = pair
                    db.execSQL("INSERT OR IGNORE INTO categories(id, name, icon, color, sortOrder, type, isDefault, parentId) VALUES(?, ?, 'Category', 0, 0, 'EXPENSE', 1, ?)", arrayOf(id, name, parentId))
                }
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureLogTableSchema(db)
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN imageUri TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN fileName TEXT DEFAULT NULL")
            }
        }

        fun get(context: Context): AutoBookDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context, AutoBookDatabase::class.java, "autobook.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                .build()
                .also { instance = it }
        }

        private fun createLogTable(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS auto_book_logs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, source TEXT NOT NULL, action TEXT NOT NULL, detail TEXT NOT NULL, createdAt INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_auto_book_logs_createdAt ON auto_book_logs(createdAt)")
        }

        private fun ensureLogTableSchema(db: SupportSQLiteDatabase) {
            if (!tableExists(db, "auto_book_logs")) {
                createLogTable(db)
                return
            }

            val columns = tableColumns(db, "auto_book_logs")
            val expectedColumns = setOf("id", "source", "action", "detail", "createdAt")
            if (columns.containsAll(expectedColumns)) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_auto_book_logs_createdAt ON auto_book_logs(createdAt)")
                return
            }

            db.execSQL("CREATE TABLE IF NOT EXISTS auto_book_logs_repaired (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, source TEXT NOT NULL, action TEXT NOT NULL, detail TEXT NOT NULL, createdAt INTEGER NOT NULL)")
            val idExpr = if ("id" in columns) "id" else "NULL"
            val sourceExpr = if ("source" in columns) "source" else "''"
            val actionExpr = when {
                "action" in columns -> "action"
                "method" in columns -> "method"
                else -> "''"
            }
            val detailExpr = when {
                "detail" in columns -> "detail"
                "content" in columns -> "content"
                else -> "''"
            }
            val createdAtExpr = if ("createdAt" in columns) "createdAt" else "0"
            db.execSQL(
                "INSERT OR REPLACE INTO auto_book_logs_repaired(id, source, action, detail, createdAt) " +
                    "SELECT $idExpr, $sourceExpr, $actionExpr, $detailExpr, $createdAtExpr FROM auto_book_logs"
            )
            db.execSQL("DROP TABLE auto_book_logs")
            db.execSQL("ALTER TABLE auto_book_logs_repaired RENAME TO auto_book_logs")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_auto_book_logs_createdAt ON auto_book_logs(createdAt)")
        }

        private fun tableExists(db: SupportSQLiteDatabase, tableName: String): Boolean {
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(tableName)).use { cursor ->
                return cursor.moveToFirst()
            }
        }

        private fun tableColumns(db: SupportSQLiteDatabase, tableName: String): Set<String> {
            val columns = mutableSetOf<String>()
            db.query("PRAGMA table_info($tableName)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    columns += cursor.getString(nameIndex)
                }
            }
            return columns
        }
    }
}
