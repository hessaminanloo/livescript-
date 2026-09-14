package ai.z.livescript.agents

/** The same 11 agent types the old app hardcoded, kept identical so the backend
 *  contract doesn't change. */
enum class AgentType(val key: String, val label: String) {
    SUMMARY("summary", "خلاصه"),
    DISCUSSION("discussion", "بحث"),
    ACTIONS("actions", "اقدامات"),
    QUESTIONS("questions", "سوالات"),
    TRANSLATION("translation", "ترجمه"),
    SENTIMENT("sentiment", "احساسات"),
    RISKS("risks", "ریسک"),
    PRIORITIES("priorities", "اولویت"),
    IDEAS("ideas", "ایده"),
    TIMELINE("timeline", "زمان‌بندی"),
    DEVILS_ADVOCATE("devils_advocate", "شیطان")
}

data class AgentResult(
    val type: AgentType,
    val conclusion: String,
    val rounds: List<String> = emptyList(),
    val ranOffline: Boolean
)

sealed class AgentRunResult {
    data class Success(val results: List<AgentResult>, val rawJson: String, val ranOffline: Boolean) : AgentRunResult()
    data class Failure(val message: String) : AgentRunResult()
}

interface AgentRunner {
    suspend fun run(transcript: String, topic: String, agents: List<AgentType>): AgentRunResult
}
