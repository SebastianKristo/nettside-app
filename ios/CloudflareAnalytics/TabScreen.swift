import SwiftUI

struct TabScreen: View {
    let spec: TabSpec
    @Environment(AppModel.self) private var model

    var body: some View {
        @Bindable var model = model
        ScrollView {
            GlassEffectContainer {
                VStack(alignment: .leading, spacing: 12) {
                    HeroCard(snapshot: model.snapshot, range: model.range)

                    Picker("Periode", selection: $model.range) {
                        ForEach(TimeRange.allCases) { Text($0.label).tag($0) }
                    }
                    .pickerStyle(.segmented)
                    .padding(.top, 4)

                    LazyVGrid(columns: [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8)], spacing: 8) {
                        ForEach(spec.stats) { s in StatCard(spec: s, snapshot: model.snapshot) { model.detail = s.metric } }
                    }

                    ForEach(spec.progress) { p in ProgressCard(spec: p, value: model.snapshot?.values[p.metric]) }

                    if spec.map, let snap = model.snapshot {
                        CountriesCard(snapshot: snap, selected: $model.selectedCountry)
                        PagesCard(pages: snap.pages)
                    }

                    Text("Historikk").font(.title2.weight(.semibold)).padding(.top, 12)
                    Picker("Historikk", selection: $model.period) {
                        ForEach(Period.allCases) { Text($0.label).tag($0) }
                    }
                    .pickerStyle(.segmented)
                    ForEach(spec.charts) { c in HistoryCard(spec: c, history: model.history, period: model.period) }

                    let errs = model.snapshot?.errors.filter { spec.sections.contains($0.section) } ?? []
                    if !errs.isEmpty {
                        Label("Feil i spørring: \(errs.map { "\($0.section): \($0.message)" }.joined(separator: " · ")). Sjekk API-tilgangen i Cloudflare.",
                              systemImage: "exclamationmark.circle.fill")
                            .font(.footnote)
                            .foregroundStyle(.white)
                            .padding(14)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .glassEffect(.regular.tint(.red.opacity(0.5)), in: .rect(cornerRadius: 16))
                    }

                    FooterView(snapshot: model.snapshot)
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 24)
            }
        }
        .refreshable { await model.load(network: true, announce: true) }
    }
}

struct HeroCard: View {
    var snapshot: Snapshot?
    var range: TimeRange

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 8) {
                Circle().fill(Palette.green).frame(width: 8, height: 8)
                Text(range == .today ? "Live · i dag" : "Live · siste 24 timer").font(.footnote).foregroundStyle(.secondary)
                Spacer()
                if (snapshot?.failedQueries ?? 0) > 0 {
                    Text("Feil i spørring").font(.caption).padding(.horizontal, 8).padding(.vertical, 4)
                        .glassEffect(.regular.tint(.red.opacity(0.5)), in: .capsule)
                }
            }
            HStack(alignment: .lastTextBaseline, spacing: 20) {
                value(snapshot?.text(.visits) ?? "—", "besøk", big: true)
                value(snapshot?.text(.requests) ?? "—", "forespørsler")
                value(snapshot?.text(.bytesMB, decimals: 0) ?? "—", "MB")
            }
            .padding(.top, 4)
            SparklineView(points: snapshot?.hourly ?? [], color: Palette.pink)
                .frame(height: 72)
                .padding(.horizontal, -20)
                .padding(.top, 8)
        }
        .padding([.horizontal, .top], 20)
        .clipped()
        .glassCard(28)
    }

    private func value(_ v: String, _ label: String, big: Bool = false) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(v).font(big ? .system(size: 52, weight: .light) : .title2).monospacedDigit()
                .lineLimit(1).minimumScaleFactor(0.5)
            Text(label).font(.caption).foregroundStyle(.secondary)
        }
    }
}

struct StatCard: View {
    var spec: StatSpec
    var snapshot: Snapshot?
    var onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 6) {
                    Image(systemName: spec.icon).foregroundStyle(spec.color)
                    Text(spec.label).lineLimit(1)
                    Spacer(minLength: 0)
                    Image(systemName: "chevron.right").font(.caption2)
                }
                .font(.caption)
                .foregroundStyle(.secondary)
                HStack(alignment: .lastTextBaseline, spacing: 4) {
                    Text(snapshot?.text(spec.metric) ?? "—").font(.system(size: 30)).monospacedDigit()
                        .lineLimit(1).minimumScaleFactor(0.5)
                    Text(spec.unit).font(.caption).foregroundStyle(.secondary)
                }
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .glassCard(16, interactive: true)
    }
}

struct ProgressCard: View {
    var spec: ProgressSpec
    var value: Double?

