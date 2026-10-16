# Cloudflare Analytics – Android-app (handoff til Claude Code)

Referansedesign: `Cloudflare Analytics.dc.html` (åpne i nettleser, interaktiv). Bygg som native Android-app.

## Stil: Material 3 Expressive
- `androidx.compose.material3` 1.4+ med `MaterialExpressiveTheme` og `MotionScheme.expressive()` (fjær-animasjoner)
- Bunnnavigasjon: `ShortNavigationBar` med pill-indikator som strekker seg ved valg, fylt ikon
- Segmentvalg: connected `ButtonGroup`, valgt knapp morfer til full pill
- Former: stat-kort med asymmetriske hjørner (MaterialShapes), morf ved trykk; hero-kort i primaryContainer med emphasized display-typografi (Roboto Flex wdth 125, vekt 700)
- Refresh: `LoadingIndicator` (morfende former) i stedet for spinner
- Knapper 48dp, seksjonskort 32dp hjørner

## Stack
- Kotlin, Jetpack Compose, Material 3 (`androidx.compose.material3`), min SDK 26
- Dynamic color (Material You) på Android 12+, ellers fallback-tema under
- Ktor eller Retrofit + kotlinx.serialization for GraphQL
- Room (historikk-cache), DataStore + EncryptedSharedPreferences/Keystore for tokens
- WorkManager: oppdatering hvert 30. min
- Språk: norsk bokmål (`values/strings.xml`), tallformat `nb-NO` (mellomrom som tusenskille, komma desimal)

## Skjermstruktur
- **TopAppBar**: domenevelger (AssistChip med `language`-ikon + dropdown) · refresh (`refresh`, roterer under lasting → Snackbar «Data oppdatert fra Cloudflare») · innstillinger (`settings`)
- **Domenemeny** (DropdownMenu): liste over domener (valgt = `check`), divider, «Legg til domene» (`add`)
- **Hero-kort** (surfaceContainerHigh, radius 28): «Live · siste 24 timer», Besøk (displayLarge), Forespørsler, MB, sparkline (område + linje, primary)
- **Innhold per fane**:
  - Overskrift (headlineSmall) + SegmentedButton «Siste 24 t / I dag»
  - 2-kolonne grid med stat-kort (surfaceContainer, radius 16, ikon + label + chevron_right, verdi 32sp + enhet). **Trykk → ModalBottomSheet med historikk**
  - Progress-kort (LinearProgressIndicator) der relevant
  - Historikk: SegmentedButton 7 d / 30 d / 90 d / 1 år + søylediagram-kort (siste søyle full farge, resten 70 % alpha), bunntekst: startdato · snitt/maks · «I dag»
  - Statuslinje nederst: `check_circle` + «Cloudflare · spørringer ok · oppdateres hvert 30. min»
- **NavigationBar** (bunn, 80dp): Trafikk `monitoring` · Besøk `group` · Ytelse `speed` · Sikkerhet `shield` (valgt = fylt ikon, pill-indikator)

### Faner
| Fane | Stat-kort | Ekstra | Historikk |
|---|---|---|---|
| Trafikk | Besøk, Forespørsler, Dataoverføring (MB), Land | – | Besøk, Forespørsler, Dataoverføring |
| Besøk | Sidevisninger, Unike besøkende | Verdenskart + toppland, Mest besøkte sider | Sidevisninger, Unike besøkende |
| Ytelse | Svartid p50, p95 (ms), 4xx-andel, 5xx-andel (%), Fra hurtiglager, Data fra hurtiglager | Treffrate, Båndbredde spart | Svartid p95 (maks), Hurtiglager-treffrate |
| Sikkerhet | DNS-oppslag, Stoppede forespørsler | NXDOMAIN-andel, feilbanner (errorContainer) ved GraphQL-tilgangsfeil | DNS-oppslag |

