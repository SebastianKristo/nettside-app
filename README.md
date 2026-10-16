# Cloudflare Analytics (Android + iOS)

Native apper som viser Cloudflare-analyse for ett eller flere domener.

```
/android   Kotlin, Jetpack Compose, Material 3
/ios       Swift 6, SwiftUI, iOS 26 (Liquid Glass) – prosjektet genereres med XcodeGen
/shared    queries/*.graphql, countries.json (verdenskart), metrics.md (utregninger)
/design    designfiler og handoff-beskrivelser
```

## Bygg Android

```sh
cd android
./gradlew assembleDebug        # krever Android SDK (compileSdk 35) og JDK 17+
```

APK-en havner i `android/app/build/outputs/apk/debug/`. GitHub Actions (`.github/workflows/build.yml`) bygger den også og laster den opp som artefakt.

## Oppsett i appen

Legg til domene → Domene, Zone ID (32 hex) og en API-token med **Zone → Analytics: Read** (+ Zone: Read).
Tokenen testes mot `/user/tokens/verify` og en liten GraphQL-spørring, og lagres kryptert med en nøkkel i Android Keystore.

## Struktur

| Fil | Innhold |
|---|---|
| `android/…/data/CloudflareApi.kt` | Ktor-klient, GraphQL-spørringer (HTTP, ytelse, DNS, brannmur, daglig historikk) |
| `android/…/data/Repository.kt` | Henter og avleder tall (4xx/5xx-andel, hurtiglager, NXDOMAIN …), lagrer ett punkt per dag i Room |
| `android/…/data/Storage.kt` | DataStore (domener, valgt domene/fane/periode) og Keystore-kryptert token-lager |
| `android/…/data/Db.kt` | Room: daglige punkter og cache av siste snapshot |
| `android/…/work/RefreshWorker.kt` | WorkManager, oppdaterer alle domener hvert 30. min |
| `android/…/ui/` | Compose-skjermer: hero-kort, faner, stat-kort, bottom sheet, verdenskart, dialoger |

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

## Bygg iOS

Krever macOS med Xcode 26 og [XcodeGen](https://github.com/yonaskolb/XcodeGen):

```sh
cd ios
brew install xcodegen
xcodegen generate
open CloudflareAnalytics.xcodeproj
```

Legg inn din Team ID i `ios/project.yml` (`DEVELOPMENT_TEAM`) én gang – da overlever den `xcodegen generate` – og kjør på telefonen (gratis Apple-ID gir 7-dagers signering).
GitHub Actions bygger også en usignert `CloudflareAnalytics-unsigned.ipa` (artefakt `ios-unsigned-ipa`) som kan sideloades med Sideloadly eller AltStore, som signerer den med din Apple-ID.
