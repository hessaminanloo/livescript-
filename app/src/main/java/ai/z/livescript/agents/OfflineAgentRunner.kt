package ai.z.livescript.agents

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Runs the same 11 agents entirely on-device, no network required — this is the
 * "مدل آفلاین" the user asked for. Used automatically when there's no connectivity,
 * or when the user explicitly toggles "حالت آفلاین" in settings.
 *
 * Model setup (see README "Offline models" section):
 *   A small instruction-tuned model in MediaPipe's .task format, e.g. Gemma 2 2B-it
 *   (int4 quantized, ~1.3GB) or Gemma 3 1B-it (smaller/faster, ~0.5GB), downloaded
 *   from Google AI Edge / Kaggle Models and pushed to the device (too large to ship
 *   inside the APK itself). Quality/latency trade-off is real — 1-2B models will
 *   produce noticeably shorter, less nuanced output than the cloud backend.
 */
class OfflineAgentRunner(private val context: Context) : AgentRunner {

    private var llm: LlmInference? = null

    val isReady: Boolean get() = llm != null

    fun warmUpInBackground(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) { loadModelBlocking() }
    }

    private fun loadModelBlocking() {
        if (llm != null) return
        val modelFile = File(context.getExternalFilesDir(null), "models/gemma-offline.task")
        if (!modelFile.exists()) {
            Log.w("OfflineAgentRunner", "No offline LLM found at ${modelFile.absolutePath} — " +
                "download one per README before offline mode will work.")
            return
        }
        try {
            val options = LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(1024)
                .setTopK(40)
                .setTemperature(0.7f)
                .setRandomSeed(0)
                .build()
            llm = LlmInference.createFromOptions(context, options)
        } catch (e: Exception) {
            Log.e("OfflineAgentRunner", "Failed to load on-device model", e)
        }
    }

    override suspend fun run(transcript: String, topic: String, agents: List<AgentType>): AgentRunResult =
        withContext(Dispatchers.Default) {
            val engine = llm ?: run {
                loadModelBlocking()
                llm
            } ?: return@withContext AgentRunResult.Failure(
                "مدل آفلاین روی این دستگاه نصب نیست. طبق README یک مدل .task دانلود و در پوشه‌ی models قرار بده."
            )

            val results = mutableListOf<AgentResult>()
            for (type in agents) {
                val prompt = AgentPrompts.buildPrompt(type, transcript, topic)
                val text = try {
                    engine.generateResponse(prompt)
                } catch (e: Exception) {
                    Log.e("OfflineAgentRunner", "Inference failed for ${type.key}", e)
                    "(خطا در پردازش آفلاین این بخش)"
                }
                results.add(AgentResult(type, text.trim(), ranOffline = true))
            }
            AgentRunResult.Success(results, rawJson = "", ranOffline = true)
        }

    fun close() {
        llm?.close()
        llm = null
    }
}
