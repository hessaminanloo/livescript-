package ai.z.livescript.data

import kotlinx.coroutines.flow.Flow

class SessionRepository(private val dao: SessionDao) {

    fun observeSessions(): Flow<List<SessionEntity>> = dao.observeAll()

    suspend fun save(
        title: String,
        topic: String,
        transcript: String,
        agentResultsJson: String,
        wasOffline: Boolean
    ): Long = dao.insert(
        SessionEntity(
            title = title.ifBlank { "جلسه بدون عنوان" },
            topic = topic,
            transcript = transcript,
            agentResultsJson = agentResultsJson,
            createdAt = System.currentTimeMillis(),
            wasOffline = wasOffline
        )
    )

    suspend fun delete(session: SessionEntity) = dao.delete(session)

    suspend fun clearAll() = dao.clearAll()
}
