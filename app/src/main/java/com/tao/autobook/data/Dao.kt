package com.tao.autobook.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import com.tao.autobook.data.AutoBookLogEntry

@Dao
interface AutoBookDao {
    @Query("SELECT * FROM transactions ORDER BY paidAt DESC")
    fun observeTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY paidAt DESC")
    suspend fun getTransactions(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE paidAt BETWEEN :start AND :end ORDER BY paidAt DESC")
    fun observeTransactionsBetween(start: Long, end: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransaction(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE dedupeKey = :dedupeKey LIMIT 1")
    suspend fun findByDedupeKey(dedupeKey: String): TransactionEntity?

    @Query(
        "SELECT * FROM transactions WHERE paymentApp = :paymentApp AND amountCents = :amountCents AND type = :type " +
            "AND paidAt BETWEEN :from AND :to ORDER BY paidAt DESC LIMIT 1"
    )
    suspend fun findSimilar(paymentApp: PaymentApp, amountCents: Long, type: TransactionType, from: Long, to: Long): TransactionEntity?

    @Query(
        "SELECT * FROM transactions WHERE amountCents = :amountCents AND type = :type " +
            "AND paidAt BETWEEN :from AND :to AND sourceType IN (:sourceTypes) ORDER BY paidAt DESC LIMIT 1"
    )
    suspend fun findSimilarAutoAnyApp(amountCents: Long, type: TransactionType, from: Long, to: Long, sourceTypes: List<SourceType>): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransaction(entity: TransactionEntity): Long

    @Update
    suspend fun updateTransaction(entity: TransactionEntity)

    @Query("UPDATE transactions SET categoryId = :newCategoryId, updatedAt = :updatedAt WHERE categoryId = :oldCategoryId")
    suspend fun moveTransactionsToCategory(oldCategoryId: String, newCategoryId: String, updatedAt: Long)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransaction(id: Long)

    @Query("SELECT * FROM categories ORDER BY type, sortOrder")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY type, sortOrder")
    suspend fun getCategories(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategory(id: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategories(categories: List<CategoryEntity>)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategory(id: String)

    @Query("SELECT * FROM merchant_rules ORDER BY priority DESC")
    suspend fun getMerchantRules(): List<MerchantRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMerchantRule(rule: MerchantRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRawCapture(entity: RawCaptureEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScreenshot(entity: ScreenshotCaptureEntity): Long

    @Update
    suspend fun updateScreenshot(entity: ScreenshotCaptureEntity)

    @Query("SELECT * FROM screenshots ORDER BY capturedAt DESC")
    fun observeScreenshots(): Flow<List<ScreenshotCaptureEntity>>

    @Query("SELECT * FROM screenshots WHERE status = :status ORDER BY capturedAt DESC")
    fun observeScreenshotsByStatus(status: ScreenshotStatus): Flow<List<ScreenshotCaptureEntity>>

    @Query("SELECT * FROM screenshots WHERE id = :id")
    suspend fun getScreenshot(id: Long): ScreenshotCaptureEntity?

    @Query("SELECT * FROM screenshots WHERE status != :confirmed AND parsedTransactionId IS NULL")
    suspend fun getUnconfirmedScreenshots(confirmed: ScreenshotStatus = ScreenshotStatus.CONFIRMED): List<ScreenshotCaptureEntity>

    @Query("DELETE FROM screenshots WHERE status != :confirmed AND parsedTransactionId IS NULL")
    suspend fun deleteUnconfirmedScreenshots(confirmed: ScreenshotStatus = ScreenshotStatus.CONFIRMED)

    @Query("SELECT * FROM screenshots WHERE parsedTransactionId = :transactionId ORDER BY capturedAt ASC")
    suspend fun getScreenshotsByTransactionId(transactionId: Long): List<ScreenshotCaptureEntity>

    @Query("SELECT COUNT(*) FROM screenshots WHERE parsedTransactionId = :transactionId")
    suspend fun countScreenshotsByTransactionId(transactionId: Long): Int

    @Query("DELETE FROM screenshots WHERE parsedTransactionId = :transactionId")
    suspend fun deleteScreenshotsByTransactionId(transactionId: Long)

    @Query("DELETE FROM screenshots WHERE id = :screenshotId")
    suspend fun deleteScreenshotById(screenshotId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(entry: AutoBookLogEntry): Long

    @Query("SELECT * FROM auto_book_logs ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getLogs(limit: Int = 200): List<AutoBookLogEntry>

    @Query("SELECT * FROM auto_book_logs ORDER BY createdAt DESC")
    fun observeLogs(): Flow<List<AutoBookLogEntry>>

    @Query("DELETE FROM auto_book_logs")
    suspend fun clearLogs()

    @Query("DELETE FROM auto_book_logs WHERE createdAt < :cutoff")
    suspend fun deleteLogsOlderThan(cutoff: Long)

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM transactions WHERE paidAt BETWEEN :start AND :end AND type = :type")
    fun observeTotalBetween(start: Long, end: Long, type: TransactionType): Flow<Long>

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM transactions WHERE paidAt >= :start AND type = 'EXPENSE'")
    suspend fun getMonthExpense(start: Long): Long

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM transactions WHERE paidAt >= :start AND type = 'INCOME'")
    suspend fun getMonthIncome(start: Long): Long

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM transactions WHERE paidAt >= :start AND type = 'EXPENSE'")
    suspend fun getTodayExpense(start: Long): Long

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM transactions WHERE paidAt >= :start AND type = 'INCOME'")
    suspend fun getTodayIncome(start: Long): Long

    @androidx.room.Query("SELECT * FROM transactions ORDER BY paidAt DESC")
    fun observeTransactionsPaged(): androidx.paging.PagingSource<Int, TransactionEntity>

    @Transaction
    suspend fun seedCategoriesIfEmpty() {
        val current = getCategories()
        if (current.isEmpty()) {
            upsertCategories(BuiltInCategories.defaults)
            return
        }
        val existingIds = current.map { it.id }.toSet()
        val missing = BuiltInCategories.defaults.filter { it.id !in existingIds }
        if (missing.isNotEmpty()) upsertCategories(missing)
    }

    @Transaction
    suspend fun deleteCategoryAndMoveTransactions(categoryId: String, fallbackId: String) {
        moveTransactionsToCategory(categoryId, fallbackId, System.currentTimeMillis())
        deleteCategory(categoryId)
    }

    // --- 通知规则库 ---
    @Query("SELECT * FROM notification_rules ORDER BY createdAt DESC")
    fun observeNotificationRules(): Flow<List<NotificationRuleEntity>>

    @Query("SELECT * FROM notification_rules WHERE enabled = 1")
    suspend fun getEnabledNotificationRules(): List<NotificationRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotificationRule(rule: NotificationRuleEntity): Long

    @Update
    suspend fun updateNotificationRule(rule: NotificationRuleEntity)

    @Query("DELETE FROM notification_rules WHERE id = :id")
    suspend fun deleteNotificationRule(id: Long)
}