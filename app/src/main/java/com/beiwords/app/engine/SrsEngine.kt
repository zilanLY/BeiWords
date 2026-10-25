package com.beiwords.app.engine

import com.beiwords.app.data.Progress
import com.beiwords.app.data.Word

/**
 * 复刻「不背单词」的记忆调度引擎。
 *
 * 逆向依据（v5.11.7 官方 APK）：
 * - coreengine/LeitnerCardInMemoryImp  -> 莱特纳记忆盒（分级 + 到期复习）
 * - NewLearnActivity.doKnownProcess / doUnKnownProcess / lookAnswer -> 「认识/不认识/看答案」三级处理
 * - NewLearnActivity.getWordMaxLevel / setDotLevel / ProgressLevelView -> 词进度小圆点（等级可视化）
 * - 官方文案「连续答对 N 次完成本词学习」「答对次数已清零，还需连续答对 N 次」-> 连续答对闯关制
 * - 官方文案「本词最后一关：请在无提示的情况下判断」-> 最后一关为无提示拼写
 * - 官方文案「通过多轮复习达到掌握标准后，系统自动判断单词为复习完成」-> 满级毕业
 * - 官方设置「新学单词首次复习时间 / 学习节奏 / 复习节奏 / x 词/组」-> 组学习与节奏参数
 */
object SrsEngine {

    const val MAX_LEVEL = 7          // 莱特纳盒最高等级，达到即毕业
    const val REQUIRED_CONSECUTIVE = 3 // 原版默认：连续答对 3 次完成本词学习
    const val GROUP_SIZE = 4         // 原版默认：4 词/组

    /** 莱特纳间隔阶梯（毫秒）。L0 当日重现，之后指数扩张：0.5h/12h/1d/2d/4d/7d/15d */
    private val LADDER = longArrayOf(
        20L * 60 * 1000,          // L0 -> 20 分钟后（组内快速重现）
        12L * 60 * 60 * 1000,     // L1 -> 12 小时（首次复习时间）
        1L * 24 * 60 * 60 * 1000, // L2 -> 1 天
        2L * 24 * 60 * 60 * 1000, // L3 -> 2 天
        4L * 24 * 60 * 60 * 1000, // L4 -> 4 天
        7L * 24 * 60 * 60 * 1000, // L5 -> 7 天
        15L * 24 * 60 * 60 * 1000 // L6 -> 15 天
    )

    fun intervalOf(level: Int): Long =
        if (level <= 0) LADDER[0] else LADDER[minOf(level, LADDER.size - 1)]

    fun nextDue(level: Int, now: Long = System.currentTimeMillis()): Long =
        now + intervalOf(level)

    // ---------- 卡片类型 ----------

    enum class CardType {
        /** 新词首现：先回想，再自评 认识/模糊/忘记了 */
        INTRO,

        /** 词 -> 义 四选一 */
        CHOOSE_MEANING,

        /** 例句/释义 -> 词 四选一 */
        CHOOSE_WORD,

        /** 拼写（最后一关，无提示） */
        SPELLING,

        /** 复习回忆自评卡 */
        REVIEW_RECALL
    }

    data class Card(
        val word: Word,
        val type: CardType,
        val options: List<String>,   // 选择题选项（拼写题为空）
        val answerIndex: Int,        // 正确选项下标（拼写题为 -1）
        val isLastGate: Boolean      // 是否为最后一关（无提示拼写）
    )

    /** 学习会话中的一个队列项 */
    data class QueueItem(
        val word: Word,
        val type: CardType,
        val isNew: Boolean
    )

    /**
     * 构建一次学习会话队列（复刻原版「今日学习&复习」）：
     * 1. 先安排到期复习（复习优先）
     * 2. 再按「学习节奏」配额安排新词，按 GROUP_SIZE 词/组切组
     * 3. 新词组内：首现卡 -> 词义选择 -> 例句选词 -> 拼写（最后一关）
     * 4. 组学完后本组词快速重刷（拼写）——对应原版组末回顾
     */
    fun buildSession(
        dueReviews: List<Word>,
        newWords: List<Word>,
        continuing: List<Word>,
        reviewLimit: Int = Int.MAX_VALUE
    ): MutableList<QueueItem> {
        val q = mutableListOf<QueueItem>()

        // 复习：回忆自评卡
        dueReviews.take(reviewLimit).forEach {
            q.add(QueueItem(it, CardType.REVIEW_RECALL, isNew = false))
        }

        // 跨日续学（学习中未完成）—— 补拼写/选择
        continuing.forEach {
            q.add(QueueItem(it, CardType.CHOOSE_MEANING, isNew = true))
            q.add(QueueItem(it, CardType.CHOOSE_WORD, isNew = true))
            q.add(QueueItem(it, CardType.SPELLING, isNew = true))
        }

        // 新词按组安排
        newWords.chunked(GROUP_SIZE).forEach { group ->
            group.forEach {
                q.add(QueueItem(it, CardType.INTRO, isNew = true))
            }
            group.forEach {
                q.add(QueueItem(it, CardType.CHOOSE_MEANING, isNew = true))
            }
            group.forEach {
                q.add(QueueItem(it, CardType.CHOOSE_WORD, isNew = true))
            }
            group.forEach {
                q.add(QueueItem(it, CardType.SPELLING, isNew = true))
            }
        }
        return q
    }

