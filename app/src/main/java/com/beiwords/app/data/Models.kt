package com.beiwords.app.data

/** 词条 */
data class Word(
    val id: Long,
    val bookId: Long,
    val spelling: String,
    val phonetic: String,
    val meaning: String,
    val exampleEn: String,
    val exampleZh: String
)

/** 词书 */
data class Book(
    val id: Long,
    val name: String,
    val isCustom: Boolean,
    val total: Int,
    val learned: Int,
    val mastered: Int
)

/**
 * 单词学习进度 —— 复刻不背单词的 BBWordProcess 模型
 *
 * stage: -1 未学 / 0 学习中(闯关) / 1 复习中 / 2 已掌握(毕业,不再安排)
 * level: 莱特纳盒等级 0..7，对应 ProgressLevelView 的小圆点
 * consecutive: 连续答对次数（原版 "连续答对 N 次完成本词学习"）
 */
data class Progress(
    val wordId: Long,
    val stage: Int,
    val level: Int,
    val consecutive: Int,
    val dueAt: Long,
    val correctCnt: Int,
    val wrongCnt: Int,
    val learnedDate: String
)
