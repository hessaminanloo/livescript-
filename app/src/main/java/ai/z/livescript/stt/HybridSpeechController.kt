package ai.z.livescript.stt

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The core fix for "دقت تشخیص گفتار ضعیفه": instead of relying only on Android's
 * built-in SpeechRecognizer (the old app), this pipeline runs TWO offline engines:
 *
 *  - Vosk streams partial results in real time (low latency, so the user sees text
 *    appear as they talk).
 *  - Every time Vosk detects the end of an utterance, the raw audio for exactly that
 *    utterance is handed to Whisper, which is slower but noticeably more accurate.
 *    The Vosk guess for that segment is then silently replaced with Whisper's result.
 *
 * If neither offline model is available (not downloaded yet / unsupported ABI), it
 * transparently falls back to Android's SpeechRecognizer so the app still works.
 */
class HybridSpeechController(private val context: Context) {

    private val sampleRate = 16000
    private val vosk = VoskSpeechEngine(context)
    private val whisper = WhisperTranscriber(context)
    private var androidFallback: AndroidFallbackEngine? = null

    private val _engineState = MutableStateFlow<SpeechEngineState>(SpeechEngineState.Idle)
    val engineState: StateFlow<SpeechEngineState> = _engineState

    private val _segments = MutableStateFlow<List<TranscriptSegment>>(emptyList())
    private val _livePartial = MutableStateFlow("")

    val transcriptUpdates: StateFlow<TranscriptUpdate> = MutableStateFlow(
        TranscriptUpdate("", "", TranscriptSource.LIVE_VOSK, 0)
    ).also { out ->
        // Cheap manual combine to avoid pulling in an extra Flow operator import set.
        CoroutineScope(Dispatchers.Default).launch {
            kotlinx.coroutines.flow.combine(_segments, _livePartial) { segs, live ->
                val committed = segs.joinToString(" ") { it.text }
                val full = if (live.isBlank()) committed else "$committed $live".trim()
                TranscriptUpdate(
                    committedText = committed,
                    livePartialText = live,
                    source = segs.lastOrNull()?.source ?: TranscriptSource.LIVE_VOSK,
                    wordCount = full.split(Regex("\\s+")).count { it.isNotBlank() }
                )
            }.collect { out.value = it }
        }
    }

    private var recordJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var nextSegmentId = 0L
    private var usingOfflineModels = false

    fun warmUpInBackground(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            _engineState.value = SpeechEngineState.LoadingModels
            try {
                vosk.loadModelBlocking()
                whisper.loadModelBlocking()
                usingOfflineModels = vosk.isReady
                _engineState.value = if (usingOfflineModels) SpeechEngineState.Ready else SpeechEngineState.ModelsUnavailable
            } catch (e: Exception) {
                Log.e("HybridSpeechController", "Model load failed, will fall back to Android SpeechRecognizer", e)
                _engineState.value = SpeechEngineState.ModelsUnavailable
            }
        }
    }

    fun start(scope: CoroutineScope) {
        resetTranscript()
        if (!usingOfflineModels) {
            startAndroidFallback()
            return
        }
        _engineState.value = SpeechEngineState.Listening
        recordJob = scope.launch(Dispatchers.IO) { recordLoop(scope) }
    }

    fun stop() {
        recordJob?.cancel()
        recordJob = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        androidFallback?.stop()
        vosk.reset()
        if (_engineState.value == SpeechEngineState.Listening) {
            _engineState.value = SpeechEngineState.Ready
        }
    }

    fun addManualText(text: String) {
        _segments.update { it + TranscriptSegment(nextSegmentId++, text, TranscriptSource.MANUAL) }
    }

    fun resetTranscript() {
        _segments.value = emptyList()
        _livePartial.value = ""
        nextSegmentId = 0
    }

    fun release() {
        stop()
        vosk.close()
        whisper.close()
    }

    // ---- offline hybrid pipeline -------------------------------------------------

    private suspend fun recordLoop(scope: CoroutineScope) {
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufSize = maxOf(minBuf, sampleRate) // at least ~0.5s of headroom
        val record = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, sampleRate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize)
        audioRecord = record
        record.startRecording()

        val chunk = ShortArray(1600) // 100ms @16kHz
        var segmentBuffer = ArrayList<Short>()

        while (scope.isActive && record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            val read = record.read(chunk, 0, chunk.size)
            if (read <= 0) continue
            for (i in 0 until read) segmentBuffer.add(chunk[i])

            val (text, isFinal) = vosk.acceptAudio(chunk, read)
            if (isFinal) {
                if (text.isNotBlank()) {
                    val segmentId = nextSegmentId++
                    // 1) show the fast Vosk guess immediately
                    _segments.update { it + TranscriptSegment(segmentId, text, TranscriptSource.LIVE_VOSK) }
                    _livePartial.value = ""
                    // 2) refine it in the background with Whisper, then swap it in place
                    val audioForWhisper = segmentBuffer.toShortArray()
                    scope.launch(Dispatchers.Default) { refineSegment(segmentId, audioForWhisper) }
                }
                segmentBuffer = ArrayList()
            } else if (text.isNotBlank()) {
                _livePartial.value = text
            }
        }
        record.stop()
        record.release()
    }

    private suspend fun refineSegment(segmentId: Long, audio: ShortArray) {
        if (!whisper.isReady || audio.size < sampleRate / 4) return // skip refining near-silent/too-short clips
        _engineState.value = SpeechEngineState.RefiningWithWhisper
        val refined = withContext(Dispatchers.Default) { whisper.transcribe(audio) }
        if (refined.isNotBlank()) {
            _segments.update { list ->
                list.map { if (it.id == segmentId) it.copy(text = refined, source = TranscriptSource.REFINED_WHISPER) else it }
            }
        }
        if (_engineState.value == SpeechEngineState.RefiningWithWhisper) {
            _engineState.value = SpeechEngineState.Listening
        }
    }

    // ---- last-resort fallback ------------------------------------------------------

    private fun startAndroidFallback() {
        _engineState.value = SpeechEngineState.Listening
        androidFallback = AndroidFallbackEngine(
            context,
            onPartial = { _livePartial.value = it },
            onFinal = { text ->
                if (text.isNotBlank()) {
                    _segments.update { it + TranscriptSegment(nextSegmentId++, text, TranscriptSource.ANDROID_FALLBACK) }
                }
                _livePartial.value = ""
            },
            onError = { _engineState.value = SpeechEngineState.Error(it) }
        ).also { it.start() }
    }
}
