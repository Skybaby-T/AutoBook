package com.tao.autobook.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tao.autobook.data.ChatMessage
import kotlinx.coroutines.launch

private val UserBubble = Color(0xFF4C8DCE)
private val AiBubble = Color(0xFFF0F2F5)
private val AiText = Color(0xFF121820)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    isSending: Boolean,
    onSend: (String) -> Unit,
    onExecuteOp: (String) -> Unit,
    onClearHistory: () -> Unit,
    onBack: () -> Unit
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
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("问我账单相关的问题...") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp)
                    )
                    IconButton(
                        onClick = { if (input.isNotBlank() && !isSending) { onSend(input); input = "" } },
                        enabled = input.isNotBlank() && !isSending,
                        modifier = Modifier.size(48.dp).background(UserBubble, CircleShape)
                    ) {
                        Icon(Icons.Default.Send, "发送", tint = Color.White, modifier = Modifier.size(20.dp))
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
                Text("v1.0.3-build0248", color = Color(0xFFBBC3CC), style = MaterialTheme.typography.labelSmall)
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
                Text(
                    msg.content,
                    color = if (isUser) Color.White else AiText,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
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
