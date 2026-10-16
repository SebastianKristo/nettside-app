# Cloudflare Analytics – iOS (Liquid Glass) – prompt til Claude Code

Bygg en iOS-app i samme repo som Android-appen (`/ios` ved siden av `/android`). Referansedesign: `Cloudflare Analytics iOS.dc.html`. Funksjoner, faner, datamodell og Cloudflare GraphQL-spørringer er identiske med `README.md` – del gjerne spørringene som `.graphql`-filer i `/shared`.

## Stack
- Swift 6, SwiftUI, iOS 26 (Liquid Glass), Xcode 26
- Bygg/installer via Xcode (gratis Apple-ID gir 7-dagers signering) eller sideload (AltStore/Sideloadly) fra .ipa
- Nettverk: URLSession + async/await, Codable
- Lagring: SwiftData for daglige historikkpunkter, Keychain for API-tokens (én per domene), @AppStorage for valgt domene/fane/periode
- Bakgrunnsoppdatering: BGAppRefreshTask (~30 min), WidgetKit-widget valgfritt
- Grafer: Swift Charts (BarMark, AreaMark+LineMark for sparkline)
- Kart: MapKit `Map` med `Annotation` per land (boble ∝ besøk) eller prikk-kart i Canvas som i designet
- Språk: nb (String Catalog), tallformat `.formatted(.number.locale(Locale(identifier: "nb_NO")))`

## Liquid Glass – bruk systemets API, ikke hjemmelagd blur
- `TabView` med 4 `Tab`: Trafikk (chart.bar.xaxis), Besøk (person.2), Ytelse (gauge.with.needle), Sikkerhet (shield) – får glass-tabbar automatisk; `.tabBarMinimizeBehavior(.onScrollDown)`
- `NavigationStack` med stor tittel = fanenavn; toolbar: domene-`Menu` (venstre), refresh + «+» (høyre) – glass-knapper automatisk
- Kort: `.glassEffect(.regular, in: .rect(cornerRadius: 24))`, grupper i `GlassEffectContainer`; hero-kort radius 28
- Segmentvalg: `Picker(...).pickerStyle(.segmented)` (Siste 24 t / I dag, 7 d / 30 d / 90 d / 1 år)
- Stat-detalj: `.sheet` med `.presentationDetents([.medium, .large])` – glass-sheet fra systemet
- Legg til domene: `.sheet` eller `.alert` med TextField (domene, Zone ID) + SecureField (token); valider som i README, test token mot `/user/tokens/verify`
- Toast etter oppdatering: liten glass-kapsel øverst
- Bakgrunn: farget mesh/gradient bak innholdet (`MeshGradient`, rosa/blå/lilla/oransje) slik at glasset har noe å bryte; respekter Reduce Transparency (fall tilbake til `.regularMaterial`)
- Alle elementer (kort, rader, knapper, inputfelt, søyler, sheets, toast) bruker `.glassEffect`; små/trykkbare `.glassEffect(.regular.interactive())`
- Innstillinger (tannhjul i toolbar → sheet): bryter «Farget bakgrunn» (@AppStorage("coloredBackground"); av = svart systembakgrunn), «Legg til domene»
- Tabbar: valgt-indikator er en glass-«linse» som kan dras mellom faner (forstørres/strekkes under drag, spretter til nærmeste fane). Systemets TabView i iOS 26 gjør dette selv – ikke lag egen tabbar
- Ikoner: SF Symbols (designet bruker Material Symbols som plassholder)

## Fargeaksenter
Accent #FF4F9A · grønn #34C759 · diagram: rosa #FFB1C8, blå #A0CAFD, amber #F3BD6E, grønn #8FD6A0, lilla #C9B6FF · feil: systemRed

## Repo-struktur
```
/android   Kotlin/Compose (README.md)
/ios       SwiftUI-app (CloudflareAnalytics.xcodeproj)
/shared    queries/*.graphql, metrics.md (utregninger), countries.json (ISO-kode → navn, lat/lon)
/design    *.dc.html
```


## Endringer siden første handoff
- Alle elementer i Liquid Glass (`.glassEffect`, interaktive for trykkbare)
- Innstillinger-sheet: «Farget bakgrunn» av/på (@AppStorage), «Legg til domene»
- Tabbar med dragbar glass-linse (systemets TabView i iOS 26)
- Verdenskart: MapKit `Map` (style .imagery/.standard mørk) med `MapPolygon`/`Annotation`, eller GeoJSON (world-atlas 110m) i Canvas; land med trafikk i rosa #FF4F9A med alpha etter andel, valgt land full farge


## Bugfix: sparkline i hero-kortet
Problem: grafen tegnes utenfor kortets avrundede hjørner (fyllet stikker ut nede til høyre/venstre), og linjen starter i 0 nede i venstre hjørne.
Fix:
```swift
VStack(alignment: .leading) { header; numbers
  Chart(points) { p in
    AreaMark(x: .value("t", p.date), y: .value("v", p.value))
      .foregroundStyle(.linearGradient(colors: [.pink.opacity(0.35), .clear], startPoint: .top, endPoint: .bottom))
      .interpolationMethod(.monotone)
    LineMark(x: .value("t", p.date), y: .value("v", p.value))
      .foregroundStyle(.pink).lineStyle(.init(lineWidth: 2)).interpolationMethod(.monotone)
  }
  .chartXAxis(.hidden).chartYAxis(.hidden).chartLegend(.hidden)
  .chartYScale(domain: 0...(maxValue * 1.15))
  .chartPlotStyle { $0.padding(.zero) }
  .frame(height: 72)
}
.padding(.top, 18).padding(.horizontal, 18)          // graf full bredde nederst
.clipShape(.rect(cornerRadius: 28))                   // klipp grafen til kortformen
.glassEffect(.regular, in: .rect(cornerRadius: 28))
```
- Bruk `.monotone` (ikke `.catmullRom`) så kurven ikke overskyter under 0 eller over toppen
- Filtrer bort første punkt hvis det mangler data (hindrer at linjen starter i 0), eller bruk siste 24 timer med kun fullførte timer
- Gradient-fyll som falmer til klart, ikke flat farge
