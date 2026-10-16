package no.kristo.cfanalytics

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import no.kristo.cfanalytics.ui.AppScreen
import no.kristo.cfanalytics.ui.theme.CfTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CfTheme { AppScreen() } }
    }
}