### Verdenskart (Besøk)
- Prikkmatrise-kart (equirectangular), bobler per land: størrelse ∝ besøk, valgt land = fylt primary
- Trykk på boble/rad → infokort: land, antall, andel av total
- Anbefalt: Canvas i Compose med Natural Earth 110m (eller prikker fra GeoJSON), land-sentroider fra ISO-3166-tabell
- Liste under: landkode, stolpe (secondary), antall

### Stat-detalj (bottom sheet)
Ikon i sirkel, label, domene, lukk · periodevelger · total (displayMedium) · søylediagram 180dp · 3 kort: Snitt / Høyeste / Laveste

### Legg til domene (AlertDialog)
Felter: Domene, Zone ID, API-token (passord). Validering: domene regex `^[a-z0-9-]+(\.[a-z0-9-]+)+$`, Zone ID 32 hex, token ikke tom. Ved lagring: test token mot `GET /client/v4/user/tokens/verify` + en liten GraphQL-spørring; feil vises under feltene. Lagre token kryptert. Hvert domene har sin egen token.

## Tema (dark fallback, primary-seed ≈ #FFB1C8)
```
background/surface      #141218
surfaceContainerLow     #1D1B20
surfaceContainer        #211F26
surfaceContainerHigh    #2B2930
surfaceContainerHighest #36343B
onSurface               #E6E0E9
onSurfaceVariant        #CAC4D0
outline                 #938F99
outlineVariant          #49454F
primary                 #FFB1C8   onPrimary #5E1133
primaryContainer        #7B2949   onPrimaryContainer #FFD9E2
error                   #FFB4AB   errorContainer #5C1A1A
Diagramfarger: rosa #FFB1C8 · blå #A0CAFD · amber #F3BD6E · grønn #8FD6A0 · lilla #C9B6FF
```
Typografi: Roboto Flex. Ikoner: Material Symbols Rounded.

## Data – Cloudflare GraphQL Analytics API
Endepunkt: `POST https://api.cloudflare.com/client/v4/graphql`, header `Authorization: Bearer <token>`.
Token-rettigheter: Zone → Analytics: Read (+ Zone: Read). DNS-analyse krever DNS Analytics-tilgang.

### 24 t / i dag (fanene)
```graphql
query($zone: String!, $since: Time!, $until: Time!) {
  viewer { zones(filter: { zoneTag: $zone }) {
    totals: httpRequestsAdaptiveGroups(limit: 1, filter: { datetime_geq: $since, datetime_lt: $until }) {
      count
      sum { edgeResponseBytes visits }
    }
    hourly: httpRequestsAdaptiveGroups(limit: 48, orderBy: [datetimeHour_ASC], filter: { datetime_geq: $since, datetime_lt: $until }) {
      count dimensions { datetimeHour } sum { visits }
    }
    countries: httpRequestsAdaptiveGroups(limit: 10, orderBy: [sum_visits_DESC], filter: { datetime_geq: $since, datetime_lt: $until }) {
      sum { visits } dimensions { clientCountryName }
    }
    pages: httpRequestsAdaptiveGroups(limit: 5, orderBy: [count_DESC], filter: { datetime_geq: $since, datetime_lt: $until, requestSource: "eyeball" }) {
      count dimensions { clientRequestPath }
    }
    status: httpRequestsAdaptiveGroups(limit: 10, filter: { datetime_geq: $since, datetime_lt: $until }) {
      count dimensions { edgeResponseStatus }
    }
    cache: httpRequestsAdaptiveGroups(limit: 10, filter: { datetime_geq: $since, datetime_lt: $until }) {
      count sum { edgeResponseBytes } dimensions { cacheStatus }
    }
    perf: httpRequestsAdaptiveGroups(limit: 1, filter: { datetime_geq: $since, datetime_lt: $until }) {
      quantiles { edgeTimeToFirstByteMsP50 edgeTimeToFirstByteMsP95 }
    }
    dns: dnsAnalyticsAdaptiveGroups(limit: 10, filter: { datetime_geq: $since, datetime_lt: $until }) {
      count dimensions { responseCode }
    }
    firewall: firewallEventsAdaptiveGroups(limit: 1, filter: { datetime_geq: $since, datetime_lt: $until, action_in: ["block","challenge","managed_challenge","js_challenge"] }) { count }
  } }
}
```
Avledet:
- 4xx/5xx-andel = sum(status 400–499 / 500–599) / total
- Fra hurtiglager = cacheStatus ∈ {hit, stale, revalidated, updating}; treffrate = hit/total; båndbredde spart = cachede bytes / alle bytes
- NXDOMAIN-andel = responseCode NXDOMAIN / total DNS
- Sidevisninger ≈ forespørsler med content-type html (`edgeResponseContentTypeName: "html"`); unike besøkende ≈ `sum.visits` (Cloudflare har ikke ekte unike i adaptive groups på gratisplan)
- Merk: adaptive datasett er begrenset bakover (ofte 1–8 dager avhengig av plan); se `settings { httpRequestsAdaptiveGroups { maxDuration notOlderThan } }`

