package com.beiwords.app.data

import android.content.Context
import android.content.SharedPreferences

/** 轻量配置：当前词书 / 每日新词配额(学习节奏) / 连续打卡 */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("beiwords_prefs", Context.MODE_PRIVATE)

    var activeBookId: Long
        get() = sp.getLong("active_book", -1L)
        set(v) = sp.edit().putLong("active_book", v).apply()

    var dailyQuota: Int
        get() = sp.getInt("daily_quota", 10)
        set(v) = sp.edit().putInt("daily_quota", v).apply()

    var streakDays: Int
        get() = sp.getInt("streak", 0)
        set(v) = sp.edit().putInt("streak", v).apply()

    var lastStudyDate: String
        get() = sp.getString("last_study_date", "") ?: ""
        set(v) = sp.edit().putString("last_study_date", v).apply()

    companion object {
        @Volatile
        private var inst: Prefs? = null

        fun get(context: Context): Prefs =
            inst ?: synchronized(this) {
                inst ?: Prefs(context.applicationContext).also { inst = it }
            }
    }
}
