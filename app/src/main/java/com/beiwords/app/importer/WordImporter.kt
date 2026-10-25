package com.beiwords.app.importer

/**
 * 生词导入器 —— 支持常见格式，逐行解析：
 *
 *   单词<Tab>释义
 *   单词,释义
 *   单词    释义        （两个以上空格）
 *   单词:释义 / 单词：释义
 *   单词=释义
 *   单词|释义|例句      （可选第三列英文例句）
 *   单词               （仅单词，无释义也可导入）
 *
 *   以 # 开头的行忽略；自动去首尾空白与引号；忽略重复行。
 */
object WordImporter {

    data class Entry(
        val spelling: String,
        val meaning: String,
        val exampleEn: String = ""
    )

    data class Result(
        val entries: List<Entry>,
        val skipped: Int,
        val duplicates: Int
    )

    fun parse(text: String): Result {
        val entries = mutableListOf<Entry>()
        val seen = HashSet<String>()
        var skipped = 0
        var dup = 0

        text.split('\n', '\r').forEach { rawLine ->
            val line = rawLine.trim().trim('\"', '\'')
            if (line.isEmpty() || line.startsWith("#")) return@forEach

            val parts = split(line)
            if (parts.isEmpty()) {
                skipped++
                return@forEach
            }
            val spelling = parts[0].trim()
            if (spelling.isEmpty() || !spelling.first().isLetter()) {
                skipped++
                return@forEach
            }
            val meaning = parts.getOrNull(1)?.trim() ?: ""
            val example = parts.getOrNull(2)?.trim() ?: ""

            val key = spelling.lowercase()
            if (key in seen) {
                dup++
                return@forEach
            }
            seen.add(key)
            entries.add(Entry(spelling, meaning, example))
        }
        return Result(entries, skipped, dup)
    }

    /** 按优先级切分：Tab > | > 逗号(中英文) > = > 冒号(中英文) > >=2空格 > 单空格(仅当只有2段且首段为纯英文) */
    private fun split(line: String): List<String> = when {
        line.contains('\t') -> line.split('\t')
        line.contains('|') -> line.split('|')
        line.contains(',') -> line.split(',')
        line.contains('，') -> line.split('，')
        line.contains('=') -> line.split('=')
        line.contains(':') -> line.split(':')
        line.contains('：') -> line.split('：')
        line.contains("  ") -> line.split(Regex(" {2,}"))
        else -> {
            // "abandon 放弃" —— 首个空格处切，前提是第一段是纯英文字母
            val idx = line.indexOf(' ')
            if (idx > 0 && line.substring(0, idx).matches(Regex("[A-Za-z'-]+"))) {
                listOf(line.substring(0, idx), line.substring(idx + 1))
            } else if (idx < 0) {
                listOf(line) // 仅单词
            } else {
                listOf(line)
            }
        }
    }.map { it.trim() }
}
