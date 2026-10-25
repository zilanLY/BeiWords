package com.beiwords.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.beiwords.app.importer.WordImporter
import com.beiwords.app.data.Prefs

/**
 * 导入生词：从 .txt/.csv 文件 或 粘贴文本，创建自定义词书。
 * 导入后即可像内置词书一样按原版逻辑背诵（新词闯关 + 莱特纳复习）。
 */
@Composable
fun ImportScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { BeiWordsDbHolder.get(context) }
    val prefs = remember { Prefs.get(context) }

    var text by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf<String?>(null) }
    var bookName by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<WordImporter.Result?>(null) }
    var importing by remember { mutableStateOf(false) }
    var doneMsg by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()
                    ?.use { it.readText() } ?: ""
                text = content
                fileName = uri.lastPathSegment ?: "导入的生词"
                if (bookName.isEmpty()) {
                    bookName = (uri.lastPathSegment ?: "")
                        .substringAfterLast('/')
                        .removeSuffix(".txt").removeSuffix(".csv")
                        .ifEmpty { "我的生词本" }
                }
            } catch (e: Exception) {
                doneMsg = "读取文件失败：${e.message}"
            }
        }
    }

    fun doImport() {
        val r = WordImporter.parse(text)
        result = r
        if (r.entries.isEmpty()) {
            doneMsg = "没有解析到有效单词，请检查格式（每行：单词 + 分隔符 + 释义）"
            return
        }
        importing = true
        val name = bookName.trim().ifEmpty { fileName?.removeSuffix(".txt")?.removeSuffix(".csv") ?: "我的生词本" }
        val bookId = db.createBook(name, true)
        var inserted = 0
        r.entries.forEach { e ->
            if (!db.bookHasWord(bookId, e.spelling)) {
                db.insertWord(bookId, e.spelling, "", e.meaning, e.exampleEn, "")
                inserted++
            }
        }
        prefs.activeBookId = bookId
        importing = false
        doneMsg = "导入成功：共 ${r.entries.size} 词，新增 $inserted 词" +
            (if (r.duplicates > 0) "，文件内重复 ${r.duplicates} 词已合并" else "") +
            (if (r.skipped > 0) "，跳过无效行 ${r.skipped} 行" else "") +
            "\n已切换为当前词书，回首页即可开始背诵"
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
            }
            Text("导入生词", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("支持的格式（每行一个词）", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "单词<Tab>释义\n单词,释义\n单词 释义\n单词:释义 / 单词=释义\n单词|释义|英文例句（例句可选）\n\n# 开头的行会被忽略，重复单词自动去重",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Row {
            Button(onClick = {
                filePicker.launch(arrayOf("text/*", "application/csv", "text/csv", "text/comma-separated-values"))
            }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.UploadFile, null)
                Spacer(Modifier.width(4.dp))
                Text("从文件导入")
            }
            Spacer(Modifier.width(10.dp))
            OutlinedButton(
                onClick = { text = ""; fileName = null; result = null; doneMsg = null },
                modifier = Modifier.weight(1f)
            ) { Text("清空") }
        }

        if (fileName != null) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Description, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text(fileName ?: "", fontSize = 12.sp, maxLines = 1)
            }
        }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = text,
            onValueChange = { text = it; doneMsg = null },
            label = { Text("粘贴生词文本（每行：单词 分隔符 释义）") },
            modifier = Modifier.fillMaxWidth().height(220.dp)
        )

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = bookName,
            onValueChange = { bookName = it },
            label = { Text("词书名称") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(14.dp))

        Button(onClick = { doImport() }, modifier = Modifier.fillMaxWidth(), enabled = text.isNotBlank()) {
            Text("解析并导入为词书")
        }

        if (importing) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        doneMsg?.let { msg ->
            Spacer(Modifier.height(12.dp))
            Card(shape = RoundedCornerShape(12.dp)) {
                Text(msg, Modifier.padding(14.dp), fontSize = 13.sp)
            }
        }

        result?.let { r ->
            Spacer(Modifier.height(10.dp))
            Text(
                "解析预览：有效 ${r.entries.size} 词 / 跳过 ${r.skipped} 行 / 重复 ${r.duplicates}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            r.entries.take(5).forEach {
                Text("  ${it.spelling} — ${it.meaning.ifEmpty { "(无释义)" }}", fontSize = 12.sp)
            }
            if (r.entries.size > 5) Text("  …等共 ${r.entries.size} 词", fontSize = 12.sp)
        }

        Spacer(Modifier.height(20.dp))
    }
}