    var body: some View {
        VStack(spacing: 12) {
            HStack {
                Text(spec.label)
                Spacer()
                Text(value.map { "\(nf($0, 2)) %" } ?? "—").fontWeight(.medium)
            }
            .font(.subheadline)
            ProgressView(value: min(max((value ?? 0) / 100, 0), 1)).tint(spec.color)
        }
        .padding(16)
        .glassCard(16)
    }
}

struct CountriesCard: View {
    var snapshot: Snapshot
    @Binding var selected: Int

    var body: some View {
        let countries = snapshot.countries
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("Besøk per land").font(.headline)
                Spacer()
                Text("Trykk på et land").font(.caption).foregroundStyle(.secondary)
            }
            WorldMapView(countries: countries, selected: $selected, accent: Palette.pink)
            if countries.indices.contains(selected) {
                let c = countries[selected]
                let total = max(snapshot.values[.visits] ?? countries.reduce(0) { $0 + $1.visits }, 1)
                HStack(spacing: 12) {
                    Image(systemName: "mappin.circle.fill").font(.title2).foregroundStyle(Palette.pink)
                    VStack(alignment: .leading) {
                        Text(countryName(c.code)).font(.subheadline.weight(.medium))
                        Text("\(nf(c.visits / total * 100, 1)) % av all trafikk").font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    Text(nf(c.visits)).font(.title2)
                }
                .padding(.horizontal, 14).padding(.vertical, 10)
                .glassCard(12)
            }
            let m = max(countries.map(\.visits).max() ?? 1, 1)
            ForEach(Array(countries.enumerated()), id: \.offset) { i, c in
                BarRow(label: c.code, value: c.visits, max: m, color: Palette.blue, labelWidth: 32)
                    .contentShape(.rect)
                    .onTapGesture { selected = i }
            }
        }
        .padding(16)
        .glassCard(24)
    }
}

struct PagesCard: View {
    var pages: [PageStat]

    var body: some View {
        if !pages.isEmpty {
            let m = max(pages.map(\.count).max() ?? 1, 1)
            VStack(alignment: .leading, spacing: 10) {
                Text("Mest besøkte sider").font(.headline)
                ForEach(pages, id: \.path) { p in
                    BarRow(label: p.path, value: p.count, max: m, color: Palette.pink, labelWidth: 96, mono: true)
                }
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .glassCard(24)
        }
    }
}

struct BarRow: View {
    var label: String
    var value: Double
    var max: Double
    var color: Color
    var labelWidth: CGFloat
    var mono = false

    var body: some View {
        HStack(spacing: 12) {
            Text(label).font(mono ? .footnote.monospaced() : .footnote).foregroundStyle(.secondary)
                .lineLimit(1).frame(width: labelWidth, alignment: .leading)
            GeometryReader { g in
                ZStack(alignment: .leading) {
                    Capsule().fill(.white.opacity(0.12))
                    Capsule().fill(color).frame(width: Swift.max(6, g.size.width * value / max))
                }
            }
            .frame(height: 6)
            Text(nf(value)).font(.footnote).frame(width: 48, alignment: .trailing)
        }
    }
}

struct HistoryCard: View {
    var spec: StatSpec
    var history: [Metric: [String: Double]]
    var period: Period

    var body: some View {
        let chart = buildChart(series(history, spec.metric), spec.metric, period)
        let d = spec.metric.decimals
        VStack(spacing: 8) {
            HStack(alignment: .lastTextBaseline) {
                Text(spec.label).font(.subheadline).foregroundStyle(.secondary)
                Spacer()
                Text(chart.hasData ? nf(chart.total, d) : "—").font(.title2)
                Text(spec.unit).font(.caption).foregroundStyle(.secondary)
            }
            BarChartView(values: chart.bars, color: spec.color, height: 88)
            HStack {
                Text(startLabel(chart.start, period))
                Spacer()
                Text(spec.metric.aggregate == .sum ? "snitt \(nf(chart.avgPerDay, d)) / dag" : "maks \(nf(chart.max, d))")
                Spacer()
                Text("I dag")
            }
            .font(.caption2)
            .foregroundStyle(.secondary)
        }
        .padding(16)
        .glassCard(24)
    }
}

struct FooterView: View {
    var snapshot: Snapshot?

    var body: some View {
        let failed = snapshot?.failedQueries ?? 0
        Label(failed > 0 ? "Cloudflare · \(snapshot?.okQueries ?? 0) ok · \(failed) feil · oppdateres hvert 30. min"
                         : "Cloudflare · spørringer ok · oppdateres hvert 30. min",
              systemImage: failed > 0 ? "exclamationmark.circle.fill" : "checkmark.circle.fill")
            .font(.caption)
            .foregroundStyle(.secondary)
            .padding(.top, 4)
    }
}
