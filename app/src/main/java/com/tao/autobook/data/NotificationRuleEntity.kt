package com.tao.autobook.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class NotificationMatchType(val label: String) {
    CONTAINS("包含"),
    EXACT("精确匹配")
}

@Entity(
    tableName = "notification_rules",
    indices = [Index(value = ["keyword", "paymentApp"], unique = true)]
)
data class NotificationRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val keyword: String,
    val categoryId: String,
    val paymentApp: PaymentApp,
    val matchType: NotificationMatchType = NotificationMatchType.CONTAINS,
    val enabled: Boolean = true,
    val createdByUser: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

