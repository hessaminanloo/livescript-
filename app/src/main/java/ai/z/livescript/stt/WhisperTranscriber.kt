package ai.z.livescript.stt

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * JNI bridge to whisper.cpp (https://github.com/ggerganov/whisper.cpp), used as the
 * "final pass" that re-transcribes the raw audio with much higher accuracy than Vosk's
 * real-time guesses. This is intentionally NOT streaming — it's meant to be called on
 * a buffered chunk (or the whole recording) after the fact.
 *
 * Build setup (see README "Offline models" section):
 *   1. `git submodule add https://github.com/ggerganov/whisper.cpp app/src/main/cpp/whisper.cpp`
 *   2. Download a quantized ggml model, e.g. ggml-small.bin or a fine-tuned Persian
 *      variant, and ship it in app/src/main/assets/whisper/ (or download on first run
 *      and cache in filesDir — recommended, since these models are 150MB-1GB+).
 *   3. The CMakeLists.txt in app/src/main/cpp already links against whisper.cpp's
 *      static library and builds this JNI bridge (whisper_jni.cpp) into libwhisper_jni.so.
 */
class WhisperTranscriber(private val context: Context) {

    private var nativeContextPtr: Long = 0L

    val isReady: Boolean get() = nativeContextPtr != 0L

    companion object {
        private const val TAG = "WhisperTranscriber"
        init {
            try {
                System.loadLibrary("whisper_jni")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "libwhisper_jni.so not built yet — see README for native build steps", e)
            }
        }
    }

    /** Copies the bundled/cached ggml model to a real file path (whisper.cpp needs a
     *  filesystem path, not an assets stream) and loads it into a native context. */
    fun loadModelBlocking(modelAssetName: String = "whisper/ggml-small.bin") {
        if (nativeContextPtr != 0L) return
        val modelFile = File(context.filesDir, modelAssetName.substringAfterLast('/'))
        if (!modelFile.exists()) {
            context.assets.open(modelAssetName).use { input ->
                FileOutputStream(modelFile).use { output -> input.copyTo(output) }
            }
        }
        nativeContextPtr = nativeInit(modelFile.absolutePath)
    }

    /** pcm16Mono16k: raw 16-bit PCM, mono, 16kHz — same format the recorder captures for Vosk. */
    fun transcribe(pcm16Mono16k: ShortArray, languageHint: String = "fa"): String {
        if (nativeContextPtr == 0L) return ""
        val floatSamples = FloatArray(pcm16Mono16k.size) { i -> pcm16Mono16k[i] / 32768.0f }
        return nativeTranscribe(nativeContextPtr, floatSamples, languageHint)
    }

    fun close() {
        if (nativeContextPtr != 0L) {
            nativeFree(nativeContextPtr)
            nativeContextPtr = 0L
        }
    }

    private external fun nativeInit(modelPath: String): Long
    private external fun nativeTranscribe(contextPtr: Long, samples: FloatArray, language: String): String
    private external fun nativeFree(contextPtr: Long)
}
