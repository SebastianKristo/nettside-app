package no.kristo.cfanalytics.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import no.kristo.cfanalytics.CfApp
import no.kristo.cfanalytics.data.Range
import java.util.concurrent.TimeUnit

/** Every 30 minutes: refresh every domain so the daily history keeps one point per day. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as CfApp).repository
        var failed = false
        for (domain in repo.prefs.current().domains) {
            runCatching {
                repo.refresh(domain, Range.TODAY)
                repo.refresh(domain, Range.LAST_24H)
            }.onFailure { failed = true }
        }
        return if (failed) Result.retry() else Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("cf-refresh", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