    // ---------- 答题处理 ----------

    /** 自评三档（原版文案：瞬间想起选「认识」/ 思考后想起选「模糊」/ 想不起来「忘记了」） */
    enum class Recall { KNOW, BLURRY, FORGOT }

    /**
     * 新词首现自评结果 -> 进度变化
     * 认识   : 连对 +2（加速通关），等级从 L0 开始
     * 模糊   : 连对 +1
     * 忘记了 : 连对清零，等级 L0
     */
    fun applyIntroRating(p: Progress, rating: Recall, today: String): Progress {
        val (stage, level, consec) = when (rating) {
            Recall.KNOW -> Triple(0, 1, 2)
            Recall.BLURRY -> Triple(0, 0, 1)
            Recall.FORGOT -> Triple(0, 0, 0)
        }
        return p.copy(
            stage = stage,
            level = level,
            consecutive = consec,
            learnedDate = if (p.learnedDate.isEmpty()) today else p.learnedDate
        )
    }

    /**
     * 学习中答对：连对+1；连对达标 -> 本词学习完成，进入复习队列（stage=1, L0, 当日到期）
     */
    fun applyLearnCorrect(p: Progress, today: String): Progress {
        val consec = p.consecutive + 1
        return if (consec >= REQUIRED_CONSECUTIVE) {
            p.copy(
                stage = 1,
                level = 0,
                consecutive = 0,
                dueAt = nextDue(0),
                learnedDate = if (p.learnedDate.isEmpty()) today else p.learnedDate,
                correctCnt = p.correctCnt + 1
            )
        } else {
            p.copy(consecutive = consec, correctCnt = p.correctCnt + 1)
        }
    }

    /** 学习中答错：连对清零（原版「答对次数已清零，还需连续答对 N 次」），当日重现 */
    fun applyLearnWrong(p: Progress): Progress =
        p.copy(consecutive = 0, wrongCnt = p.wrongCnt + 1, dueAt = 0)

    /**
     * 复习自评结果 -> 莱特纳盒迁移
     * 认识   : level+1；满级 -> 毕业（stage=2，不再安排）
     * 模糊   : level 不变，稍后重现（半小时内）
     * 忘记了 : 回炉 level=0，当日重现
     */
    fun applyReviewRating(p: Progress, rating: Recall): Progress {
        val now = System.currentTimeMillis()
        return when (rating) {
            Recall.KNOW -> {
                val lv = p.level + 1
                if (lv >= MAX_LEVEL) {
                    p.copy(stage = 2, level = MAX_LEVEL, consecutive = 0, dueAt = Long.MAX_VALUE, correctCnt = p.correctCnt + 1)
                } else {
                    p.copy(stage = 1, level = lv, dueAt = now + intervalOf(lv), correctCnt = p.correctCnt + 1)
                }
            }
            Recall.BLURRY ->
                p.copy(stage = 1, dueAt = now + 30L * 60 * 1000)
            Recall.FORGOT ->
                p.copy(stage = 1, level = 0, consecutive = 0, dueAt = now + intervalOf(0), wrongCnt = p.wrongCnt + 1)
        }
    }

    /** 复习拼写/选择答对 -> 等级+1（同认识） */
    fun applyReviewCorrect(p: Progress): Progress = applyReviewRating(p, Recall.KNOW)

    /** 复习答错 -> 回炉 */
    fun applyReviewWrong(p: Progress): Progress = applyReviewRating(p, Recall.FORGOT)

    /** 进度小圆点展示（0..MAX_LEVEL） */
    fun dotsOf(p: Progress): Int = if (p.stage == 2) MAX_LEVEL else p.level
}
