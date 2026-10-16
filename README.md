# Cloudflare Analytics (Android)

Native Android-app (Kotlin, Jetpack Compose, Material 3) som viser Cloudflare-analyse for ett eller flere domener.
Designgrunnlag: [`docs/handoff.md`](docs/handoff.md) og [`docs/design.dc.html`](docs/design.dc.html).

## Bygg

```sh
./gradlew assembleDebug        # krever Android SDK (compileSdk 35) og JDK 17+
```

APK-en havner i `app/build/outputs/apk/debug/`. GitHub Actions (`.github/workflows/android.yml`) bygger den også og laster den opp som artefakt.

## Oppsett i appen

Legg til domene → Domene, Zone ID (32 hex) og en API-token med **Zone → Analytics: Read** (+ Zone: Read).
Tokenen testes mot `/user/tokens/verify` og en liten GraphQL-spørring, og lagres kryptert med en nøkkel i Android Keystore.

## Struktur

| Fil | Innhold |
|---|---|
| `data/CloudflareApi.kt` | Ktor-klient, GraphQL-spørringer (HTTP, ytelse, DNS, brannmur, daglig historikk) |
| `data/Repository.kt` | Henter og avleder tall (4xx/5xx-andel, hurtiglager, NXDOMAIN …), lagrer ett punkt per dag i Room |
| `data/Storage.kt` | DataStore (domener, valgt domene/fane/periode) og Keystore-kryptert token-lager |
| `data/Db.kt` | Room: daglige punkter og cache av siste snapshot |
| `work/RefreshWorker.kt` | WorkManager, oppdaterer alle domener hvert 30. min |
| `ui/` | Compose-skjermer: hero-kort, faner, stat-kort, bottom sheet, verdenskart, dialoger |

Hver spørring kjøres for seg, så en feil (f.eks. manglende DNS-tilgang) gir «—» på berørte kort, et feilbanner i fanen og «Feil i spørring» på hero-kortet, uten å velte resten.

## Signert release-APK

`release`-jobben i GitHub Actions bygger `app-release.apk` signert med din egen nøkkel. Legg inn disse under
**Settings → Secrets and variables → Actions**:

| Secret | Innhold |
|---|---|
| `KEYSTORE_BASE64` | keystore-filen base64-kodet (`base64 -w0 release.jks`) |
| `KEYSTORE_PASSWORD` | passord til keystore |
| `KEY_ALIAS` | nøkkelalias (`cfanalytics`) |
| `KEY_PASSWORD` | passord til nøkkelen |

Lokalt: `KEYSTORE_PATH=/sti/release.jks KEYSTORE_PASSWORD=… KEY_ALIAS=cfanalytics KEY_PASSWORD=… ./gradlew assembleRelease`.
Ta vare på keystore-filen – uten den kan ikke nye versjoner installeres som oppdatering. Den skal aldri sjekkes inn.
