package ai.z.livescript

import android.app.Application
import ai.z.livescript.agents.ConnectivityObserver
import ai.z.livescript.agents.OfflineAgentRunner
import ai.z.livescript.agents.RemoteAgentRunner
import ai.z.livescript.data.AppDatabase
import ai.z.livescript.data.SessionRepository
import ai.z.livescript.stt.HybridSpeechController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * Central place for everything that used to be missing from the old MainActivity:
 * a real database, a real speech pipeline, and a real (online/offline) agent runner.
 * Everything here is created once and reused, instead of being re-created (or never
 * persisted at all) every time the activity is recreated.
 */
class LiveScriptApplication : Application() {

    val appScope = CoroutineScope(SupervisorJob())

    val database: AppDatabase by lazy { AppDatabase.build(this) }
    val sessionRepository: SessionRepository by lazy { SessionRepository(database.sessionDao()) }

    val connectivityObserver: ConnectivityObserver by lazy { ConnectivityObserver(this) }

    val hybridSpeech: HybridSpeechController by lazy { HybridSpeechController(this) }

    val remoteAgentRunner: RemoteAgentRunner by lazy { RemoteAgentRunner() }
    val offlineAgentRunner: OfflineAgentRunner by lazy { OfflineAgentRunner(this) }

    override fun onCreate() {
        super.onCreate()
        // Kick off model loading in the background as soon as the process starts,
        // so the user isn't staring at a spinner the first time they hit "record".
        hybridSpeech.warmUpInBackground(appScope)
        offlineAgentRunner.warmUpInBackground(appScope)
    }
}
