import SwiftData
import SwiftUI

@main
struct CloudflareAnalyticsApp: App {
    private let container: ModelContainer
    @State private var model: AppModel
    @Environment(\.scenePhase) private var scenePhase

    init() {
        let container = try! ModelContainer(for: DailyPoint.self)
        self.container = container
        _model = State(initialValue: AppModel(container: container))
    }

    var body: some Scene {
        let model = model
        return WindowGroup {
            ContentView()
                .environment(model)
                .preferredColorScheme(.dark)
        }
        .modelContainer(container)
        .onChange(of: scenePhase) { _, phase in
            if phase == .background { AppModel.scheduleRefresh() }
        }
        .backgroundTask(.appRefresh(AppModel.refreshTaskId)) {
            AppModel.scheduleRefresh()
            await model.refreshAll()
        }
    }
}
