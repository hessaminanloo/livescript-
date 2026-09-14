package ai.z.livescript.agents

import ai.z.livescript.util.Prefs
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Same contract as the old app: POST { transcript, topic, agents } to
 * {serverUrl}/api/agents/run-all and parse the JSON response. The 300s timeout from
 * the original app is kept, but it's now on a background dispatcher so it can no
 * longer freeze the UI thread.
 */
class RemoteAgentRunner(private val context: Context? = null) : AgentRunner {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun run(transcript: String, topic: String, agents: List<AgentType>): AgentRunResult =
        withContext(Dispatchers.IO) {
            val serverUrl = context?.let { Prefs.getServerUrl(it) }?.trimEnd('/')
                ?: return@withContext AgentRunResult.Failure("آدرس سرور تنظیم نشده")

            val payload = JSONObject().apply {
                put("transcript", transcript)
                put("topic", topic)
                put("agents", JSONArray(agents.map { it.key }))
            }

            val request = Request.Builder()
                .url("$serverUrl/api/agents/run-all")
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext AgentRunResult.Failure("خطای سرور (${response.code}): ${body.take(200)}")
                    }
                    AgentRunResult.Success(parseResults(body), body, ranOffline = false)
                }
            } catch (e: Exception) {
                AgentRunResult.Failure("اتصال به سرور برقرار نشد: ${e.message}")
            }
        }

    private fun parseResults(body: String): List<AgentResult> {
        val root = JSONObject(body)
        val arr = root.optJSONArray("results") ?: JSONArray()
        val out = mutableListOf<AgentResult>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val key = obj.optString("agent")
            val type = AgentType.entries.find { it.key == key } ?: continue
            val rounds = mutableListOf<String>()
            obj.optJSONArray("rounds")?.let { r -> for (j in 0 until r.length()) rounds.add(r.getString(j)) }
            out.add(AgentResult(type, obj.optString("conclusion"), rounds, ranOffline = false))
        }
        return out
    }
}
