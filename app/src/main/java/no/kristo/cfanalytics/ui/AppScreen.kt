@file:OptIn(ExperimentalMaterial3Api::class)

package no.kristo.cfanalytics.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import no.kristo.cfanalytics.R
import no.kristo.cfanalytics.data.Metric
import no.kristo.cfanalytics.data.Period
import no.kristo.cfanalytics.data.Range
import no.kristo.cfanalytics.data.Snapshot
import no.kristo.cfanalytics.ui.theme.ChartColors

@Composable
fun AppScreen(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val add by vm.add.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var settingsOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                title = { DomainPicker(state, onPick = vm::selectDomain, onAdd = vm::openAdd) },
                actions = {
                    RefreshButton(state.loading, enabled = state.selected != null, onClick = vm::refresh)
                    IconButton(onClick = { settingsOpen = true }) {
                        Icon(Icons.Rounded.Settings, stringResource(R.string.settings))
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                TABS.forEachIndexed { i, tab ->
                    val selected = state.tab == i
                    NavigationBarItem(
                        selected = selected,
                        onClick = { vm.selectTab(i) },
                        icon = { Icon(if (selected) tab.iconSelected else tab.icon, null) },
                        label = { Text(stringResource(tab.title), fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium) },
                    )
                }
            }
        },
    ) { padding ->
        if (state.ready && state.domains.isEmpty()) {
            EmptyState(Modifier.padding(padding), onAdd = vm::openAdd)
        } else {
            PullToRefreshBox(
                isRefreshing = state.loading,
                onRefresh = vm::refresh,
                modifier = Modifier.padding(padding).fillMaxSize(),
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
                ) {
                    HeroCard(state.snapshot, state.range)
                    Spacer(Modifier.height(16.dp))
                    TabContent(state, vm)
                }
            }
        }
    }

    val tab = TABS[state.tab]
    val detailSpec = tab.stats.firstOrNull { it.metric == state.detail }
    if (detailSpec != null) {
        StatDetailSheet(
            spec = detailSpec,
            domain = state.selected?.name.orEmpty(),
            history = state.history,
            period = state.period,
            onPeriod = vm::selectPeriod,
            onDismiss = { vm.openDetail(null) },
        )
    }
    if (add.open) {
        AddDomainDialog(add, onSave = vm::saveDomain, onDismiss = vm::closeAdd)
    }
    if (settingsOpen) {
        SettingsDialog(state.domains, onRemove = vm::removeDomain, onDismiss = { settingsOpen = false })
    }
}

@Composable
private fun DomainPicker(state: UiState, onPick: (no.kristo.cfanalytics.data.Domain) -> Unit, onAdd: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { open = true },
            label = {
                Text(state.selected?.name ?: stringResource(R.string.add_domain), maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            leadingIcon = { Icon(Icons.Rounded.Language, null, tint = MaterialTheme.colorScheme.primary) },
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null) },
            shape = RoundedCornerShape(20.dp),
            border = null,
            colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
            modifier = Modifier.height(40.dp),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            state.domains.forEach { d ->
                val sel = d.id == state.selected?.id
                DropdownMenuItem(
                    text = { Text(d.name) },
                    leadingIcon = { Icon(if (sel) Icons.Rounded.Check else Icons.Rounded.Language, null) },
                    onClick = { open = false; onPick(d) },
                    modifier = if (sel) Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest) else Modifier,
                )
            }
            if (state.domains.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = 8.dp))
            DropdownMenuItem(
                text = { Text(stringResource(R.string.add_domain)) },
                leadingIcon = { Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { open = false; onAdd() },
            )
        }
    }
}

@Composable
private fun RefreshButton(loading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "spin")
    val angle by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Restart),
        label = "angle",
    )
    IconButton(onClick = onClick, enabled = enabled && !loading) {
        Icon(Icons.Rounded.Refresh, null, Modifier.rotate(if (loading) angle else 0f))
    }
}

@Composable
private fun EmptyState(modifier: Modifier, onAdd: () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.Language, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.empty_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAdd) {
            Icon(Icons.Rounded.Add, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.add_domain))
        }
    }
}

