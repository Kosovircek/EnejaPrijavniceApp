package si.eneja.prijavnice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import si.eneja.prijavnice.core.TextNorm
import java.util.Locale

enum class NavCmd { NEXT, BACK, REPEAT }

/** Govorjena navodila (Android TTS, deluje brez interneta, če je nameščen slovenski glas). */
class Speaker(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var pending: String? = null

    /** null = še ni znano */
    var slovenianAvailable by mutableStateOf<Boolean?>(null)
        private set

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            slovenianAvailable = false
            return
        }
        val r = tts.setLanguage(Locale.forLanguageTag("sl-SI"))
        slovenianAvailable = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
        ready = true
        pending?.let { speak(it) }
        pending = null
    }

    fun speak(text: String) {
        if (!ready) {
            pending = text
            return
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "navodilo")
    }

    fun stop() {
        if (ready) tts.stop()
    }

    fun shutdown() {
        tts.shutdown()
    }
}

/**
 * Glasovni ukazi "naprej" / "nazaj" / "ponovi". Najprej poskusi prepoznavo na napravi (brez interneta),
 * če ta za slovenščino ni na voljo, uporabi običajno prepoznavo govora.
 */
class VoiceCommands(context: Context, private val onCmd: (NavCmd) -> Unit) {
    private val ctx = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var active = false
    private var useOnDevice = true
    private var firedThisRound = false

    var status by mutableStateOf("")
        private set

    fun start() {
        if (active) return
        active = true
        listen()
    }

    fun stop() {
        active = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        status = ""
    }

    private fun create(): SpeechRecognizer? {
        val onDevice = useOnDevice && Build.VERSION.SDK_INT >= 31 &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx)
        if (onDevice) return SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            status = "Prepoznava govora na tem telefonu ni na voljo (uporabi gumbe ali tipki za glasnost)"
            return null
        }
        return SpeechRecognizer.createSpeechRecognizer(ctx)
    }

    private fun listen() {
        if (!active) return
        if (recognizer == null) {
            val r = create() ?: return
            r.setRecognitionListener(listener)
            recognizer = r
        }
        firedThisRound = false
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "sl-SI")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        }
        try {
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            status = "Napaka mikrofona: ${e.message}"
            restart(1500)
        }
    }

    private fun restart(delayMs: Long) {
        if (!active) return
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ listen() }, delayMs)
    }

    private fun handle(results: Bundle?) {
        if (firedThisRound) return
        val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
        val cmd = parse(texts) ?: return
        firedThisRound = true
        onCmd(cmd)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            status = "Poslušam … reci »naprej«, »nazaj« ali »ponovi«"
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    status = "Ni dovoljenja za mikrofon"
                    active = false
                }
                12, 13 -> { // jezik ni podprt / ni na voljo na napravi
                    if (useOnDevice) {
                        useOnDevice = false
                        recognizer?.destroy()
                        recognizer = null
                        restart(100)
                    } else {
                        status = "Slovenska prepoznava govora ni na voljo"
                        restart(3000)
                    }
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> restart(1000)
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> {
                    status = "Prepoznava govora potrebuje internet – uporabi gumbe ali tipki za glasnost"
                    restart(3000)
                }
                else -> restart(250)
            }
        }

        override fun onResults(results: Bundle?) {
            handle(results)
            restart(150)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            handle(partialResults)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    companion object {
        fun parse(texts: List<String>): NavCmd? {
            val all = texts.joinToString(" ") { TextNorm.norm(it) }
            val words = all.split(Regex("[^a-z]+")).filter { it.isNotEmpty() }
            if (words.any { it == "nazaj" || it == "back" || it.startsWith("prejsnj") }) return NavCmd.BACK
            if (words.any { it.startsWith("ponov") || it == "repeat" }) return NavCmd.REPEAT
            if (words.any {
                    it == "naprej" || it == "dalje" || it.startsWith("naslednj") || it == "next" ||
                        it == "ok" || it == "okej" || it == "naprej" || it == "gremo"
                }
            ) return NavCmd.NEXT
            return null
        }
    }
}
