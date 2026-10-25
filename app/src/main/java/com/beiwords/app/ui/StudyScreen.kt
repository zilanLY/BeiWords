package com.beiwords.app.ui

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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.beiwords.app.BeiWordsDbHolder
import com.beiwords.app.data.Prefs
import com.beiwords.app.engine.SrsEngine
import com.beiwords.app.tts.WordTts

@Composable
fun StudyScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val db = remember { BeiWordsDbHolder.get(context) }
    val prefs = remember { Prefs.get(context) }
    val vm: StudyViewModel = viewModel()
    val tts = remember { WordTts(context) }

    LaunchedEffect(Unit) {
        val bookId = prefs.activeBookId
        if (bookId != -1L) vm.startSession(bookId, prefs.dailyQuota)
    }

    // 单词首现/复习卡自动发音
    LaunchedEffect(vm.currentWord?.id, vm.currentType, vm.index) {
        val w = vm.currentWord
        if (w != null && !vm.answered &&
            (vm.currentType == SrsEngine.CardType.INTRO || vm.currentType == SrsEngine.CardType.REVIEW_RECALL)
        ) {
            tts.speak(w.spelling)
        }
    }

    DisposableEffect(Unit) {
        onDispose { tts.shutdown() }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        // 顶栏
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onExit) {
                Icon(Icons.Filled.Close, "退出")
            }
            LinearProgressIndicator(
                progress = {
                    if (vm.queue.isEmpty()) 0f
                    else vm.index.toFloat() / vm.queue.size
                },
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            Text("${vm.index}/${vm.queue.size}", fontSize = 12.sp)
        }

        when {
            vm.finished -> SessionDone(vm, onExit)
            vm.currentWord == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> CardArea(vm, tts)
        }
    }
}

@Composable
private fun Dots(level: Int) {
    Row {
        repeat(SrsEngine.MAX_LEVEL) { i ->
            val active = i < level
            Spacer(Modifier.width(3.dp))
            Box(
                Modifier
                    .size(9.dp)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape
                    )
            )
        }
    }
}

@Composable
private fun CardArea(vm: StudyViewModel, tts: WordTts) {
    val w = vm.currentWord ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(18.dp))

        // 词进度小圆点（对应原版 ProgressLevelView）
        Dots(vm.progressDots)
        Text(
            when {
                vm.currentType == SrsEngine.CardType.REVIEW_RECALL -> "复习 · 回忆"
                vm.currentType == SrsEngine.CardType.INTRO -> "新词"
                vm.currentType == SrsEngine.CardType.CHOOSE_MEANING -> "词义选择"
                vm.currentType == SrsEngine.CardType.CHOOSE_WORD -> "例句选词"
                else -> if (vm.isLastGate) "最后一关 · 拼写" else "拼写"
            },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
        )

        when (vm.currentType) {
            SrsEngine.CardType.INTRO -> IntroCard(vm, tts)
            SrsEngine.CardType.REVIEW_RECALL -> ReviewRecallCard(vm, tts)
            SrsEngine.CardType.CHOOSE_MEANING -> ChooseCard(
                vm,
                title = "选择正确释义",
                prompt = w.spelling,
                sub = w.phonetic,
                promptIsWord = true,
                tts = tts
            )
            SrsEngine.CardType.CHOOSE_WORD -> ChooseCard(
                vm,
                title = "例句中选择单词",
                prompt = if (w.exampleEn.contains(w.spelling, true))
                    w.exampleEn.replaceFirst(
                        Regex(w.spelling, RegexOption.IGNORE_CASE), "____"
                    ) else "____",
                sub = w.meaning,
                promptIsWord = false,
                tts = null
            )
            SrsEngine.CardType.SPELLING -> SpellingCard(vm)
        }
    }
}

@Composable
private fun IntroCard(vm: StudyViewModel, tts: WordTts) {
    val w = vm.currentWord ?: return
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(w.spelling, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { tts.speak(w.spelling) }) {
                    Icon(Icons.Filled.VolumeUp, "发音", tint = MaterialTheme.colorScheme.primary)
                }
            }
            if (w.phonetic.isNotEmpty()) Text(w.phonetic, fontSize = 14.sp, color = Color.Gray)

            Spacer(Modifier.height(14.dp))

            // 先回想，再自评（原版：先回想词义再选择，想不起来「看答案」）
            if (!vm.introRatingDone) {
                Text("先回想词义，再作自评", fontSize = 13.sp, color = Color.Gray)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RatingBtn("认识") { vm.introRating(SrsEngine.Recall.KNOW) }
                    RatingBtn("模糊") { vm.introRating(SrsEngine.Recall.BLURRY) }
                    RatingBtn("忘记了") { vm.introRating(SrsEngine.Recall.FORGOT) }
                }
            } else {
                // 自评后展示词义详情（原版：选择是否认识后即显示释义）
                Text(w.meaning, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                if (w.exampleEn.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.7f))) {
                        Column(Modifier.padding(10.dp)) {
                            Text(w.exampleEn, fontSize = 13.sp)
                            if (w.exampleZh.isNotEmpty())
                                Text(w.exampleZh, fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "连续答对 ${SrsEngine.REQUIRED_CONSECUTIVE} 次完成本词学习",
                    fontSize = 11.sp, color = Color.Gray
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = vm::next, modifier = Modifier.fillMaxWidth()) {
                    Text("继续")
                }
            }
        }
    }
}