fun Snapshot?.text(metric: Metric, decimals: Int = metric.decimals): String =
    this?.values?.get(metric)?.let { nf(it, decimals) } ?: "—"

@Composable
private fun HeroCard(snapshot: Snapshot?, range: Range) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        Column {
            Column(Modifier.padding(start = 20.dp, top = 20.dp, end = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(ChartColors.Green))
                    Text(
                        stringResource(if (range == Range.TODAY) R.string.hero_live_today else R.string.hero_live_24h),
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if ((snapshot?.failedQueries ?: 0) > 0) {
                        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(8.dp)) {
                            Text(
                                stringResource(R.string.hero_query_error),
                                color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(top = 4.dp)) {
                    HeroValue(snapshot.text(Metric.VISITS), stringResource(R.string.hero_visits), big = true)
                    HeroValue(snapshot.text(Metric.REQUESTS), stringResource(R.string.hero_requests))
                    HeroValue(snapshot.text(Metric.BYTES_MB, 0), "MB")
                }
            }
            Sparkline(
                snapshot?.hourly.orEmpty(), MaterialTheme.colorScheme.primary,
                Modifier.fillMaxWidth().height(72.dp).padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun HeroValue(value: String, label: String, big: Boolean = false) {
    Column {
        Text(
            value,
            style = if (big) MaterialTheme.typography.displayLarge else MaterialTheme.typography.headlineSmall,
            fontWeight = if (big) FontWeight.Light else FontWeight.Normal,
        )
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TabContent(state: UiState, vm: MainViewModel) {
    val tab = TABS[state.tab]
    val snap = state.snapshot
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(tab.title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            SingleChoiceSegmentedButtonRow {
                Range.entries.forEachIndexed { i, r ->
                    SegmentedButton(
                        selected = state.range == r,
                        onClick = { vm.selectRange(r) },
                        shape = SegmentedButtonDefaults.itemShape(i, Range.entries.size),
                    ) { Text(stringResource(if (r == Range.TODAY) R.string.range_today else R.string.range_24h), fontSize = 13.sp) }
                }
            }
        }

        tab.stats.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { spec -> StatCard(spec, snap, Modifier.weight(1f)) { vm.openDetail(spec.metric) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        tab.progress.forEach { p -> ProgressCard(p, snap) }

        if (tab.map && snap != null) {
            CountriesCard(snap, state.selectedCountry, vm::selectCountry)
            PagesCard(snap)
        }

        Text(stringResource(R.string.history), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp))
        PeriodSelector(state.period, vm::selectPeriod)
        tab.charts.forEach { spec -> HistoryCard(spec, state.history, state.period) }

        val tabErrors = snap?.errors.orEmpty().filter { it.section in tab.sections }
        if (tabErrors.isNotEmpty()) ErrorBanner(tabErrors.joinToString(" · ") { "${it.section}: ${it.message}" })

        Footer(snap)
    }
}

@Composable
private fun StatCard(spec: StatSpec, snap: Snapshot?, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        onClick = onClick,
        modifier = modifier,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(spec.icon, null, tint = spec.color, modifier = Modifier.size(18.dp))
                Text(
                    stringResource(spec.label), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(snap.text(spec.metric), fontSize = 32.sp, lineHeight = 36.sp, maxLines = 1)
                Text(
                    spec.unitText ?: stringResource(spec.unit), fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ProgressCard(spec: ProgressSpec, snap: Snapshot?) {
    val v = snap?.values?.get(spec.metric)
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row {
                Text(stringResource(spec.label), fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(if (v != null) "${nf(v, 2)} %" else "—", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            LinearProgressIndicator(
                progress = { ((v ?: 0.0) / 100).toFloat().coerceIn(0f, 1f) },
                color = spec.color,
                trackColor = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CountriesCard(snap: Snapshot, selected: Int, onSelect: (Int) -> Unit) {
    val countries = snap.countries
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.map_title), fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.map_hint), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            WorldMap(
                countries, selected, onSelect,
                dotColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.9f),
                accent = MaterialTheme.colorScheme.primary,
            )
            countries.getOrNull(selected)?.let { c ->
                val total = snap.values[Metric.VISITS]?.takeIf { it > 0 } ?: countries.sumOf { it.visits }.toDouble().coerceAtLeast(1.0)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Rounded.LocationOn, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text(countryName(c.code), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(
                            stringResource(R.string.share_of_traffic, "${nf(c.visits / total * 100, 1)} %"),
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(nf(c.visits.toDouble()), fontSize = 22.sp)
                }
            }
            val max = countries.maxOfOrNull { it.visits }?.coerceAtLeast(1L) ?: 1L
            countries.forEachIndexed { i, c ->
                BarRow(c.code, c.visits, max, ChartColors.Blue, labelWidth = 32, onClick = { onSelect(i) })
            }
        }
    }
}

@Composable
private fun PagesCard(snap: Snapshot) {
    if (snap.pages.isEmpty()) return
    val max = snap.pages.maxOf { it.count }.coerceAtLeast(1L)
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.top_pages), fontSize = 16.sp, fontWeight = FontWeight.Medium)
            snap.pages.forEach { p -> BarRow(p.path, p.count, max, MaterialTheme.colorScheme.primary, labelWidth = 96, mono = true) }
        }
    }
}

@Composable
private fun BarRow(label: String, value: Long, max: Long, color: Color, labelWidth: Int, mono: Boolean = false, onClick: (() -> Unit)? = null) {
    Row(
        (if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = if (mono) FontFamily.Monospace else null,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(labelWidth.dp),
        )
        Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
            Box(Modifier.fillMaxWidth((value.toFloat() / max).coerceIn(0.02f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
        }
        Text(nf(value.toDouble()), fontSize = 13.sp, modifier = Modifier.width(48.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
fun PeriodSelector(period: Period, onSelect: (Period) -> Unit) {
    val labels = mapOf(Period.D7 to "7 d", Period.D30 to "30 d", Period.D90 to "90 d", Period.Y1 to "1 år")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        Period.entries.forEachIndexed { i, p ->
            SegmentedButton(
                selected = period == p,
                onClick = { onSelect(p) },
                shape = SegmentedButtonDefaults.itemShape(i, Period.entries.size),
            ) { Text(labels.getValue(p)) }
        }
    }
}

@Composable
private fun HistoryCard(spec: StatSpec, history: Map<Metric, Map<java.time.LocalDate, Double>>, period: Period) {
    val chart = remember(history, period, spec.metric) { buildChart(seriesFor(history, spec.metric), spec.metric, period) }
    val unit = spec.unitText ?: stringResource(spec.unit)
    val d = spec.metric.decimals
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(stringResource(spec.label), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(if (chart.hasData) nf(chart.total, d) else "—", fontSize = 24.sp)
                if (unit.isNotEmpty()) Text(" $unit", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
            }
            BarChart(chart.bars, spec.color, 88.dp)
            Row {
                val muted = MaterialTheme.colorScheme.outline
                Text(startLabel(chart.start, period), fontSize = 11.sp, color = muted)
                Spacer(Modifier.weight(1f))
                Text(
                    if (spec.metric.aggregate == no.kristo.cfanalytics.data.Aggregate.SUM) stringResource(R.string.avg_per_day, nf(chart.avgPerDay, d))
                    else stringResource(R.string.max_value, nf(chart.max, d)),
                    fontSize = 11.sp, color = muted,
                )
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.today), fontSize = 11.sp, color = muted)
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Rounded.Error, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(20.dp))
        Text(stringResource(R.string.error_banner, message), color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun Footer(snap: Snapshot?) {
    val failed = snap?.failedQueries ?: 0
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
        Icon(
            if (failed > 0) Icons.Rounded.Error else Icons.Rounded.CheckCircle, null,
            tint = if (failed > 0) MaterialTheme.colorScheme.error else ChartColors.Green, modifier = Modifier.size(16.dp),
        )
        Text(
            if (failed > 0) stringResource(R.string.footer_errors, snap?.okQueries ?: 0, failed) else stringResource(R.string.footer_ok),
            fontSize = 12.sp, color = MaterialTheme.colorScheme.outline,
        )
    }
}
