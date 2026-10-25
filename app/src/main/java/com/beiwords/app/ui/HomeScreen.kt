package com.beiwords.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beiwords.app.BeiWordsDbHolder
import com.beiwords.app.data.Book
import com.beiwords.app.data.Prefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    onStudy: () -> Unit,
    onBooks: () -> Unit,
    onImport: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { BeiWordsDbHolder.get(context) }
    val prefs = remember { Prefs.get(context) }

    var books by remember { mutableStateOf<List<Book>>(emptyList()) }
    var activeBook by remember { mutableStateOf<Book?>(null) }
    var dueCount by remember { mutableStateOf(0) }
    var newRemaining by remember { mutableStateOf(0) }
    var learningCount by remember { mutableStateOf(0) }
    var quota by remember { mutableStateOf(prefs.dailyQuota.toFloat()) }
    var bookMenu by remember { mutableStateOf(false) }

    fun refresh() {
        books = db.listBooks()
        val id = prefs.activeBookId.let { if (it == -1L) books.firstOrNull()?.id ?: -1L else it }
        activeBook = books.firstOrNull { it.id == id } ?: books.firstOrNull()
        if (activeBook != null) prefs.activeBookId = activeBook!!.id
        dueCount = db.dueWords(id, Int.MAX_VALUE).size
        newRemaining = (prefs.dailyQuota - db.todayNewCount()).coerceAtLeast(0)
        learningCount = db.learningWords(id).size
    }

    LaunchedEffect(Unit) { refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("背单词Lite", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(
            "复刻不背单词学习逻辑 · 莱特纳记忆盒 + 连对闯关",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )

        Spacer(Modifier.height(16.dp))

        // 词书选择卡
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.MenuBook, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        activeBook?.name ?: "正在初始化…",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(Color.White, CircleShape)
                                .clickable { bookMenu = true }
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("换本词书", fontSize = 12.sp)
                            Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(16.dp))
                        }
                        DropdownMenu(expanded = bookMenu, onDismissRequest = { bookMenu = false }) {
                            books.forEach { b ->
                                DropdownMenuItem(
                                    text = { Text(b.name) },
                                    onClick = {
                                        prefs.activeBookId = b.id
                                        bookMenu = false
                                        refresh()
                                    }
                                )
                            }
                        }
                    }
                }
                if (activeBook != null) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = {
                            val t = activeBook!!.total.toFloat()
                            if (t > 0) activeBook!!.learned / t else 0f
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "已学 ${activeBook!!.learned}/${activeBook!!.total} · 已掌握 ${activeBook!!.mastered}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // 今日任务
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("今日学习&复习", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                TaskRow("待复习", dueCount, Color(0xFFFF9F43))
                TaskRow("待学新词", newRemaining, Color(0xFF3D7EFF))
                if (learningCount > 0) TaskRow("闯关中", learningCount, Color(0xFF2ECC71))
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        prefs.lastStudyDate =
                            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                        onStudy()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.width(4.dp))
                    Text("开始学习")
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // 学习节奏
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("学习节奏", fontWeight = FontWeight.SemiBold)
                Text(
                    "每日新词 ${quota.toInt()} 个",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Slider(
                    value = quota,
                    onValueChange = {
                        quota = it
                        prefs.dailyQuota = it.toInt()
                    },
                    valueRange = 5f..50f,
                    steps = 8
                )
                Text(
                    "复习安排：莱特纳记忆盒自动到期，20分钟 → 12小时 → 1天 → 2天 → 4天 → 7天 → 15天 → 毕业",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onBooks, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.MenuBook, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("词书管理")
            }
            OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.UploadFile, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("导入生词")
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "实现基于对不背单词 v5.11.7 的静态逆向分析，仅用于个人学习研究",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        )
    }
}

@Composable
private fun TaskRow(label: String, count: Int, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 3.dp)
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text("$count", fontWeight = FontWeight.Bold, color = color)
    }
}
