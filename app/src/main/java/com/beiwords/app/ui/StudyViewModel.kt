package com.beiwords.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.beiwords.app.data.BeiWordsDbHolder
import com.beiwords.app.data.Progress
import com.beiwords.app.data.Word
import com.beiwords.app.engine.SrsEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/**
 * 学习会话 ViewModel —— 驱动「今日学习&复习」的完整卡片流。
 */
class StudyViewModel(app: Application) : AndroidViewModel(app) {

    private val db = BeiWordsDbHolder.get(app)

    // ---------- 对外状态 ----------
    var queue by mutableStateOf<MutableList<SrsEngine.QueueItem>>(mutableListOf())
        private set
    var index by mutableStateOf(0)
        private set
    var finished by mutableStateOf(false)
        private set

    // 当前卡片
    var currentWord by mutableStateOf<Word?>(null)
        private set
    var currentType by mutableStateOf(SrsEngine.CardType.INTRO)
        private set
    var options by mutableStateOf<List<String>>(emptyList())
        private set
    var answerIndex by mutableStateOf(-1)
        private set
    var isLastGate by mutableStateOf(false)
        private set

    // 答题反馈
    var answered by mutableStateOf(false)
        private set
    var wasCorrect by mutableStateOf(false)
        private set
    var introRatingDone by mutableStateOf(false)
        private set
    var spellingInput by mutableStateOf("")
    var progressDots by mutableStateOf(0)

    // 会话统计
    var totalAnswered by mutableStateOf(0)
        private set
    var totalCorrect by mutableStateOf(0)
        private set
    var totalWrong by mutableStateOf(0)
        private set

    private var dailyNewQuota = 10

    fun startSession(bookId: Long, newQuota: Int) {
        dailyNewQuota = newQuota
        val due = db.dueWords(bookId, Int.MAX_VALUE)
        val remainingQuota = (dailyNewQuota - db.todayNewCount()).coerceAtLeast(0)
        val fresh = db.freshWords(bookId, remainingQuota)
        val continuing = db.learningWords(bookId)
        queue = SrsEngine.buildSession(due, fresh, continuing)
        index = 0
        finished = queue.isEmpty()
        if (!finished) showCurrent()
    }

    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun showCurrent() {
        if (index >= queue.size) {
            finished = true
            return
        }
        val item = queue[index]
        currentWord = item.word
        currentType = item.type
        answered = false
        wasCorrect = false
        introRatingDone = false
        spellingInput = ""
        progressDots = SrsEngine.dotsOf(db.getProgress(item.word.id))

        when (item.type) {
            SrsEngine.CardType.INTRO -> {
                options = emptyList(); answerIndex = -1; isLastGate = false
            }
            SrsEngine.CardType.CHOOSE_MEANING -> {
                val w = item.word
                val distract = db.randomMeanings(w.bookId, w.id, 3).distinct()
                    .filter { it != w.meaning }.take(3)
                val opts = (distract + w.meaning).shuffled(Random(System.nanoTime()))
                options = opts
                answerIndex = opts.indexOf(w.meaning)
                isLastGate = false
            }
            SrsEngine.CardType.CHOOSE_WORD -> {
                val w = item.word
                val distract = db.randomSpellings(w.bookId, w.id, 3).distinct()
                    .filter { it != w.spelling }.take(3)
                val opts = (distract + w.spelling).shuffled(Random(System.nanoTime()))
                options = opts
                answerIndex = opts.indexOf(w.spelling)
                isLastGate = false
            }
            SrsEngine.CardType.SPELLING -> {
                options = emptyList(); answerIndex = -1; isLastGate = true
            }
            SrsEngine.CardType.REVIEW_RECALL -> {
                options = emptyList(); answerIndex = -1; isLastGate = false
            }
        }
    }

    /** 新词首现：自评 认识/模糊/忘记了 */
    fun introRating(rating: SrsEngine.Recall) {
        val w = currentWord ?: return
        if (introRatingDone) return
        val p = db.getProgress(w.id)
        val np = SrsEngine.applyIntroRating(p, rating, today())
        db.upsertProgress(np)
        introRatingDone = true
        progressDots = SrsEngine.dotsOf(np)
    }

    /** 选择题作答 */
    fun chooseOption(i: Int) {
        if (answered) return
        val w = currentWord ?: return
        val item = queue.getOrNull(index) ?: return
        val correct = i == answerIndex
        answered = true
        wasCorrect = correct
        totalAnswered++
        if (correct) totalCorrect++ else totalWrong++

        val p = db.getProgress(w.id)
        val np = if (item.isNew) {
            if (correct) SrsEngine.applyLearnCorrect(p, today())
            else SrsEngine.applyLearnWrong(p)
        } else {
            if (correct) SrsEngine.applyReviewCorrect(p)
            else SrsEngine.applyReviewWrong(p)
        }
        db.upsertProgress(np)
        progressDots = SrsEngine.dotsOf(np)

        // 错词重现：同一张卡片追加到队列尾部，直到答对为止
        if (!correct) {
            queue.add(SrsEngine.QueueItem(w, item.type, item.isNew))
        }
    }

    /** 拼写作答 */
    fun submitSpelling() {
        if (answered) return
        val w = currentWord ?: return
        val item = queue.getOrNull(index) ?: return
        val correct = spellingInput.trim().equals(w.spelling, ignoreCase = true)
        answered = true
        wasCorrect = correct
        totalAnswered++
        if (correct) totalCorrect++ else totalWrong++

        val p = db.getProgress(w.id)
        val np = if (item.isNew) {
            if (correct) SrsEngine.applyLearnCorrect(p, today())
            else SrsEngine.applyLearnWrong(p)
        } else {
            if (correct) SrsEngine.applyReviewCorrect(p)
            else SrsEngine.applyReviewWrong(p)
        }
        db.upsertProgress(np)
        progressDots = SrsEngine.dotsOf(np)

        if (!correct) {
            queue.add(SrsEngine.QueueItem(w, SrsEngine.CardType.SPELLING, item.isNew))
        }
    }

    /** 复习回忆自评 */
    fun reviewRating(rating: SrsEngine.Recall) {
        val w = currentWord ?: return
        if (answered) return
        val p = db.getProgress(w.id)
        val np = SrsEngine.applyReviewRating(p, rating)
        db.upsertProgress(np)
        progressDots = SrsEngine.dotsOf(np)
        answered = true
        wasCorrect = rating != SrsEngine.Recall.FORGOT
        totalAnswered++
        if (wasCorrect) totalCorrect++ else totalWrong++

        // 模糊/忘记 -> 追加一张拼写卡重现（对应原版「辨识后拼写，更能加强记忆哦」）
        if (rating != SrsEngine.Recall.KNOW) {
            queue.add(SrsEngine.QueueItem(w, SrsEngine.CardType.SPELLING, isNew = false))
        }
    }

    fun next() {
        index++
        showCurrent()
    }
}
