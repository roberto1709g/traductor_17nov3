package com.orvex.traductor.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale
import java.util.UUID

data class SpanishVoiceOption(
    val id: String,
    val label: String,
    val voice: Voice,
    val isOffline: Boolean
)

class TtsBluetoothManager(
    context: Context,
    private val onTtsStatusChanged: (String) -> Unit,
    private val onSpeakingStateChanged: (Boolean) -> Unit,
    private val onBluetoothStatusChanged: (String) -> Unit,
    private val onVoicesLoaded: (List<SpanishVoiceOption>) -> Unit
) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var tts: TextToSpeech? = null
    private var isReady = false
    private var speechRate = 1.25f
    private var isMuted = false

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            updateAudioRouteStatus()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            updateAudioRouteStatus()
        }
    }

    init {
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
        updateAudioRouteStatus()

        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                configureTts()
            } else {
                isReady = false
                onTtsStatusChanged("Error al inicializar motor TTS ($status)")
            }
        }
    }

    private fun configureTts() {
        val engine = tts ?: return

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        engine.setAudioAttributes(audioAttributes)

        val spanishLocales = listOf(
            Locale("es", "ES"),
            Locale("es", "MX"),
            Locale("es", "US"),
            Locale("es")
        )

        var langResult = TextToSpeech.LANG_NOT_SUPPORTED
        for (loc in spanishLocales) {
            val res = engine.setLanguage(loc)
            if (res == TextToSpeech.LANG_AVAILABLE ||
                res == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
                res == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
            ) {
                langResult = res
                break
            }
        }

        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            isReady = false
            onTtsStatusChanged("Faltan datos de voz en español en el sistema.")
            return
        }

        engine.setSpeechRate(speechRate)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                onSpeakingStateChanged(true)
            }

            override fun onDone(utteranceId: String?) {
                onSpeakingStateChanged(false)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                onSpeakingStateChanged(false)
            }
        })

        val availableSpanishVoices = engine.voices
            ?.filter { it.locale.language.equals("es", ignoreCase = true) }
            ?.sortedBy { it.isNetworkConnectionRequired }
            ?.mapIndexed { index, voice ->
                val mode = if (voice.isNetworkConnectionRequired) "Online" else "Offline"
                val region = voice.locale.country.ifBlank { "ES" }
                SpanishVoiceOption(
                    id = voice.name,
                    label = "Voz ${index + 1} ($region - $mode)",
                    voice = voice,
                    isOffline = !voice.isNetworkConnectionRequired
                )
            } ?: emptyList()

        availableSpanishVoices.firstOrNull { it.isOffline }?.let { offlineVoice ->
            engine.voice = offlineVoice.voice
        }

        onVoicesLoaded(availableSpanishVoices)
        isReady = true
        val offlineCount = availableSpanishVoices.count { it.isOffline }
        onTtsStatusChanged("TTS Español listo ($offlineCount voces offline)")
    }

    fun updateAudioRouteStatus() {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val btDevice = outputs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_HEARING_AID
        }
        if (btDevice != null) {
            val name = btDevice.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Auriculares / Gafas BT"
            onBluetoothStatusChanged("Salida: Bluetooth ($name)")
        } else {
            val wired = outputs.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
            }
            if (wired != null) {
                onBluetoothStatusChanged("Salida: Auriculares con cable")
            } else {
                onBluetoothStatusChanged("Salida: Parlante del celular (conecta BT)")
            }
        }
    }

    fun speak(text: String) {
        if (!isReady || isMuted || text.isBlank()) return
        val utteranceId = UUID.randomUUID().toString()
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
    }

    fun stopSpeaking() {
        tts?.stop()
        onSpeakingStateChanged(false)
    }

    fun setMuted(muted: Boolean) {
        isMuted = muted
        if (muted) {
            stopSpeaking()
        }
    }

    fun setSpeechRate(rate: Float) {
        speechRate = rate
        tts?.setSpeechRate(rate)
    }

    fun selectVoice(option: SpanishVoiceOption) {
        tts?.voice = option.voice
        onTtsStatusChanged("Activa: ${option.label}")
    }

    fun release() {
        try {
            audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        } catch (_: Exception) {}
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
