package com.beiwords.app.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** 单词发音（TTS，离线可用性取决于系统语音数据） */
class WordTts(context: Context) {
    private var tts: TextToSpeech? = null
    private var ready = false

    private val initListener = TextToSpeech.OnInitListener { status ->
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            ready = true
        }
    }

    init {
        tts = TextToSpeech(context.applicationContext, initListener)
    }

    fun speak(word: String) {
        if (ready) {
            tts?.speak(word, TextToSpeech.QUEUE_FLUSH, null, "w")
        }
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
    }
}