### Historikk (7 d / 30 d / 90 d / 1 år)
Bruk `httpRequests1dGroups` (dimensions { date }, sum { requests bytes cachedRequests pageViews threats countryMap { clientCountryName requests } }, uniq { uniques }) for daglige punkter. Der API-et ikke rekker tilbake: lagre ett punkt per dag lokalt i Room (som i originalen: «ett punkt per dag»). 90 d vises som 45 søyler (2 dager per søyle), 1 år som 52 uker.

### Feilhåndtering
GraphQL-feil per felt (f.eks. «zone … does not have access to the path») skal ikke velte hele skjermen: vis «—» på berørt kort, errorContainer-banner i fanen, og «Feil i spørring»-chip på hero. Statuslinje teller ok/feilede spørringer.

## Datamodell (forslag)
```kotlin
data class Domain(val id: String, val name: String, val zoneId: String) // token i Keystore under id
data class DailyPoint(val domainId: String, val date: LocalDate, val visits: Long, val requests: Long, val bytes: Long,
  val pageViews: Long, val uniques: Long, val p95Ms: Double?, val cacheHitRate: Double?, val dnsQueries: Long?, val blocked: Long?)
data class Snapshot(val totals: Totals, val hourly: List<Pair<Instant, Long>>, val countries: List<CountryStat>,
  val pages: List<PageStat>, val perf: Perf, val cache: Cache, val dns: Dns?, val errors: List<String>)
```

## Interaksjoner
- Bytt domene → last data for valgt domene (cache først, deretter nett)
- Pull-to-refresh + refresh-knapp
- Faneskift nullstiller bottom sheet
- Periodevalg deles mellom fane-historikk og bottom sheet
- Valgt domene, fane og periode lagres i DataStore


## Endringer siden første handoff
- **Bunnnavigasjon** (erstatter topp-faner): ShortNavigationBar, valgt pill #FFD9E2 med mørkt fylt ikon #3F0020, pill animerer bredde 32→64dp med fjær
- **Verdenskart (Besøk)**: ekte kart (Natural Earth / world-atlas 110m, equirectangular, Antarktis utelatt). Choropleth: land med trafikk i secondary (#A0CAFD, alpha 0,35–0,85 etter andel), valgt land primary #FFB1C8, øvrige #4A4752, grense 0,3dp i bakgrunnsfarge. Bobler oppå, trykk på land eller boble velger. Bundle GeoJSON i assets (ikke hent fra nett), tegn i Compose Canvas
- **Domenemeny**: DropdownMenu med 28dp hjørner, overskrift «DOMENER», hver rad: 40dp avatar med forbokstav (ulike MaterialShapes per domene), navn + «N besøk · 24 t», valgt rad primaryContainer #7B2949 med hake, rader morfer hjørner 12→20dp ved trykk. Nederst tonal pill-knapp «Legg til domene»
- **Range-knapper** («Siste 24 t / I dag»): ikke tekstbryting (maxLines = 1)
