import SwiftUI

struct DetailSheet: View {
    let spec: StatSpec
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        @Bindable var model = model
        let chart = buildChart(series(model.history, spec.metric), spec.metric, model.period)
        let d = spec.metric.decimals
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack(spacing: 12) {
                    Image(systemName: spec.icon).foregroundStyle(spec.color)
                        .frame(width: 40, height: 40).glassEffect(.regular, in: .circle)
                    VStack(alignment: .leading) {
                        Text(spec.label).font(.title3)
                        Text(model.selected?.name ?? "").font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    Button { dismiss() } label: { Image(systemName: "xmark") }
                        .buttonStyle(.glass)
                }
                Picker("Periode", selection: $model.period) {
                    ForEach(Period.allCases) { Text($0.label).tag($0) }
                }
                .pickerStyle(.segmented)
                HStack(alignment: .lastTextBaseline, spacing: 6) {
                    Text(chart.hasData ? nf(chart.total, d) : "—").font(.system(size: 44, weight: .light))
                    Text("\(spec.unit) totalt".trimmingCharacters(in: .whitespaces)).foregroundStyle(.secondary)
                }
                if !chart.hasData {
                    Text("Ingen historikk ennå – ett punkt lagres per dag.").font(.footnote).foregroundStyle(.secondary)
                }
                BarChartView(values: chart.bars, color: spec.color, height: 180)
                HStack {
                    Text(startLabel(chart.start, model.period))
                    Spacer()
                    Text("I dag")
                }
                .font(.caption2).foregroundStyle(.secondary)
                HStack(spacing: 8) {
                    fact("Snitt", chart.avgPerDay, chart.hasData, d)
                    fact("Høyeste", chart.max, chart.hasData, d)
                    fact("Laveste", chart.min, chart.hasData, d)
                }
            }
            .padding(20)
        }
    }

    private func fact(_ k: String, _ v: Double, _ has: Bool, _ d: Int) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(k).font(.caption).foregroundStyle(.secondary)
            Text(has ? nf(v, d) : "—").font(.title3)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassCard(12)
    }
}

struct AddDomainSheet: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @State private var domain = ""
    @State private var zone = ""
    @State private var token = ""
    @State private var error: String?
    @State private var busy = false

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("eksempel.no", text: $domain)
                        .textInputAutocapitalization(.never).keyboardType(.URL).autocorrectionDisabled()
                    TextField("Zone ID", text: $zone)
                        .textInputAutocapitalization(.never).autocorrectionDisabled().font(.body.monospaced())
                    SecureField("API-token", text: $token).font(.body.monospaced())
                } footer: {
                    Text("Hvert domene bruker sin egen Cloudflare API-token med tilgang til Analytics (lesetilgang).")
                }
                if busy {
                    HStack { ProgressView(); Text("Tester token…") }
                } else if let error {
                    Text(error).foregroundStyle(.red).font(.footnote)
                }
            }
            .scrollContentBackground(.hidden)
            .navigationTitle("Legg til domene")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Avbryt") { dismiss() }.disabled(busy) }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Legg til") {
                        busy = true; error = nil
                        Task {
                            do {
                                try await model.addDomain(name: domain, zoneId: zone, token: token)
                                dismiss()
                            } catch {
                                self.error = error.localizedDescription
                            }
                            busy = false
                        }
                    }
                    .disabled(busy)
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}

struct SettingsSheet: View {
    var onAdd: () -> Void
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @AppStorage("coloredBackground") private var coloredBackground = true

    var body: some View {
        NavigationStack {
            Form {
                Section { Toggle("Farget bakgrunn", isOn: $coloredBackground) }
                Section("Domener") {
                    ForEach(model.domains) { d in
                        VStack(alignment: .leading) {
                            Text(d.name)
                            Text(d.zoneId).font(.caption2.monospaced()).foregroundStyle(.secondary)
                        }
                    }
                    .onDelete { idx in idx.map { model.domains[$0] }.forEach(model.remove) }
                    Button("Legg til domene", systemImage: "plus", action: onAdd)
                }
            }
            .navigationTitle("Innstillinger")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Ferdig") { dismiss() } } }
        }
        .presentationDetents([.medium, .large])
    }
}
