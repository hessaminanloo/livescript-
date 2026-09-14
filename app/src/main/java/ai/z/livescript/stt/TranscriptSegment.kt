package ai.z.livescript.stt

data class TranscriptSegment(
    val id: Long,
    val text: String,
    val source: TranscriptSource
)
