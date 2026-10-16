package no.kristo.cfanalytics

import android.app.Application
import no.kristo.cfanalytics.data.AppDb
import no.kristo.cfanalytics.data.CloudflareApi
import no.kristo.cfanalytics.data.PrefsStore
import no.kristo.cfanalytics.data.Repository
import no.kristo.cfanalytics.data.TokenStore
import no.kristo.cfanalytics.work.RefreshWorker

class CfApp : Application() {
    lateinit var repository: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = Repository(CloudflareApi(), AppDb.create(this).dao(), PrefsStore(this), TokenStore(this))
        RefreshWorker.schedule(this)
    }
}
