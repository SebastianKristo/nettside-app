import SwiftUI

struct ContentView: View {
    @Environment(AppModel.self) private var model
    @AppStorage("coloredBackground") private var coloredBackground = true
    @State private var showAdd = false
    @State private var showSettings = false

    var body: some View {
        @Bindable var model = model
        TabView(selection: $model.tab) {
            ForEach(Array(tabs.enumerated()), id: \.offset) { i, spec in
                Tab(spec.title, systemImage: spec.icon, value: i) {
                    NavigationStack {
                        Group {
                            if model.domains.isEmpty { EmptyStateView(onAdd: { showAdd = true }) }
                            else { TabScreen(spec: spec) }
                        }
                        .navigationTitle(spec.title)
                        .toolbar { toolbar }
                        .background { BackgroundView(colored: coloredBackground) }
                    }
                }
            }
        }
        .tabBarMinimizeBehavior(.onScrollDown)
        .tint(Palette.accent)
        .overlay(alignment: .top) { ToastView(text: model.toast) }
        .sheet(isPresented: $showAdd) { AddDomainSheet() }
        .sheet(isPresented: $showSettings) { SettingsSheet(onAdd: { showSettings = false; showAdd = true }) }
        .sheet(item: Binding(get: { model.detail.map(DetailID.init) }, set: { model.detail = $0?.metric })) { d in
            if let spec = tabs[model.tab].stats.first(where: { $0.metric == d.metric }) {
                DetailSheet(spec: spec).presentationDetents([.medium, .large])
            }
        }
        .task { await model.load(network: true) }
    }

    @ToolbarContentBuilder
    private var toolbar: some ToolbarContent {
        ToolbarItem(placement: .topBarLeading) {
            Menu {
                ForEach(model.domains) { d in
                    Button { model.select(d) } label: {
                        Label(d.name, systemImage: d.id == model.selected?.id ? "checkmark" : "globe")
                    }
                }
                Divider()
                Button { showAdd = true } label: { Label("Legg til domene", systemImage: "plus") }
            } label: {
                Label(model.selected?.name ?? "Legg til domene", systemImage: "globe").labelStyle(.titleAndIcon)
            }
        }
        ToolbarItemGroup(placement: .topBarTrailing) {
            Button { Task { await model.load(network: true, announce: true) } } label: {
                Image(systemName: "arrow.clockwise")
                    .symbolEffect(.rotate, isActive: model.loading)
            }
            .disabled(model.selected == nil || model.loading)
            Button { showAdd = true } label: { Image(systemName: "plus") }
            Button { showSettings = true } label: { Image(systemName: "gearshape") }
        }
    }
}

private struct DetailID: Identifiable {
    var metric: Metric
    var id: Metric { metric }
}

struct BackgroundView: View {
    var colored: Bool

    var body: some View {
        if colored {
            MeshGradient(width: 3, height: 3, points: [
                [0, 0], [0.5, 0], [1, 0],
                [0, 0.5], [0.6, 0.4], [1, 0.5],
                [0, 1], [0.5, 1], [1, 1],
            ], colors: [
                Palette.accent, .black, Color(red: 0.24, green: 0.48, blue: 1),
                .black, Color(red: 0.48, green: 0.24, blue: 1).opacity(0.8), .black,
                Color(red: 0.48, green: 0.24, blue: 1), .black, Color(red: 1, green: 0.6, blue: 0.24),
            ])
            .opacity(0.55)
            .background(.black)
            .ignoresSafeArea()
        } else {
            Color.black.ignoresSafeArea()
        }
    }
}

struct ToastView: View {
    var text: String?

    var body: some View {
        if let text {
            Text(text)
                .font(.subheadline.weight(.medium))
                .padding(.horizontal, 16).padding(.vertical, 10)
                .glassEffect(.regular, in: .capsule)
                .padding(.top, 8)
                .transition(.move(edge: .top).combined(with: .opacity))
        }
    }
}

struct EmptyStateView: View {
    var onAdd: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "globe").font(.system(size: 48)).foregroundStyle(Palette.accent)
            Text("Ingen domener ennå").font(.title2.weight(.semibold))
            Text("Legg til et domene med Zone ID og en API-token med Analytics: Read for å komme i gang.")
                .multilineTextAlignment(.center).foregroundStyle(.secondary)
            Button("Legg til domene", systemImage: "plus", action: onAdd)
                .buttonStyle(.glassProminent)
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
