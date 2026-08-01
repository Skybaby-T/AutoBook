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

    @Query("SELECT * FROM transactions ORDER BY paidAt DESC LIMIT :limit")
    fun observeRecentTransactions(limit: Int): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY paidAt DESC")
    suspend fun getTransactions(): List<TransactionEntity>

    @Query("SELECT * FROM transactions ORDER BY paidAt DESC LIMIT :limit")
    suspend fun getRecentTransactions(limit: Int): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun getTransactionCount(): Long

    @Query("SELECT * FROM transactions WHERE paidAt BETWEEN :start AND :end ORDER BY paidAt DESC")
    suspend fun getTransactionsBetween(start: Long, end: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE categoryId = :categoryId ORDER BY paidAt DESC")
    suspend fun getTransactionsByCategory(categoryId: String): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE type = :type ORDER BY paidAt DESC")
    suspend fun getTransactionsByType(type: TransactionType): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE merchantName LIKE '%' || :keyword || '%' ORDER BY paidAt DESC")
    suspend fun getTransactionsByMerchant(keyword: String): List<TransactionEntity>

    @Query("SELECT id FROM transactions")
    suspend fun getAllTransactionIds(): List<Long>

    @Query("DELETE FROM transactions")
    suspend fun clearAllTransactions()

    @Query("DELETE FROM screenshots")
    suspend fun clearAllScreenshots()

    @Query("UPDATE transactions SET categoryId = :categoryId, updatedAt = :updatedAt WHERE merchantName LIKE '%' || :keyword || '%'")
    suspend fun updateCategoryByMerchant(keyword: String, categoryId: String, updatedAt: Long): Int

    @Query("UPDATE transactions SET merchantName = :newName, updatedAt = :updatedAt WHERE merchantName LIKE '%' || :keyword || '%'")
    suspend fun updateMerchantNameByKeyword(keyword: String, newName: String, updatedAt: Long): Int

    @Query("UPDATE transactions SET categoryId = :categoryId, updatedAt = :updatedAt")
    suspend fun updateAllCategories(categoryId: String, updatedAt: Long): Int

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

    @Query("DELETE FROM transactions WHERE id IN (:ids)")
    suspend fun deleteTransactions(ids: List<Long>)

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

    @Query("UPDATE categories SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateCategorySortOrder(id: String, sortOrder: Int)

    /** 交换两个分类的排序值，用于「上移/下移」 */
    @Transaction
    suspend fun swapCategoryOrder(idA: String, orderA: Int, idB: String, orderB: Int) {
        updateCategorySortOrder(idA, orderB)
        updateCategorySortOrder(idB, orderA)
    }

    /** 按给定顺序重排（规整化为 10,20,30…，修掉历史上重复的 sortOrder） */
    @Transaction
    suspend fun normalizeCategoryOrder(ids: List<String>) {
        ids.forEachIndexed { index, id -> updateCategorySortOrder(id, (index + 1) * 10) }
    }

    @Query("SELECT * FROM categories WHERE parentId = :parentId ORDER BY sortOrder")
    suspend fun getSubCategories(parentId: String): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE parentId = :parentId ORDER BY sortOrder")
    fun observeSubCategories(parentId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE parentId IS NULL AND type = :type ORDER BY sortOrder")
    suspend fun getTopCategories(type: TransactionType): List<CategoryEntity>

    @Query("DELETE FROM categories WHERE parentId = :parentId")
    suspend fun deleteSubCategories(parentId: String)

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

    @Query("SELECT * FROM screenshots WHERE parsedTransactionId IN (:transactionIds)")
    suspend fun getScreenshotsByTransactionIds(transactionIds: List<Long>): List<ScreenshotCaptureEntity>

    @Query("SELECT COUNT(*) FROM screenshots WHERE parsedTransactionId = :transactionId")
    suspend fun countScreenshotsByTransactionId(transactionId: Long): Int

    @Query("DELETE FROM screenshots WHERE parsedTransactionId = :transactionId")
    suspend fun deleteScreenshotsByTransactionId(transactionId: Long)

    @Query("DELETE FROM screenshots WHERE parsedTransactionId IN (:transactionIds)")
    suspend fun deleteScreenshotsByTransactionIds(transactionIds: List<Long>)

    @Query("DELETE FROM screenshots WHERE id = :screenshotId")
    suspend fun deleteScreenshotById(screenshotId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(entry: AutoBookLogEntry): Long

    @Query("SELECT * FROM auto_book_logs ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getLogs(limit: Int = 200): List<AutoBookLogEntry>

    @Query("SELECT * FROM auto_book_logs ORDER BY createdAt DESC LIMIT :limit")
    fun observeLogs(limit: Int = 200): Flow<List<AutoBookLogEntry>>

    @Query("DELETE FROM auto_book_logs")
    suspend fun clearLogs()

    @Query("DELETE FROM auto_book_logs WHERE createdAt < :cutoff")
    suspend fun deleteLogsOlderThan(cutoff: Long)

    // CAST 成 REAL 再 SUM，避免 amountCents 异常/过大时 SQLite 整数 SUM 溢出直接抛 integer overflow
    // 所有统计口径统一排除「不计入收支」的账单（excludeFromStats = 1）
    @Query("SELECT COALESCE(CAST(SUM(CAST(amountCents AS REAL)) AS INTEGER), 0) FROM transactions WHERE paidAt BETWEEN :start AND :end AND type = :type AND excludeFromStats = 0")
    fun observeTotalBetween(start: Long, end: Long, type: TransactionType): Flow<Long>

    @Query("SELECT COALESCE(CAST(SUM(CAST(amountCents AS REAL)) AS INTEGER), 0) FROM transactions WHERE paidAt BETWEEN :start AND :end AND type = 'EXPENSE' AND excludeFromStats = 0")
    suspend fun getExpenseBetween(start: Long, end: Long): Long

    @Query("SELECT COALESCE(CAST(SUM(CAST(amountCents AS REAL)) AS INTEGER), 0) FROM transactions WHERE paidAt BETWEEN :start AND :end AND type = 'INCOME' AND excludeFromStats = 0")
    suspend fun getIncomeBetween(start: Long, end: Long): Long

    /** 清理离谱金额，防止启动统计 SUM 炸库 */
    @Query("UPDATE transactions SET amountCents = 0, updatedAt = :updatedAt WHERE amountCents < 0 OR amountCents > :maxCents")
    suspend fun sanitizeOutlierAmounts(maxCents: Long, updatedAt: Long): Int

    /** 切换「不计入收支」/「不计入预算」标记 */
    @Query("UPDATE transactions SET excludeFromStats = :excludeStats, excludeFromBudget = :excludeBudget, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateExcludeFlags(id: Long, excludeStats: Boolean, excludeBudget: Boolean, updatedAt: Long)

    // ====== 报表聚合（全部走 SQL，不受账本列表 500 条上限影响）======

    /** 区间总览：合计、笔数、最大单笔、有账单天数 */
    @Query(
        """
        SELECT COALESCE(CAST(SUM(CAST(amountCents AS REAL)) AS INTEGER), 0) AS total,
               COUNT(*) AS cnt,
               COALESCE(MAX(amountCents), 0) AS maxAmount,
               COUNT(DISTINCT strftime('%Y-%m-%d', paidAt / 1000, 'unixepoch', 'localtime')) AS activeDays
        FROM transactions
        WHERE paidAt BETWEEN :start AND :end AND type = :type AND excludeFromStats = 0
        """
    )
    suspend fun getRangeSummary(start: Long, end: Long, type: TransactionType): RangeSummaryRow

    /** 预算口径合计：额外排除「不计入预算」的账单 */
    @Query(
        """
        SELECT COALESCE(CAST(SUM(CAST(amountCents AS REAL)) AS INTEGER), 0)
        FROM transactions
        WHERE paidAt BETWEEN :start AND :end AND type = :type AND excludeFromStats = 0 AND excludeFromBudget = 0
        """
    )
    suspend fun getBudgetSpent(start: Long, end: Long, type: TransactionType): Long

    /** 预算口径按分类合计 */
    @Query(
        """
        SELECT categoryId,
               COALESCE(CAST(SUM(CAST(amountCents AS REAL)) AS INTEGER), 0) AS total,
               COUNT(*) AS cnt
        FROM transactions
        WHERE paidAt BETWEEN :start AND :end AND type = :type AND excludeFromStats = 0 AND excludeFromBudget = 0
        GROUP BY categoryId
        """
    )
    suspend fun getBudgetCategoryTotals(start: Long, end: Long, type: TransactionType): List<CategoryTotalRow>

    /** 区间内被标记「不计入收支」的合计与笔数，用于报表提示 */
    @Query(
        """
        SELECT COALESCE(CAST(SUM(CAST(amountCents AS REAL)) AS INTEGER), 0) AS total,
               COUNT(*) AS cnt,
               COALESCE(MAX(amountCents), 0) AS maxAmount,
               0 AS activeDays
        FROM transactions
        WHERE paidAt BETWEEN :start AND :end AND excludeFromStats = 1
        """
    )
    suspend fun getExcludedSummary(start: Long, end: Long): RangeSummaryRow

    /** 区间内按分类合计，倒序 */
    @Query(
        """
        SELECT categoryId,
               COALESCE(CAST(SUM(CAST(amountCents AS REAL)) AS INTEGER), 0) AS total,
               COUNT(*) AS cnt
        FROM transactions
        WHERE paidAt BETWEEN :start AND :end AND type = :type AND excludeFromStats = 0
        GROUP BY categoryId
        ORDER BY total DESC
        """
    )
    suspend fun getCategoryTotals(start: Long, end: Long, type: TransactionType): List<CategoryTotalRow>

    /** 区间内按商家合计排行 */
    @Query(
        """
        SELECT merchantName,
               COALESCE(CAST(SUM(CAST(amountCents AS REAL)) AS INTEGER), 0) AS total,
               COUNT(*) AS cnt
        FROM transactions
        WHERE paidAt BETWEEN :start AND :end AND type = :type AND excludeFromStats = 0 AND TRIM(merchantName) != ''
        GROUP BY merchantName
        ORDER BY total DESC
        LIMIT :limit
        """
    )
    suspend fun getMerchantTotals(start: Long, end: Long, type: TransactionType, limit: Int): List<MerchantTotalRow>

    /** 区间内指定分类的商家排行（分类下钻） */
    @Query(
        """
        SELECT merchantName,
               COALESCE(CAST(SUM(CAST(amountCents AS REAL)) AS INTEGER), 0) AS total,
               COUNT(*) AS cnt
        FROM transactions
        WHERE paidAt BETWEEN :start AND :end AND type = :type AND excludeFromStats = 0 AND categoryId = :categoryId AND TRIM(merchantName) != ''
        GROUP BY merchantName
        ORDER BY total DESC
        LIMIT :limit
        """
    )
    suspend fun getMerchantTotalsInCategory(start: Long, end: Long, type: TransactionType, categoryId: String, limit: Int): List<MerchantTotalRow>

    /** 区间内按支付方式合计 */
    @Query(
        """
        SELECT paymentApp,
               COALESCE(CAST(SUM(CAST(amountCents AS REAL)) AS INTEGER), 0) AS total,
               COUNT(*) AS cnt
        FROM transactions
        WHERE paidAt BETWEEN :start AND :end AND type = :type AND excludeFromStats = 0
        GROUP BY paymentApp
        ORDER BY total DESC
        """
    )
    suspend fun getPaymentAppTotals(start: Long, end: Long, type: TransactionType): List<PaymentAppTotalRow>

    /** 区间内按天合计 */
    @Query(
        """
        SELECT strftime('%Y-%m-%d', paidAt / 1000, 'unixepoch', 'localtime') AS bucket,
               COALESCE(CAST(SUM(CAST(amountCents AS REAL)) AS INTEGER), 0) AS total,
               COUNT(*) AS cnt
        FROM transactions
        WHERE paidAt BETWEEN :start AND :end AND type = :type AND excludeFromStats = 0
        GROUP BY bucket
        ORDER BY bucket ASC
        """
    )
    suspend fun getDayTotals(start: Long, end: Long, type: TransactionType): List<DayTotalRow>

    /** 区间内按月合计 */
    @Query(
        """
        SELECT strftime('%Y-%m', paidAt / 1000, 'unixepoch', 'localtime') AS bucket,
               COALESCE(CAST(SUM(CAST(amountCents AS REAL)) AS INTEGER), 0) AS total,
               COUNT(*) AS cnt
        FROM transactions
        WHERE paidAt BETWEEN :start AND :end AND type = :type AND excludeFromStats = 0
        GROUP BY bucket
        ORDER BY bucket ASC
        """
    )
    suspend fun getMonthTotals(start: Long, end: Long, type: TransactionType): List<MonthTotalRow>

    /** 区间内按星期合计（0=周日 … 6=周六） */
    @Query(
        """
        SELECT CAST(strftime('%w', paidAt / 1000, 'unixepoch', 'localtime') AS INTEGER) AS weekday,
               COALESCE(CAST(SUM(CAST(amountCents AS REAL)) AS INTEGER), 0) AS total,
               COUNT(*) AS cnt
        FROM transactions
        WHERE paidAt BETWEEN :start AND :end AND type = :type AND excludeFromStats = 0
        GROUP BY weekday
        ORDER BY weekday ASC
        """
    )
    suspend fun getWeekdayTotals(start: Long, end: Long, type: TransactionType): List<WeekdayTotalRow>

    /** 区间 + 分类的账单明细（分类下钻列表） */
    @Query(
        """
        SELECT * FROM transactions
        WHERE paidAt BETWEEN :start AND :end AND type = :type AND excludeFromStats = 0 AND categoryId = :categoryId
        ORDER BY amountCents DESC
        LIMIT :limit
        """
    )
    suspend fun getTransactionsInCategoryRange(start: Long, end: Long, type: TransactionType, categoryId: String, limit: Int): List<TransactionEntity>

    /** 最早一笔账单时间，用于「全部」区间起点 */
    @Query("SELECT MIN(paidAt) FROM transactions")
    suspend fun getEarliestPaidAt(): Long?

    // ====== 预算 ======
    @Query("SELECT * FROM budgets")
    fun observeBudgets(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets")
    suspend fun getBudgets(): List<BudgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBudget(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE categoryId = :categoryId")
    suspend fun deleteBudget(categoryId: String)

    @Query("DELETE FROM budgets")
    suspend fun clearBudgets()

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
