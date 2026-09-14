package ai.z.livescript.ui

import ai.z.livescript.LiveScriptApplication
import ai.z.livescript.databinding.ActivityHistoryBinding
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val app = application as LiveScriptApplication
        val adapter = SessionAdapter(
            onClick = { /* could open a read-only detail view; kept simple for now */ },
            onLongClickDelete = { session ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("حذف جلسه؟")
                    .setMessage(session.title)
                    .setPositiveButton("حذف") { _, _ ->
                        lifecycleScope.launch { app.sessionRepository.delete(session) }
                    }
                    .setNegativeButton("انصراف", null)
                    .show()
            }
        )
        binding.recyclerSessions.layoutManager = LinearLayoutManager(this)
        binding.recyclerSessions.adapter = adapter

        lifecycleScope.launch {
            app.sessionRepository.observeSessions().collect { sessions ->
                adapter.submitList(sessions)
                binding.tvEmpty.visibility = if (sessions.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }
}
