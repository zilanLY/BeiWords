package com.beiwords.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beiwords.app.BeiWordsDbHolder
import com.beiwords.app.data.Book
import com.beiwords.app.data.Prefs

@Composable
fun BooksScreen(onBack: () -> Unit, onImport: () -> Unit) {
    val context = LocalContext.current
    val db = remember { BeiWordsDbHolder.get(context) }
    val prefs = remember { Prefs.get(context) }
    var books by remember { mutableStateOf<List<Book>>(emptyList()) }
    var deleteTarget by remember { mutableStateOf<Book?>(null) }

    fun refresh() {
        books = db.listBooks()
    }

    LaunchedEffect(Unit) { refresh() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
            }
            Text("词书管理", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Button(onClick = onImport) {
                Icon(Icons.Filled.UploadFile, null, Modifier.width(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("导入生词")
            }
        }

        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(books, key = { it.id }) { b ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (b.id == prefs.activeBookId)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(b.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                "总词数 ${b.total} · 已学 ${b.learned} · 已掌握 ${b.mastered}" +
                                    if (b.isCustom) " · 自定义" else " · 内置",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        if (b.id != prefs.activeBookId) {
                            TextButton(onClick = {
                                prefs.activeBookId = b.id
                                refresh()
                            }) { Text("设为当前") }
                        } else {
                            Text("当前", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                        }
                        if (b.isCustom) {
                            IconButton(onClick = { deleteTarget = b }) {
                                Icon(Icons.Filled.Delete, "删除", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除单词本") },
            text = { Text("确定删除「${target.name}」及其全部单词和学习进度吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    val wasActive = target.id == prefs.activeBookId
                    db.deleteBook(target.id)
                    if (wasActive) {
                        db.listBooks().firstOrNull()?.let { prefs.activeBookId = it.id }
                    }
                    deleteTarget = null
                    refresh()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}
