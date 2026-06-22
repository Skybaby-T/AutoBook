package com.tao.autobook.data

import androidx.room.*

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,        // "user" or "assistant"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val operation: String? = null  // JSON操作指令，null=纯文本
)
