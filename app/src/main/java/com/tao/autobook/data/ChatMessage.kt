package com.tao.autobook.data

import androidx.room.*

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,        // "user" or "assistant"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val operation: String? = null,  // JSON操作指令，null=纯文本
    val imageUri: String? = null,   // 图片URI（本地路径或content URI）
    val fileName: String? = null    // 附件文件名
)
