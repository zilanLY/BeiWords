package com.beiwords.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Db(context: Context) : SQLiteOpenHelper(context, "beiwords.db", null, 1) {

    companion object {
        const val T_BOOKS = "books"
        const val T_WORDS = "words"
        const val T_PROGRESS = "progress"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE $T_BOOKS(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            is_custom INTEGER NOT NULL DEFAULT 0,
            created_at INTEGER NOT NULL)"""
        )
        db.execSQL(
            """CREATE TABLE $T_WORDS(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            book_id INTEGER NOT NULL,
            spelling TEXT NOT NULL,
            phonetic TEXT DEFAULT '',
            meaning TEXT DEFAULT '',
            example_en TEXT DEFAULT '',
            example_zh TEXT DEFAULT '')"""
        )
        db.execSQL("CREATE INDEX idx_words_book ON $T_WORDS(book_id)")
        db.execSQL(
            """CREATE TABLE $T_PROGRESS(
            word_id INTEGER PRIMARY KEY,
            stage INTEGER NOT NULL DEFAULT -1,
            level INTEGER NOT NULL DEFAULT 0,
            consecutive INTEGER NOT NULL DEFAULT 0,
            due_at INTEGER NOT NULL DEFAULT 0,
            correct_cnt INTEGER NOT NULL DEFAULT 0,
            wrong_cnt INTEGER NOT NULL DEFAULT 0,
            learned_date TEXT DEFAULT '')"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v1
    }

    // ---------- 词书 ----------

    fun listBooks(): List<Book> {
        val db = readableDatabase
        val books = mutableListOf<Book>()
        val cur = db.rawQuery(
            """SELECT b.id, b.name, b.is_custom,
               (SELECT COUNT(*) FROM words w WHERE w.book_id=b.id) total,
               (SELECT COUNT(*) FROM words w JOIN progress p ON p.word_id=w.id WHERE w.book_id=b.id AND p.stage>=0) learned,
               (SELECT COUNT(*) FROM words w JOIN progress p ON p.word_id=w.id WHERE w.book_id=b.id AND p.stage=2) mastered
               FROM books b ORDER BY b.id""",
            null
        )
        while (cur.moveToNext()) {
            books.add(
                Book(
                    cur.getLong(0), cur.getString(1), cur.getInt(2) == 1,
                    cur.getInt(3), cur.getInt(4), cur.getInt(5)
                )
            )
        }
        cur.close()
        return books
    }

    fun createBook(name: String, isCustom: Boolean): Long {
        val cv = ContentValues()
        cv.put("name", name)
        cv.put("is_custom", if (isCustom) 1 else 0)
        cv.put("created_at", System.currentTimeMillis())
        return writableDatabase.insert(T_BOOKS, null, cv)
    }

    fun deleteBook(bookId: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM $T_PROGRESS WHERE word_id IN (SELECT id FROM $T_WORDS WHERE book_id=$bookId)")
            db.execSQL("DELETE FROM $T_WORDS WHERE book_id=$bookId")
            db.execSQL("DELETE FROM $T_BOOKS WHERE id=$bookId")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun insertWord(
        bookId: Long, spelling: String, phonetic: String,
        meaning: String, exampleEn: String, exampleZh: String
    ): Long {
        val cv = ContentValues()
        cv.put("book_id", bookId)
        cv.put("spelling", spelling)
        cv.put("phonetic", phonetic)
        cv.put("meaning", meaning)
        cv.put("example_en", exampleEn)
        cv.put("example_zh", exampleZh)
        return writableDatabase.insert(T_WORDS, null, cv)
    }

    fun bookHasWord(bookId: Long, spelling: String): Boolean {
        val cur = readableDatabase.rawQuery(
            "SELECT 1 FROM $T_WORDS WHERE book_id=? AND lower(spelling)=lower(?) LIMIT 1",
            arrayOf(bookId.toString(), spelling)
        )
        val r = cur.moveToFirst()
        cur.close()
        return r
    }

    // ---------- 今日队列 ----------

    private fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /** 今日已新学的词数（学习节奏配额用） */
    fun todayNewCount(): Int {
        val cur = readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $T_PROGRESS WHERE learned_date=?", arrayOf(today())
        )
        cur.moveToFirst()
        val n = cur.getInt(0)
        cur.close()
        return n
    }

    /** 到期待复习的词（stage=1 且 due_at<=now），按到期先后排序 */
    fun dueWords(bookId: Long, limit: Int): List<Word> {
        val cur = readableDatabase.rawQuery(
            """SELECT w.id,w.book_id,w.spelling,w.phonetic,w.meaning,w.example_en,w.example_zh
               FROM $T_WORDS w JOIN $T_PROGRESS p ON p.word_id=w.id
               WHERE w.book_id=$bookId AND p.stage=1 AND p.due_at<=?
               ORDER BY p.due_at ASC LIMIT $limit""",
            arrayOf(System.currentTimeMillis().toString())
        )
        return readWords(cur)
    }

    /** 未学的新词（学习节奏配额用） */
    fun freshWords(bookId: Long, limit: Int): List<Word> {
        val cur = readableDatabase.rawQuery(
            """SELECT w.id,w.book_id,w.spelling,w.phonetic,w.meaning,w.example_en,w.example_zh
               FROM $T_WORDS w LEFT JOIN $T_PROGRESS p ON p.word_id=w.id
               WHERE w.book_id=$bookId AND (p.stage IS NULL OR p.stage=-1)
               ORDER BY w.id ASC LIMIT $limit""",
            null
        )
        return readWords(cur)
    }

    /** 学习中未完成的词（跨日续学） */
    fun learningWords(bookId: Long): List<Word> {
        val cur = readableDatabase.rawQuery(
            """SELECT w.id,w.book_id,w.spelling,w.phonetic,w.meaning,w.example_en,w.example_zh
               FROM $T_WORDS w JOIN $T_PROGRESS p ON p.word_id=w.id
               WHERE w.book_id=$bookId AND p.stage=0""",
            null
        )
        return readWords(cur)
    }

    private fun readWords(cur: android.database.Cursor): List<Word> {
        val list = mutableListOf<Word>()
        while (cur.moveToNext()) {
            list.add(
                Word(
                    cur.getLong(0), cur.getLong(1), cur.getString(2),
                    cur.getString(3) ?: "", cur.getString(4) ?: "",
                    cur.getString(5) ?: "", cur.getString(6) ?: ""
                )
            )
        }
        cur.close()
        return list
    }

    fun getProgress(wordId: Long): Progress {
        val cur = readableDatabase.rawQuery(
            "SELECT word_id,stage,level,consecutive,due_at,correct_cnt,wrong_cnt,learned_date FROM $T_PROGRESS WHERE word_id=?",
            arrayOf(wordId.toString())
        )
        val p = if (cur.moveToFirst()) {
            Progress(cur.getLong(0), cur.getInt(1), cur.getInt(2), cur.getInt(3), cur.getLong(4), cur.getInt(5), cur.getInt(6), cur.getString(7) ?: "")
        } else {
            Progress(wordId, -1, 0, 0, 0L, 0, 0, "")
        }
        cur.close()
        return p
    }

    fun upsertProgress(p: Progress) {
        val cv = ContentValues()
        cv.put("word_id", p.wordId)
        cv.put("stage", p.stage)
        cv.put("level", p.level)
        cv.put("consecutive", p.consecutive)
        cv.put("due_at", p.dueAt)
        cv.put("correct_cnt", p.correctCnt)
        cv.put("wrong_cnt", p.wrongCnt)
        cv.put("learned_date", p.learnedDate)
        writableDatabase.replace(T_PROGRESS, null, cv)
    }

    // ---------- 干扰项 ----------

    /** 从同词书随机取 n 个释义（用于选择题干扰项） */
    fun randomMeanings(bookId: Long, excludeWordId: Long, n: Int): List<String> {
        val cur = readableDatabase.rawQuery(
            """SELECT meaning FROM $T_WORDS
               WHERE book_id=$bookId AND id!=$excludeWordId AND meaning!=''
               ORDER BY RANDOM() LIMIT $n""",
            null
        )
        val list = mutableListOf<String>()
        while (cur.moveToNext()) list.add(cur.getString(0))
        cur.close()
        return list
    }

    fun randomSpellings(bookId: Long, excludeWordId: Long, n: Int): List<String> {
        val cur = readableDatabase.rawQuery(
            """SELECT spelling FROM $T_WORDS
               WHERE book_id=$bookId AND id!=$excludeWordId
               ORDER BY RANDOM() LIMIT $n""",
            null
        )
        val list = mutableListOf<String>()
        while (cur.moveToNext()) list.add(cur.getString(0))
        cur.close()
        return list
    }

    // ---------- 内置词书 ----------

    fun ensureSampleBook(context: Context) {
        val cur = readableDatabase.rawQuery("SELECT COUNT(*) FROM $T_BOOKS", null)
        cur.moveToFirst()
        val n = cur.getInt(0)
        cur.close()
        if (n > 0) return
        val bookId = createBook("CET-4 核心词(内置示例)", false)
        val json = context.assets.open("words_cet4.json").bufferedReader().readText()
        val arr = JSONArray(json)
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                insertWord(
                    bookId,
                    o.optString("w"),
                    o.optString("ph"),
                    o.optString("m"),
                    o.optString("en"),
                    o.optString("zh")
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
