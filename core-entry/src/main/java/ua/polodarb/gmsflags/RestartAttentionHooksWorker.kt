package ua.polodarb.gmsflags

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import ua.polodarb.gmsflags.analytics.CrashReporter
import ua.polodarb.gmsflags.domain.hookstatus.GetHookStatus
import ua.polodarb.gmsflags.domain.hookstatus.HookApplicationStatus
import ua.polodarb.gmsflags.domain.hookstatus.RestartHookTarget
import ua.polodarb.gmsflags.domain.hookstatus.needsAttention

/**
 * Restarts every target app whose hook needs attention, once per boot.
 *
 * Runs through WorkManager rather than a plain background coroutine or a directly-started
 * foreground service: [BootCompletedReceiver] has no foreground presence of its own to keep this
 * app's process alive while it connects to the root service and force-stops targets - a bare
 * `startForegroundService()` call from a broadcast receiver is denied outside a narrow exemption
 * window, and a plain background coroutine can be frozen mid-connection by the app freezer.
 * WorkManager's own executor already handles both.
 */
class RestartAttentionHooksWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {
    private val getHookStatus: GetHookStatus by inject()
    private val restartHookTarget: RestartHookTarget by inject()
    private val crashReporter: CrashReporter by inject()

    override suspend fun doWork(): Result {
        val overview = getHookStatus().getOrElse { error ->
            logFailure(error)
            return Result.success()
        }

        overview.applications
            .filter(HookApplicationStatus::needsAttention)
            .forEach { application ->
                restartHookTarget(application.androidPackageName).onFailure(::logFailure)
            }

        return Result.success()
    }

    private fun logFailure(error: Throwable) {
        Log.e(TAG, "Failed to restart a hook needing attention after boot", error)
        crashReporter.recordException(error)
    }

    private companion object {
        const val TAG = "RestartAttentionHooks"
    }
}
