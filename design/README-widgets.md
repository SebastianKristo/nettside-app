# Widgets + iOS app-ikon — oppgave til Claude Code

Design: `Widgets.dc.html` (åpne i nettleser) (1a ikon, 1b iOS-widgets, 1c Android-widgets). Data hentes med samme GraphQL-spørringer som appen (se README.md), per valgt domene.

## Appnavn
- Visningsnavn: **Edgepuls** (iOS `CFBundleDisplayName`, Android `app_name`)
- Undertittel (App Store): "Trafikk og ytelse for dine Cloudflare-domener"

## iOS app-ikon
- 1024×1024, ingen alfa. Gradient 160°: `#FF9A3C` → `#F2541B` (55 %) → `#C2185B`.
- Glass-pille øverst (inset 12 %, høyde 43 %, radius full, hvit 55→12 % + 1px hvit 60 %-kant).
- Hvit sparkline-polyline (34,128 62,108 86,118 112,86 146,62 i 180-rutenett), strek 12, rund ende, prikk r=10 på siste punkt.
- iOS 18+/26 varianter i `AppIcon.appiconset`: **Dark** (bakgrunn `#1C1C22`, linje `#FF8A3D`) og **Tinted/Clear** (gråtone, hvit linje). Bruk Icon Composer for Liquid Glass-lag.

## iOS widgets (WidgetKit, SwiftUI)
- Ny target `EdgepulsWidget`, App Group `group.no.kristo.edgepuls` for å dele domener + cachet data. API-token i delt Keychain-gruppe, aldri i UserDefaults.
- `AppIntentConfiguration` med `SelectDomainIntent` (velg domene per widget).
- `TimelineProvider`: oppdater hvert 30. min (`.after(now + 30 min)`), fall tilbake til cache ved feil.
- Familier:
  - `.systemSmall` A: domene, besøk 24t (32pt bold), sparkline nederst.
  - `.systemSmall` B: topp 3 land 24t med flagg + antall.
  - `.systemMedium`: venstre besøk + endring mot i går (grønn `#7DFFA6`) + sparkline; høyre 2×2 rutenett (Forespørsler, Båndbredde, Cache %, Trusler).
- Stil: `.containerBackground(for: .widget) { Color.clear }` + `.glassEffect()` på iOS 26; støtt `widgetRenderingMode` `.accented`/`.vibrant` (bruk `.widgetAccentable()` på tall og sparkline).
- Sparkline: samme fiks som i IOS.md — normaliser min/max med padding, klipp til widgetens form, aldri fall til 0 ved manglende punkter.
- Tapp åpner appen via `widgetURL("edgepuls://domain/<zoneId>")`.

## Android widgets (Jetpack Glance, M3 Expressive)
- `GlanceAppWidget` + `GlanceAppWidgetReceiver`, konfig-aktivitet for valg av domene (lagre i DataStore per `appWidgetId`).
- Oppdatering med `WorkManager` periodisk 30 min; vis cache ved feil.
- Bruk `GlanceTheme` med dynamiske farger (Material You); fargene i designet er fallback:
  - 2×2 Besøk: `primaryContainer` (`#FFB68A`/`#331200`), asymmetrisk form (28/28/28/8 dp), tall 44sp ExtraBold.
  - 2×2 Cache: sirkulær form, `tertiaryContainer` (`#4A3C5C`), ikon `bolt`, prosent 34sp.
  - 4×2 Oversikt: `surfaceContainer` (`#2B2930`), header med domene, periode-chip (7d) og refresh-knapp; 7 avrundede stolper (siste i `primary`); 3 metrikk-kort med asymmetriske hjørner.
- `SizeMode.Responsive` med størrelser 2×2 og 4×2; `previewLayout` + generert forhåndsvisning (Android 15).
- Tapp åpner `MainActivity` med domene-extra.

## Felles
- Tallformat: `nb-NO` (12,4k, 3,2 GB, 87 %).
- Tom-tilstand: "Legg til et domene i Edgepuls".
- Feiltilstand: vis siste cache + liten "Oppdatert hh:mm".
- Test mot ekte Cloudflare API med brukerens token før release.
