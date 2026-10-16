import SwiftUI

enum Palette {
    static let accent = Color(red: 1, green: 0.31, blue: 0.60)       // #FF4F9A
    static let green = Color(red: 0.20, green: 0.78, blue: 0.35)     // #34C759
    static let pink = Color(red: 1, green: 0.69, blue: 0.78)         // #FFB1C8
    static let blue = Color(red: 0.63, green: 0.79, blue: 0.99)      // #A0CAFD
    static let amber = Color(red: 0.95, green: 0.74, blue: 0.43)     // #F3BD6E
    static let chartGreen = Color(red: 0.56, green: 0.84, blue: 0.63) // #8FD6A0
    static let purple = Color(red: 0.79, green: 0.71, blue: 1)       // #C9B6FF
}

struct StatSpec: Identifiable {
    var metric: Metric
    var label: String
    var icon: String
    var unit: String
    var color: Color
    var id: Metric { metric }
}

struct ProgressSpec: Identifiable {
    var metric: Metric
    var label: String
    var color: Color
    var id: Metric { metric }
}

struct TabSpec {
    var title: String
    var icon: String
    var stats: [StatSpec]
    var progress: [ProgressSpec] = []
    var map = false
    var charts: [StatSpec]
    /// Error sections (see Fetcher) that belong on this tab.
    var sections: Set<String>
}

private let visits = StatSpec(metric: .visits, label: "Besøk", icon: "person.2.fill", unit: "besøk", color: Palette.pink)
private let requests = StatSpec(metric: .requests, label: "Forespørsler", icon: "arrow.up.arrow.down", unit: "", color: Palette.blue)
private let bytes = StatSpec(metric: .bytesMB, label: "Dataoverføring", icon: "icloud.and.arrow.down", unit: "MB", color: Palette.amber)
private let pageViews = StatSpec(metric: .pageViews, label: "Sidevisninger", icon: "eye", unit: "visninger", color: Palette.pink)
private let uniques = StatSpec(metric: .uniques, label: "Unike besøkende", icon: "person.fill", unit: "besøkende", color: Palette.purple)
private let p95 = StatSpec(metric: .p95, label: "Svartid p95", icon: "timer", unit: "ms", color: Palette.amber)
private let hitRate = StatSpec(metric: .cacheHitRate, label: "Hurtiglager-treffrate", icon: "bolt.fill", unit: "%", color: Palette.chartGreen)
private let dns = StatSpec(metric: .dnsQueries, label: "DNS-oppslag", icon: "server.rack", unit: "oppslag", color: Palette.blue)

let tabs: [TabSpec] = [
    TabSpec(title: "Trafikk", icon: "chart.bar.xaxis",
            stats: [visits, requests, bytes,
                    StatSpec(metric: .countries, label: "Land", icon: "globe", unit: "", color: Palette.chartGreen)],
            charts: [visits, requests, bytes], sections: ["Trafikk"]),
    TabSpec(title: "Besøk", icon: "person.2",
            stats: [pageViews, uniques], map: true,
            charts: [pageViews, uniques], sections: ["Trafikk"]),
    TabSpec(title: "Ytelse", icon: "gauge.with.needle",
            stats: [
                StatSpec(metric: .p50, label: "Svartid p50", icon: "timer", unit: "ms", color: Palette.chartGreen), p95,
                StatSpec(metric: .rate4xx, label: "4xx-andel", icon: "exclamationmark.triangle", unit: "%", color: Palette.amber),
                StatSpec(metric: .rate5xx, label: "5xx-andel", icon: "xmark.octagon", unit: "%", color: .red),
                StatSpec(metric: .cachedRequests, label: "Fra hurtiglager", icon: "bolt.fill", unit: "forespørsler", color: Palette.chartGreen),
                StatSpec(metric: .cachedMB, label: "Data fra hurtiglager", icon: "checkmark.icloud", unit: "MB", color: Palette.blue),
            ],
            progress: [ProgressSpec(metric: .cacheHitRate, label: "Treffrate", color: Palette.chartGreen),
                       ProgressSpec(metric: .bandwidthSaved, label: "Båndbredde spart", color: Palette.blue)],
            charts: [p95, hitRate], sections: ["Trafikk", "Svartid"]),
    TabSpec(title: "Sikkerhet", icon: "shield",
            stats: [dns, StatSpec(metric: .blocked, label: "Stoppede", icon: "shield.fill", unit: "forespørsler", color: .red)],
            progress: [ProgressSpec(metric: .nxdomainRate, label: "NXDOMAIN-andel", color: Palette.amber)],
            charts: [dns], sections: ["DNS-oppslag", "Stoppede forespørsler"]),
]

/// Liquid Glass card; falls back to a material when Reduce Transparency is on.
struct GlassCard: ViewModifier {
    var radius: CGFloat = 24
    var interactive = false
    @Environment(\.accessibilityReduceTransparency) private var reduce

    func body(content: Content) -> some View {
        if reduce {
            content.background(.regularMaterial, in: .rect(cornerRadius: radius))
        } else {
            content.glassEffect(interactive ? .regular.interactive() : .regular, in: .rect(cornerRadius: radius))
        }
    }
}

extension View {
    func glassCard(_ radius: CGFloat = 24, interactive: Bool = false) -> some View {
        modifier(GlassCard(radius: radius, interactive: interactive))
    }
}
