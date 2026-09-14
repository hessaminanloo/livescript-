package ai.z.livescript.stt

/** Which engine actually produced a given piece of text. Shown in the UI so the
 *  user knows whether they're looking at a rough live guess or a refined pass. */
enum class TranscriptSource { LIVE_VOSK, REFINED_WHISPER, ANDROID_FALLBACK, MANUAL }

data class TranscriptUpdate(
    val committedText: String,     // finalized text so far (may later be replaced by a refined pass)
    val livePartialText: String,   // current in-progress utterance, not final yet
    val source: TranscriptSource,
    val wordCount: Int
)

sealed class SpeechEngineState {
    data object Idle : SpeechEngineState()
    data object LoadingModels : SpeechEngineState()
    data object ModelsUnavailable : SpeechEngineState()   // e.g. models not downloaded yet
    data object Ready : SpeechEngineState()
    data object Listening : SpeechEngineState()
    data object RefiningWithWhisper : SpeechEngineState()
    data class Error(val message: String) : SpeechEngineState()
}
