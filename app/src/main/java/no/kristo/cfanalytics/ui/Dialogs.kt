@file:OptIn(ExperimentalMaterial3Api::class)

package no.kristo.cfanalytics.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.kristo.cfanalytics.R
import no.kristo.cfanalytics.data.Domain
import no.kristo.cfanalytics.data.Metric
import no.kristo.cfanalytics.data.Period
import java.time.LocalDate

@Composable
fun StatDetailSheet(
    spec: StatSpec,
    domain: String,
    history: Map<Metric, Map<LocalDate, Double>>,
    period: Period,
    onPeriod: (Period) -> Unit,
    onDismiss: () -> Unit,
) {
    val chart = remember(history, period, spec.metric) { buildChart(seriesFor(history, spec.metric), spec.metric, period) }
    val unit = spec.unitText ?: stringResource(spec.unit)
    val d = spec.metric.decimals
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier.padding(start = 20.dp, end = 20.dp, bottom = 32.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(spec.icon, null, tint = spec.color, modifier = Modifier.size(22.dp)) }
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(spec.label), fontSize = 18.sp)
                    Text(domain, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, stringResource(R.string.close)) }
            }
            PeriodSelector(period, onPeriod)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (chart.hasData) nf(chart.total, d) else "—", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Light)
                Text(
                    stringResource(R.string.total_suffix, unit).trim(), fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            if (!chart.hasData) {
                Text(stringResource(R.string.no_history), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column {
                BarChart(chart.bars, spec.color, 180.dp)
                Row(Modifier.padding(top = 8.dp)) {
                    Text(startLabel(chart.start, period), fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.weight(1f))
                    Text(stringResource(R.string.today), fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    R.string.fact_avg to chart.avgPerDay,
                    R.string.fact_max to chart.max,
                    R.string.fact_min to chart.min,
                ).forEach { (label, v) ->
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(label), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if (chart.hasData) nf(v, d) else "—", fontSize = 18.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddDomainDialog(state: AddState, onSave: (String, String, String) -> Unit, onDismiss: () -> Unit) {
    var domain by rememberSaveable { mutableStateOf("") }
    var zone by rememberSaveable { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.AddLink, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.add_domain)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.add_domain_info), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = domain, onValueChange = { domain = it },
                    label = { Text(stringResource(R.string.field_domain)) },
                    placeholder = { Text("eksempel.no") },
                    singleLine = true, enabled = !state.busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = zone, onValueChange = { zone = it },
                    label = { Text(stringResource(R.string.field_zone)) },
                    placeholder = { Text("023bac12…") },
                    singleLine = true, enabled = !state.busy,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = token, onValueChange = { token = it },
                    label = { Text(stringResource(R.string.field_token)) },
                    singleLine = true, enabled = !state.busy,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )
                when {
                    state.busy -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.verifying), fontSize = 12.sp)
                    }
                    state.error != null -> Text(state.error, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(domain, zone, token) }, enabled = !state.busy) { Text(stringResource(R.string.add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.busy) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
fun SettingsDialog(domains: List<Domain>, onRemove: (Domain) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings)) },
        text = {
            Column {
                Text(stringResource(R.string.settings_domains), style = MaterialTheme.typography.titleSmall)
                domains.forEach { d ->
                    ListItem(
                        headlineContent = { Text(d.name) },
                        supportingContent = { Text(d.zoneId, fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
                        leadingContent = { Icon(Icons.Rounded.Language, null) },
                        trailingContent = {
                            IconButton(onClick = { onRemove(d) }) { Icon(Icons.Rounded.Delete, stringResource(R.string.remove)) }
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
