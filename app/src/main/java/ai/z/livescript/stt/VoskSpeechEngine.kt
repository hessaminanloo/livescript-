package ai.z.livescript.stt

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import java.io.File

class VoskSpeechEngine(private val context: Context) {

    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private val sampleRate = 16000f

    val isReady: Boolean get() = model != null

    fun loadModelBlocking() {
        if (model != null) return
        val modelDir = File(context.filesDir, "vosk-model-fa")
        if (!modelDir.exists()) {
            StorageService.unpack(
                context, "model-fa", "vosk-model-fa",
                { unpackedModel -> model = unpackedModel },
                { e -> Log.e("VoskSpeechEngine", "Failed to unpack Vosk model", e) }
            )
        } else {
            model = Model(modelDir.absolutePath)
        }
        model?.let { recognizer = Recognizer(it, sampleRate) }
    }

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
