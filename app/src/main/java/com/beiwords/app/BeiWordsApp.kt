package com.beiwords.app

import android.app.Application
import android.content.Context
import com.beiwords.app.data.Db

/** 全局单例 Db 持有者 */
object BeiWordsDbHolder {
    @Volatile
    private var instance: Db? = null

    fun get(context: Context): Db =
        instance ?: synchronized(this) {
            instance ?: Db(context.applicationContext).also { instance = it }
        }
}

class BeiWordsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val db = BeiWordsDbHolder.get(this)
        db.ensureSampleBook(this)
    }
}
