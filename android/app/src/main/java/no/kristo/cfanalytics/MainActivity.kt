package no.kristo.cfanalytics

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import no.kristo.cfanalytics.ui.AppScreen
import no.kristo.cfanalytics.ui.theme.CfTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openDomainFrom(intent)
        setContent { CfTheme { AppScreen() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openDomainFrom(intent)
    }

    /** A widget tap carries the domain it shows. */
    private fun openDomainFrom(intent: Intent?) {
        val id = intent?.getStringExtra(EXTRA_DOMAIN_ID)?.takeIf { it.isNotEmpty() } ?: return
        lifecycleScope.launch { (application as CfApp).repository.prefs.setSelected(id) }
    }

    companion object {
        const val EXTRA_DOMAIN_ID = "domainId"
    }
}
