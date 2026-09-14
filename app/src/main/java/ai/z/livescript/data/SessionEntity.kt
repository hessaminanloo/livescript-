package ai.z.livescript.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A persisted meeting/session. This is what the old app's "save session" button
 * claimed to do but never actually did (it just cleared the screen).
 */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val topic: String,
    val transcript: String,
    val agentResultsJson: String,   // raw JSON blob returned by /api/agents/run-all, or offline results
    val createdAt: Long,
    val wasOffline: Boolean         // true if agents were run by the on-device model
)