@Composable
private fun RatingBtn(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) { Text(text) }
}

@Composable
private fun ReviewRecallCard(vm: StudyViewModel, tts: WordTts) {
    val w = vm.currentWord ?: return
    Card(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("还记得这个词吗？", fontSize = 13.sp, color = Color.Gray)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(w.spelling, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { tts.speak(w.spelling) }) {
                    Icon(Icons.Filled.VolumeUp, "发音", tint = MaterialTheme.colorScheme.primary)
                }
            }
            if (w.phonetic.isNotEmpty()) Text(w.phonetic, fontSize = 13.sp, color = Color.Gray)

            Spacer(Modifier.height(16.dp))
            if (!vm.answered) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RatingBtn("认识") { vm.reviewRating(SrsEngine.Recall.KNOW) }
                    RatingBtn("模糊") { vm.reviewRating(SrsEngine.Recall.BLURRY) }
                    RatingBtn("忘记了") { vm.reviewRating(SrsEngine.Recall.FORGOT) }
                }
            } else {
                Text(w.meaning, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                if (w.exampleEn.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(w.exampleEn, fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = vm::next, modifier = Modifier.fillMaxWidth()) { Text("继续") }
            }
        }
    }
}

@Composable
private fun ChooseCard(
    vm: StudyViewModel,
    title: String,
    prompt: String,
    sub: String,
    promptIsWord: Boolean,
    tts: WordTts?
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(6.dp))
        Text(prompt, fontSize = if (promptIsWord) 32.sp else 16.sp, fontWeight = FontWeight.Bold)
        if (sub.isNotEmpty()) Text(sub, fontSize = 13.sp, color = Color.Gray)
        if (promptIsWord && tts != null) {
            IconButton(onClick = {
                vm.currentWord?.let { tts.speak(it.spelling) }
            }) {
                Icon(Icons.Filled.VolumeUp, "发音", tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(16.dp))
        vm.options.forEachIndexed { i, opt ->
            val selectedWrong = vm.answered && !vm.wasCorrect
            val bg = when {
                !vm.answered -> MaterialTheme.colorScheme.surface
                i == vm.answerIndex -> Color(0xFFE3F6E8)
                else -> MaterialTheme.colorScheme.surface
            }
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = bg),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable(enabled = !vm.answered) { vm.chooseOption(i) }
            ) {
                Text(
                    opt,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(14.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        if (vm.answered) {
            Spacer(Modifier.height(10.dp))
            Text(
                if (vm.wasCorrect) "✓ 答对了" else "✗ 答错了，稍后重现",
                color = if (vm.wasCorrect) Color(0xFF2E9E5B) else Color(0xFFE05252),
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = vm::next, modifier = Modifier.fillMaxWidth()) { Text("继续") }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SpellingCard(vm: StudyViewModel) {
    val w = vm.currentWord ?: return
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (vm.isLastGate) "最后一关：请在无提示的情况下拼写" else "根据释义拼写单词",
                    fontSize = 12.sp, color = Color.Gray
                )
                Spacer(Modifier.height(10.dp))
                Text(w.meaning.ifEmpty { w.spelling }, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                if (!vm.isLastGate && w.phonetic.isNotEmpty())
                    Text(w.phonetic, fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = vm.spellingInput,
                    onValueChange = { vm.spellingInput = it },
                    label = { Text("输入单词") },
                    singleLine = true,
                    enabled = !vm.answered,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                if (!vm.answered) {
                    Button(onClick = vm::submitSpelling, modifier = Modifier.fillMaxWidth()) {
                        Text("确定")
                    }
                } else {
                    Text(
                        if (vm.wasCorrect) "✓ 拼写正确" else "✗ 正确答案：${w.spelling}",
                        color = if (vm.wasCorrect) Color(0xFF2E9E5B) else Color(0xFFE05252),
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = vm::next, modifier = Modifier.fillMaxWidth()) { Text("继续") }
                }
            }
        }
    }
}

@Composable
private fun SessionDone(vm: StudyViewModel, onExit: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🎉", fontSize = 48.sp)
        Spacer(Modifier.height(12.dp))
        Text("本次学习完成", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text("答题 ${vm.totalAnswered} 次 · 答对 ${vm.totalCorrect} · 答错 ${vm.totalWrong}")
        Spacer(Modifier.height(24.dp))
        Button(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
            Text("返回首页")
        }
    }
}
