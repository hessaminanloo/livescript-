package ai.z.livescript.stt

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import java.io.File

/**
 * Wraps the offline Vosk recognizer for fast, low-latency partial results while the
 * user is still talking. This is the "real-time" half of the hybrid pipeline.
 *
 * Model setup (required once, see README "Offline models" section):
 *   1. Download a Persian Vosk model, e.g. "vosk-model-small-fa-0.4" or "vosk-model-fa-0.5"
 *      from https://alphacephei.com/vosk/models
 *   2. Unzip it into app/src/main/assets/model-fa/  (Vosk's StorageService.unpack expects
 *      the model directory to ship inside assets)
 */
class VoskSpeechEngine(private val context: Context) {

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private val sampleRate = 16000f

    val isReady: Boolean get() = model != null

    /** Blocking; call from a background thread/coroutine. Unpacks the bundled model
     *  from assets into internal storage the first time it runs. */
    fun loadModelBlocking() {
        if (model != null) return
        val modelDir = File(context.filesDir, "vosk-model-fa")
        if (!modelDir.exists()) {
            StorageService.unpack(
                context, "model-fa", "vosk-model-fa",
                { path -> model = Model(path) },
                { e -> Log.e("VoskSpeechEngine", "Failed to unpack Vosk model", e) }
            )
        } else {
            model = Model(modelDir.absolutePath)
        }
        model?.let { recognizer = Recognizer(it, sampleRate) }
    }

    /** Feed one chunk of 16-bit PCM mono audio. Returns the latest partial guess,
     *  or a finalized phrase (endOfUtterance = true) when Vosk detects a pause. */
    fun acceptAudio(pcm: ShortArray, length: Int): Pair<String, Boolean> {
        val rec = recognizer ?: return "" to false
        val isFinal = rec.acceptWaveForm(pcm, length)
        val json = if (isFinal) rec.result else rec.partialResult
        val text = try {
            val obj = JSONObject(json)
            obj.optString(if (isFinal) "text" else "partial", "")
        } catch (e: Exception) {
            ""
        }
        return text to isFinal
    }

    fun reset() {
        recognizer?.reset()
    }

    fun close() {
        recognizer?.close()
        model?.close()
        recognizer = null
        model = null
    }
}
