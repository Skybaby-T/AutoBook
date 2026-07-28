package com.tao.autobook.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tao.autobook.data.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val UserBubble = Color(0xFF4C8DCE)
private val AiBubble = Color(0xFFF0F2F5)
private val AiText = Color(0xFF121820)
private val AccentPurple = Color(0xFF667eea)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    isSending: Boolean,
    onSend: (String, String?, String?) -> Unit,
    onExecuteOp: (String) -> Unit,
    onClearHistory: () -> Unit,
    onBack: () -> Unit,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit,
    pendingImageUri: String?,
    pendingFileName: String?,
    onClearPendingImage: () -> Unit,
    onClearPendingFile: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 自动滚动到底部
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 助手", fontWeight = FontWeight.SemiBold, color = Color(0xFF121820)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge, color = Color(0xFF4C8DCE))
                    }
                },
                actions = {
                    TextButton(onClick = onClearHistory) { Text("清空", color = Color(0xFFD86B64)) }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    // 待发送附件预览
                    if (pendingImageUri != null || pendingFileName != null) {
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (pendingImageUri != null) {
                                // 图片缩略图预览
                                val ctx = LocalContext.current
                                val bitmap = remember(pendingImageUri) {
                                    try {
                                        val uri = Uri.parse(pendingImageUri)
                                        ctx.contentResolver.openInputStream(uri)?.use { stream ->
                                            BitmapFactory.decodeStream(stream)
                                        }
                                    } catch (_: Exception) { null }
                                }
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "待发送图片",
                                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFE8EAF0)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("📷", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Text("图片已选择", color = Color(0xFF7D8792), style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = onClearPendingImage) {
                                    Text("移除", color = Color(0xFFD86B64), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            if (pendingFileName != null) {
                                Box(
                                    Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFE8EAF0)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("📄", style = MaterialTheme.typography.bodySmall)
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(pendingFileName, color = Color(0xFF7D8792), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = onClearPendingFile) {
                                    Text("移除", color = Color(0xFFD86B64), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    // 输入行
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // 图片按钮
                        IconButton(
                            onClick = onPickImage,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Text("📷", style = MaterialTheme.typography.titleMedium)
                        }
                        // 文件按钮
                        IconButton(
                            onClick = onPickFile,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Text("📎", style = MaterialTheme.typography.titleMedium)
                        }
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            placeholder = { Text("问我账单相关的问题...") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp)
                        )
                        IconButton(
                            onClick = {
                                if ((input.isNotBlank() || pendingImageUri != null) && !isSending) {
                                    onSend(input, pendingImageUri, pendingFileName)
                                    input = ""
                                }
                            },
                            enabled = (input.isNotBlank() || pendingImageUri != null) && !isSending,
                            modifier = Modifier.size(48.dp).background(UserBubble, CircleShape)
                        ) {
                            Icon(Icons.Default.Send, "发送", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    WelcomeHint()
                }
            }
            items(messages, key = { it.id }) { msg ->
                ChatBubble(msg, onExecuteOp)
            }
            if (isSending) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(32.dp).background(AiBubble, CircleShape), contentAlignment = Alignment.Center) {
                            Text("🤖", style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("思考中...", color = Color(0xFF7D8792), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeHint() {
    Card(colors = CardDefaults.cardColors(containerColor = AiBubble), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🤖 你好！我是你的记账助手。", fontWeight = FontWeight.Bold, color = AiText)
            Text("可以问我：", color = AiText, style = MaterialTheme.typography.bodySmall)
            Text("• 这个月餐饮花了多少？", color = Color(0xFF7D8792), style = MaterialTheme.typography.bodySmall)
            Text("• 帮我分析消费习惯", color = Color(0xFF7D8792), style = MaterialTheme.typography.bodySmall)
            Text("• 把拼多多的账单分类改成购物", color = Color(0xFF7D8792), style = MaterialTheme.typography.bodySmall)
            Text("• 删除所有导入的测试数据", color = Color(0xFF7D8792), style = MaterialTheme.typography.bodySmall)
            Text("• 📷 发送账单截图，自动识别记账", color = Color(0xFF7D8792), style = MaterialTheme.typography.bodySmall)
            Text("• 📎 附件CSV账单文件", color = Color(0xFF7D8792), style = MaterialTheme.typography.bodySmall)
            val ctx = LocalContext.current
            val ver = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (_: Exception) { "" }
            Text("v${ver}", color = Color(0xFFBBC3CC), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ChatBubble(msg: ChatMessage, onExecuteOp: (String) -> Unit) {
    val isUser = msg.role == "user"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(Modifier.size(32.dp).background(AiBubble, CircleShape), contentAlignment = Alignment.Center) {
                Text("🤖", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(8.dp))
        }
        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = if (isUser) UserBubble else AiBubble),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    // 图片显示
                    if (msg.imageUri != null) {
                        val ctx = LocalContext.current
                        val bitmap = remember(msg.imageUri) {
                            try {
                                val uri = Uri.parse(msg.imageUri)
                                ctx.contentResolver.openInputStream(uri)?.use { stream ->
                                    BitmapFactory.decodeStream(stream)
                                }
                            } catch (_: Exception) { null }
                        }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "发送的图片",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.FillWidth
                            )
                            if (msg.content.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                    // 文件名显示
                    if (msg.fileName != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(
                                    if (isUser) Color.White.copy(alpha = 0.15f) else Color(0xFFE0E3E8),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("📄", style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                msg.fileName,
                                color = if (isUser) Color.White.copy(alpha = 0.9f) else AiText,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        }
                        if (msg.content.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                    // 文本内容
                    if (msg.content.isNotBlank()) {
                        Text(
                            msg.content,
                            color = if (isUser) Color.White else AiText,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            // 操作按钮
            if (msg.operation != null) {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { onExecuteOp(msg.operation) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("执行操作", color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        if (isUser) {
            Spacer(Modifier.width(8.dp))
        }
    }
}
