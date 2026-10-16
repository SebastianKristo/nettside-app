package no.kristo.cfanalytics.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import no.kristo.cfanalytics.CfApp
import no.kristo.cfanalytics.R
import no.kristo.cfanalytics.data.Domain
import no.kristo.cfanalytics.ui.theme.CfTheme

/** Lets the user pick which domain a widget shows; stored per appWidgetId. */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Backing out without a choice leaves no widget behind.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }

        val repo = (application as CfApp).repository
        setContent {
            CfTheme {
                var domains by remember { mutableStateOf<List<Domain>?>(null) }
                LaunchedEffect(Unit) { domains = repo.prefs.current().domains }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(vertical = 24.dp)) {
                        Text(
                            stringResource(R.string.widget_pick_domain),
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        )
                        val list = domains
                        if (list != null && list.isEmpty()) {
                            Text(stringResource(R.string.widget_empty), modifier = Modifier.padding(24.dp))
                        }
                        list.orEmpty().forEach { d ->
                            ListItem(
                                headlineContent = { Text(d.name) },
                                leadingContent = { Icon(Icons.Rounded.Language, null) },
                                modifier = Modifier.clickable { choose(appWidgetId, d) },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun choose(appWidgetId: Int, domain: Domain) {
        lifecycleScope.launch {
            WidgetPrefs.setDomainId(this@WidgetConfigActivity, appWidgetId, domain.id)
            WidgetData.updateAll(this@WidgetConfigActivity)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }
    }
}
