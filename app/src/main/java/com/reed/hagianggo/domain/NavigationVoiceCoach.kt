package com.reed.hagianggo.domain

import android.content.Context
import android.speech.tts.TextToSpeech
import com.reed.hagianggo.model.ActiveNavigationState
import java.util.Locale

/**
 * Bộ đọc chỉ dẫn rẽ tiếng Việt hoàn toàn Offline qua Android TextToSpeech.
 * Mặc định TẮT theo thiết lập người dùng; chỉ khởi tạo TTS engine khi người dùng bật loa.
 */
class NavigationVoiceCoach(context: Context) {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var isEnabled = false
    private var currentRouteStamp = 0L
    private val spokenKeys = HashSet<String>()

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        if (enabled) {
            ensureTtsInitialized()
        } else {
            stopSpeaking()
        }
    }

    fun resetForRoute(routeComputedAtMillis: Long) {
        if (currentRouteStamp != routeComputedAtMillis) {
            currentRouteStamp = routeComputedAtMillis
            spokenKeys.clear()
        }
    }

    fun onProgressUpdate(state: ActiveNavigationState) {
        isEnabled = state.isVoiceEnabled
        if (!isEnabled) return
        val route = state.route ?: return
        resetForRoute(route.computedAtMillis)

        ensureTtsInitialized()
        if (!isTtsReady) return

        if (state.hasArrived) {
            speakOnce("arrived", "Bạn đã đến ${state.destination.name}.")
            return
        }

        if (state.isGoingWrongWay) {
            speakOnce("wrongway_${state.matchedSegmentIndex / 6}", "Bạn đang đi ngược chiều, vui lòng quay đầu lại.")
            return
        }

        if (state.isRerouting || state.isOffRoute) {
            speakOnce("offroute_${state.matchedSegmentIndex / 5}", "Bạn đang lệch tuyến, đang tính lại đường.")
            return
        }

        val step = route.steps.getOrNull(state.currentStepIndex) ?: return
        val distM = state.distanceToNextManeuverMeters
        val idx = step.index

        when {
            distM in 350.0..550.0 && step.distanceMeters >= 700.0 -> {
                speakOnce("step_${idx}_500", "Còn 500 mét nữa, ${step.instructionVi}")
            }
            distM in 15.0..140.0 -> {
                speakOnce("step_${idx}_120", "Sắp tới, ${step.instructionVi}")
            }
        }
    }

    fun speakImmediateHint(text: String) {
        if (!isEnabled) return
        ensureTtsInitialized()
        if (!isTtsReady) return
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nav_hint_${System.currentTimeMillis()}")
        } catch (_: Exception) {}
    }

    fun stopSpeaking() {
        try {
            tts?.stop()
        } catch (_: Exception) {}
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
        isTtsReady = false
    }

    private fun ensureTtsInitialized() {
        if (tts != null) return
        try {
            tts = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val engine = tts ?: return@TextToSpeech
                    val viLocale = Locale.forLanguageTag("vi-VN")
                    val avail = engine.isLanguageAvailable(viLocale)
                    if (avail >= TextToSpeech.LANG_AVAILABLE) {
                        engine.language = viLocale
                    }
                    engine.setSpeechRate(1.02f)
                    isTtsReady = true
                }
            }
        } catch (_: Exception) {
            tts = null
            isTtsReady = false
        }
    }

    private fun speakOnce(key: String, text: String) {
        if (key in spokenKeys) return
        spokenKeys.add(key)
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, key)
        } catch (_: Exception) {}
    }
}
