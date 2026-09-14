package ai.z.livescript

import ai.z.livescript.agents.AgentRunResult
import ai.z.livescript.agents.AgentType
import ai.z.livescript.databinding.ActivityMainBinding
import ai.z.livescript.stt.SpeechEngineState
import ai.z.livescript.ui.HistoryActivity
import ai.z.livescript.util.Prefs
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var app: LiveScriptApplication

    private var isRecording = false
    private var lastAgentResultsJson: String = ""
    private var lastRunWasOffline = false

    private val micPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) toggleRecording() else toast(getString(R.string.mic_permission_rationale))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = application as LiveScriptApplication
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        binding.etServerUrl.setText(Prefs.getServerUrl(this))
        binding.etServerUrl.addTextChangedListener(simpleWatcher { Prefs.setServerUrl(this, it) })

        binding.switchOfflineMode.isChecked = Prefs.isForcedOfflineMode(this)
        binding.switchOfflineMode.setOnCheckedChangeListener { _, checked ->
            Prefs.setForcedOfflineMode(this, checked)
        }

        buildAgentChips()
        observeEngineState()
        observeTranscript()

        binding.btnRecord.setOnClickListener { onRecordClicked() }
        binding.btnAddManualText.setOnClickListener { showAddTextDialog() }
        binding.btnRunAgents.setOnClickListener { runAgents() }
        binding.btnSaveSession.setOnClickListener { saveSession() }
        binding.btnClearSession.setOnClickListener { confirmClearSession() }
        binding.btnHistory.setOnClickListener { startActivity(Intent(this, HistoryActivity::class.java)) }
    }

    // ---- speech ---------------------------------------------------------------------

    private fun onRecordClicked() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            toggleRecording()
        }
    }

    private fun toggleRecording() {
        isRecording = !isRecording
        if (isRecording) {
            app.hybridSpeech.start(lifecycleScope)
            binding.btnRecord.text = getString(R.string.btn_stop_recording)
        } else {
            app.hybridSpeech.stop()
            binding.btnRecord.text = getString(R.string.btn_start_recording)
        }
    }

    private fun showAddTextDialog() {
        val input = EditText(this)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_add_text_title)
            .setView(input)
            .setPositiveButton("افزودن") { _, _ ->
                val text = input.text.toString()
                if (text.isNotBlank()) app.hybridSpeech.addManualText(text)
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun observeEngineState() {
        lifecycleScope.launch {
            app.hybridSpeech.engineState.collect { state ->
                binding.tvEngineStatus.text = when (state) {
                    SpeechEngineState.LoadingModels -> getString(R.string.status_loading_models)
                    SpeechEngineState.ModelsUnavailable -> getString(R.string.status_models_unavailable)
                    SpeechEngineState.Listening -> getString(R.string.status_listening)
                    SpeechEngineState.RefiningWithWhisper -> getString(R.string.status_refining)
                    is SpeechEngineState.Error -> state.message
                    else -> if (app.connectivityObserver.isOnlineNow())
                        getString(R.string.status_ready_online) else getString(R.string.status_ready_offline)
                }
            }
        }
    }

    private fun observeTranscript() {
        lifecycleScope.launch {
            app.hybridSpeech.transcriptUpdates.collect { update ->
                val full = listOf(update.committedText, update.livePartialText)
                    .filter { it.isNotBlank() }.joinToString(" ")
                // Only overwrite if the user isn't actively mid-edit of manually typed text;
                // simplest safe behaviour is to just keep transcript in sync while recording.
                if (isRecording) binding.etTranscript.setText(full)
            }
        }
    }

    // ---- agents ---------------------------------------------------------------------

    private fun buildAgentChips() {
        binding.chipGroupAgents.removeAllViews()
        for (type in AgentType.entries) {
            val chip = Chip(this).apply {
                text = type.label
                isCheckable = true
                tag = type
            }
            binding.chipGroupAgents.addView(chip)
        }
    }

    private fun selectedAgents(): List<AgentType> =
        (0 until binding.chipGroupAgents.childCount)
            .map { binding.chipGroupAgents.getChildAt(it) as Chip }
            .filter { it.isChecked }
            .mapNotNull { it.tag as? AgentType }

    private fun runAgents() {
        val transcript = binding.etTranscript.text?.toString().orEmpty()
        if (transcript.isBlank()) {
            toast(getString(R.string.toast_no_transcript)); return
        }
        val agents = selectedAgents()
        if (agents.isEmpty()) {
            toast(getString(R.string.toast_no_agents)); return
        }
        val topic = binding.etTopic.text?.toString().orEmpty()
        val forceOffline = binding.switchOfflineMode.isChecked
        val goOffline = forceOffline || !app.connectivityObserver.isOnlineNow()

        binding.btnRunAgents.isEnabled = false
        lifecycleScope.launch {
            val runner = if (goOffline) app.offlineAgentRunner else app.remoteAgentRunner
            val result = runner.run(transcript, topic, agents)
            binding.btnRunAgents.isEnabled = true
            when (result) {
                is AgentRunResult.Success -> {
                    lastAgentResultsJson = result.rawJson
                    lastRunWasOffline = result.ranOffline
                    binding.tvAgentResults.text = result.results.joinToString("\n\n") { r ->
                        "▸ ${r.type.label}\n${r.conclusion}"
                    }
                }
                is AgentRunResult.Failure -> {
                    binding.tvAgentResults.text = result.message
                }
            }
        }
    }

    // ---- session persistence (this is the actual fix for the old "save does nothing" bug) --

    private fun saveSession() {
        val transcript = binding.etTranscript.text?.toString().orEmpty()
        if (transcript.isBlank()) {
            toast(getString(R.string.toast_no_transcript)); return
        }
        val topic = binding.etTopic.text?.toString().orEmpty()
        lifecycleScope.launch {
            app.sessionRepository.save(
                title = topic.ifBlank { transcript.take(30) },
                topic = topic,
                transcript = transcript,
                agentResultsJson = lastAgentResultsJson,
                wasOffline = lastRunWasOffline
            )
            toast(getString(R.string.toast_session_saved))
        }
    }

    private fun confirmClearSession() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.confirm_clear_title)
            .setMessage(R.string.confirm_clear_message)
            .setPositiveButton("پاک کن") { _, _ ->
                app.hybridSpeech.resetTranscript()
                binding.etTranscript.setText("")
                binding.tvAgentResults.text = ""
                lastAgentResultsJson = ""
                lastRunWasOffline = false
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    // ---- lifecycle --------------------------------------------------------------------

    override fun onPause() {
        super.onPause()
        if (isRecording) toggleRecording()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun simpleWatcher(onChanged: (String) -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) { onChanged(s?.toString().orEmpty()) }
    }
}
