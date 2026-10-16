import Charts
import SwiftUI
import WidgetKit

@main
struct EdgepulsWidgets: WidgetBundle {
    var body: some Widget {
        VisitsWidget()
        CountriesWidget()
    }
}

/// Small A (visits + sparkline) and medium (visits, change and a 2×2 grid).
struct VisitsWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "EdgepulsVisits", intent: SelectDomainIntent.self, provider: Provider()) { entry in
            VisitsView(entry: entry)
        }
        .configurationDisplayName("Besøk")
        .description("Besøk siste 24 timer for et domene.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

/// Small B: top 3 countries.
struct CountriesWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "EdgepulsCountries", intent: SelectDomainIntent.self, provider: Provider()) { entry in
            CountriesView(entry: entry)
        }
        .configurationDisplayName("Toppland")
        .description("Landene med flest besøk siste 24 timer.")
        .supportedFamilies([.systemSmall])
    }
}

// MARK: - Views

private let positive = Color(red: 0x7D / 255, green: 1, blue: 0xA6 / 255)

private struct WidgetChrome<Content: View>: View {
    let entry: StatsEntry
    @ViewBuilder var content: () -> Content

    var body: some View {
        Group {
            if entry.domain == nil {
                Text("Legg til et domene i Edgepuls")
                    .font(.footnote.weight(.medium))
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                content()
            }
        }
        .foregroundStyle(.white)
        .containerBackground(for: .widget) { Color.clear }
        .widgetURL(entry.domain.flatMap { URL(string: "edgepuls://domain/\($0.zoneId)") })
    }
}

private struct UpdatedLabel: View {
    let entry: StatsEntry
    var body: some View {
        if entry.stale, let at = entry.snapshot?.fetchedAt {
            Text("Oppdatert \(at.formatted(.dateTime.hour().minute().locale(nbLocale)))")
                .font(.system(size: 9))
                .foregroundStyle(.white.opacity(0.6))
        }
    }
}

struct VisitsView: View {
    let entry: StatsEntry
    @Environment(\.widgetFamily) private var family

    var body: some View {
        WidgetChrome(entry: entry) {
            if family == .systemMedium { medium } else { small }
        }
    }

    private var visits: String { entry.snapshot?.values[.visits].map(compact) ?? "—" }

    private var small: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(entry.domain?.name ?? "").font(.caption).foregroundStyle(.white.opacity(0.8)).lineLimit(1)
            Text(visits).font(.system(size: 32, weight: .bold)).widgetAccentable().padding(.top, 6)
            Text("besøk · 24t").font(.caption).foregroundStyle(.white.opacity(0.75))
            Spacer(minLength: 4)
            WidgetSparkline(points: entry.snapshot?.hourly ?? []).frame(height: 34)
            UpdatedLabel(entry: entry)
        }
    }

    private var change: String? {
        guard let now = entry.snapshot?.values[.visits], let prev = entry.previousVisits, prev > 0 else { return nil }
        let pct = (now - prev) / prev * 100
        return "\(pct >= 0 ? "+" : "−")\(nf(abs(pct))) % mot i går"
    }

    private var medium: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 0) {
                Text("\(entry.domain?.name ?? "") · 24t").font(.caption).foregroundStyle(.white.opacity(0.8)).lineLimit(1)
                Text(visits).font(.system(size: 32, weight: .bold)).widgetAccentable().padding(.top, 6)
                if let change {
                    Text(change).font(.caption).foregroundStyle(change.hasPrefix("+") ? positive : .white.opacity(0.75))
                }
                Spacer(minLength: 4)
                WidgetSparkline(points: entry.snapshot?.hourly ?? []).frame(height: 34)
                UpdatedLabel(entry: entry)
            }
            Grid(horizontalSpacing: 8, verticalSpacing: 8) {
                GridRow {
                    tile("Forespørsler", entry.snapshot?.values[.requests].map(compact))
                    tile("Båndbredde", entry.snapshot?.values[.bytesMB].map { compactBytes(mb: $0) })
                }
                GridRow {
                    tile("Cache", cacheShare)
                    tile("Trusler", entry.snapshot?.values[.blocked].map(compact))
                }
            }
        }
    }

    private var cacheShare: String? {
        guard let s = entry.snapshot, let req = s.values[.requests], req > 0, let c = s.values[.cachedRequests] else { return nil }
        return "\(nf(c / req * 100)) %"
    }

    private func tile(_ label: String, _ value: String?) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(.system(size: 11)).foregroundStyle(.white.opacity(0.75)).lineLimit(1)
            Text(value ?? "—").font(.system(size: 17, weight: .semibold)).widgetAccentable().lineLimit(1).minimumScaleFactor(0.7)
        }
        .padding(8)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .glassEffect(.regular, in: .rect(cornerRadius: 14))
    }
}

struct CountriesView: View {
    let entry: StatsEntry

    var body: some View {
        WidgetChrome(entry: entry) {
            VStack(alignment: .leading, spacing: 8) {
                Text("Toppland · 24t").font(.caption).foregroundStyle(.white.opacity(0.8))
                let top = Array((entry.snapshot?.countries ?? []).prefix(3))
                if top.isEmpty {
                    Text("Ingen data ennå").font(.footnote).foregroundStyle(.white.opacity(0.7))
                }
                ForEach(top, id: \.code) { c in
                    HStack {
                        Text("\(flag(c.code)) \(countryName(c.code))").lineLimit(1)
                        Spacer(minLength: 4)
                        Text(compact(c.visits)).bold().widgetAccentable()
                    }
                    .font(.system(size: 14))
                }
                Spacer(minLength: 0)
                UpdatedLabel(entry: entry)
            }
        }
    }
}

private func countryName(_ code: String) -> String { nbLocale.localizedString(forRegionCode: code) ?? code }

/// Sparkline normalised to min/max with padding, so missing points never drop the line to 0.
struct WidgetSparkline: View {
    let points: [Double]

    var body: some View {
        let data = Array(points.drop { $0 == 0 })
        if data.count >= 2, let lo = data.min(), let hi = data.max() {
            let pad = max((hi - lo) * 0.15, 1)
            Chart(Array(data.enumerated()), id: \.offset) { i, v in
                LineMark(x: .value("t", i), y: .value("v", v))
                    .foregroundStyle(.white)
                    .lineStyle(StrokeStyle(lineWidth: 2.5, lineCap: .round, lineJoin: .round))
                    .interpolationMethod(.monotone)
            }
            .chartXAxis(.hidden)
            .chartYAxis(.hidden)
            .chartLegend(.hidden)
            .chartYScale(domain: (lo - pad)...(hi + pad))
            .chartPlotStyle { $0.padding(.zero) }
            .widgetAccentable()
            .clipShape(.rect(cornerRadius: 6))
        } else {
            Color.clear
        }
    }
}
