package no.kristo.cfanalytics.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import no.kristo.cfanalytics.CfApp
import no.kristo.cfanalytics.R
import no.kristo.cfanalytics.data.Domain
import no.kristo.cfanalytics.data.Metric
import no.kristo.cfanalytics.data.Period
import no.kristo.cfanalytics.data.Range
import no.kristo.cfanalytics.data.Snapshot
import no.kristo.cfanalytics.widget.WidgetData
import java.time.LocalDate

data class UiState(
    val ready: Boolean = false,
    val domains: List<Domain> = emptyList(),
    val selected: Domain? = null,
    val tab: Int = 0,
    val range: Range = Range.LAST_24H,
    val period: Period = Period.D30,
    val snapshot: Snapshot? = null,
    val history: Map<Metric, Map<LocalDate, Double>> = emptyMap(),
    val loading: Boolean = false,
    val detail: Metric? = null,
    val selectedCountry: Int = 0,
    /** Visits last 24 h per domain id, from cache (shown in the domain menu). */
    val domainVisits: Map<String, Double> = emptyMap(),
)

data class AddState(val open: Boolean = false, val busy: Boolean = false, val error: String? = null)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as CfApp).repository

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _add = MutableStateFlow(AddState())
    val add: StateFlow<AddState> = _add.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            repo.prefs.prefs.collect { p ->
                val before = _state.value
                val selected = p.domains.firstOrNull { it.id == p.selectedId } ?: p.domains.firstOrNull()
                _state.update {
                    it.copy(ready = true, domains = p.domains, selected = selected, tab = p.tab, range = p.range, period = p.period)
                }
                if (!before.ready || before.selected?.id != selected?.id || before.range != p.range) load(network = true)
                if (before.domains != p.domains) loadDomainVisits()
            }
        }
    }

    /** Cache first, then network. */
    private fun load(network: Boolean, announce: Boolean = false) {
        val s = _state.value
        val domain = s.selected ?: run { _state.update { it.copy(snapshot = null, history = emptyMap()) }; return }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    snapshot = repo.cachedSnapshot(domain.id, s.range),
                    history = repo.history(domain.id, Period.Y1.days),
                    selectedCountry = 0,
                    loading = network,
                )
            }
            if (!network) return@launch
            val result = runCatching { repo.refresh(domain, s.range) }
            _state.update { st ->
                if (st.selected?.id != domain.id) st
                else st.copy(
                    snapshot = result.getOrNull() ?: st.snapshot,
                    history = repo.history(domain.id, Period.Y1.days),
                    loading = false,
                )
            }
            loadDomainVisits()
            if (result.isSuccess) runCatching { WidgetData.updateAll(getApplication<Application>()) }
            result.onSuccess { if (announce) send(getApplication<Application>().getString(R.string.data_updated)) }
                .onFailure { send(getApplication<Application>().getString(R.string.update_failed, it.message.orEmpty())) }
        }
    }

    private fun loadDomainVisits() = viewModelScope.launch {
        val visits = _state.value.domains.mapNotNull { d ->
            repo.cachedSnapshot(d.id, Range.LAST_24H)?.values?.get(Metric.VISITS)?.let { d.id to it }
        }.toMap()
        _state.update { it.copy(domainVisits = visits) }
    }

    private fun send(msg: String) { _messages.trySend(msg) }

    fun refresh() = load(network = true, announce = true)

    fun selectDomain(domain: Domain) = viewModelScope.launch { repo.prefs.setSelected(domain.id) }

    fun selectTab(tab: Int) {
        _state.update { it.copy(tab = tab, detail = null) }
        viewModelScope.launch { repo.prefs.setTab(tab) }
    }

    fun selectRange(range: Range) = viewModelScope.launch { repo.prefs.setRange(range) }
    fun selectPeriod(period: Period) = viewModelScope.launch { repo.prefs.setPeriod(period) }
    fun openDetail(metric: Metric?) = _state.update { it.copy(detail = metric) }
    fun selectCountry(index: Int) = _state.update { it.copy(selectedCountry = index) }

    fun openAdd() { _add.value = AddState(open = true) }
    fun closeAdd() { if (!_add.value.busy) _add.value = AddState() }

    fun saveDomain(name: String, zoneId: String, token: String) {
        val app = getApplication<Application>()
        val d = name.trim().lowercase()
        val z = zoneId.trim().lowercase()
        val t = token.trim()
        val error = when {
            !DOMAIN_RE.matches(d) -> app.getString(R.string.err_domain)
            !ZONE_RE.matches(z) -> app.getString(R.string.err_zone)
            t.isEmpty() -> app.getString(R.string.err_token)
            else -> null
        }
        if (error != null) { _add.update { it.copy(error = error) }; return }
        _add.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.addDomain(d, z, t) }
                .onSuccess { _add.value = AddState(); send(app.getString(R.string.domain_added, d)) }
                .onFailure { e -> _add.update { it.copy(busy = false, error = e.message) } }
        }
    }

    fun removeDomain(domain: Domain) = viewModelScope.launch {
        repo.removeDomain(domain)
        send(getApplication<Application>().getString(R.string.domain_removed, domain.name))
    }

    private companion object {
        val DOMAIN_RE = Regex("^[a-z0-9-]+(\\.[a-z0-9-]+)+$")
        val ZONE_RE = Regex("^[0-9a-f]{32}$")
    }
}
