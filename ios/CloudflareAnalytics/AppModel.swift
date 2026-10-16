import BackgroundTasks
import Foundation
import Observation
import SwiftData

@MainActor
@Observable
final class AppModel {
    nonisolated static let refreshTaskId = "no.kristo.cfanalytics.refresh"

    var domains: [Domain] = Prefs.domains
    var selectedId: String? = UserDefaults.standard.string(forKey: "selectedDomain") {
        didSet { UserDefaults.standard.set(selectedId, forKey: "selectedDomain") }
    }
    var tab: Int = UserDefaults.standard.integer(forKey: "tab") {
        didSet { UserDefaults.standard.set(tab, forKey: "tab"); detail = nil }
    }
    var range: TimeRange = TimeRange(rawValue: UserDefaults.standard.string(forKey: "range") ?? "") ?? .last24h {
        didSet { UserDefaults.standard.set(range.rawValue, forKey: "range"); Task { await load(network: true) } }
    }
    var period: Period = Period(rawValue: UserDefaults.standard.string(forKey: "period") ?? "") ?? .d30 {
        didSet { UserDefaults.standard.set(period.rawValue, forKey: "period") }
    }

    var snapshot: Snapshot?
    var history: [Metric: [String: Double]] = [:]
    var loading = false
    var toast: String?
    var detail: Metric?
    var selectedCountry = 0

    private let fetcher = Fetcher()
    private let context: ModelContext

    init(container: ModelContainer) {
        context = ModelContext(container)
    }

    var selected: Domain? { domains.first { $0.id == selectedId } ?? domains.first }

    func select(_ d: Domain) {
        selectedId = d.id
        Task { await load(network: true) }
    }

    /// Cache first, then network.
    func load(network: Bool, announce: Bool = false) async {
        guard let domain = selected else { snapshot = nil; history = [:]; return }
        snapshot = Prefs.snapshot(domain.id, range)
        history = loadHistory(domain.id)
        selectedCountry = 0
        guard network else { return }
        loading = true
        defer { loading = false }
        do {
            let s = try await refresh(domain, range: range)
            guard selected?.id == domain.id else { return }
            snapshot = s
            history = loadHistory(domain.id)
            if announce { show("Data oppdatert fra Cloudflare") }
        } catch {
            show("Kunne ikke oppdatere: \(error.localizedDescription)")
        }
    }

    /// Fetches live numbers and daily history, and stores both.
    @discardableResult
    func refresh(_ domain: Domain, range: TimeRange) async throws -> Snapshot {
        guard let token = Keychain.get(domain.id) else { throw APIError(message: "Mangler API-token for \(domain.name)") }
        let s = try await fetcher.snapshot(domain: domain, token: token, range: range)
        Prefs.setSnapshot(s, domain.id, range)
        if let days = try? await fetcher.daily(domain: domain, token: token) {
            days.forEach { upsert(domain.id, $0) }
        }
        // Today's point comes from the "I dag" numbers; from the 24 h view only rates/latency are kept.
        let today = isoDay(Date())
        for (m, v) in s.values where range == .today || m.aggregate == .max {
            upsert(domain.id, PointValue(date: today, metric: m, value: v))
        }
        try? context.save()
        return s
    }

    private func upsert(_ domainId: String, _ p: PointValue) {
        context.insert(DailyPoint(domainId: domainId, date: p.date, metric: p.metric.rawValue, value: p.value))
    }

    private func loadHistory(_ domainId: String) -> [Metric: [String: Double]] {
        let from = isoDay(Calendar.current.date(byAdding: .day, value: -Period.y1.days - 1, to: Date())!)
        let desc = FetchDescriptor<DailyPoint>(predicate: #Predicate { $0.domainId == domainId && $0.date >= from })
        var out: [Metric: [String: Double]] = [:]
        for p in (try? context.fetch(desc)) ?? [] {
            if let m = Metric(rawValue: p.metric) { out[m, default: [:]][p.date] = p.value }
        }
        return out
    }

    func refreshAll() async {
        for d in domains {
            _ = try? await refresh(d, range: .today)
            _ = try? await refresh(d, range: .last24h)
        }
    }

    func show(_ text: String) {
        toast = text
        Task {
            try? await Task.sleep(for: .seconds(2.5))
            if toast == text { toast = nil }
        }
    }

    // MARK: domains

    func addDomain(name: String, zoneId: String, token: String) async throws {
        let d = name.trimmingCharacters(in: .whitespaces).lowercased()
        let z = zoneId.trimmingCharacters(in: .whitespaces).lowercased()
        let t = token.trimmingCharacters(in: .whitespaces)
        guard d.wholeMatch(of: /[a-z0-9-]+(\.[a-z0-9-]+)+/) != nil else { throw APIError(message: "Ugyldig domenenavn") }
        guard z.wholeMatch(of: /[0-9a-f]{32}/) != nil else { throw APIError(message: "Zone ID må være 32 heksadesimale tegn") }
        guard !t.isEmpty else { throw APIError(message: "Mangler API-token") }
        try await fetcher.api.verify(token: t)
        try await fetcher.ping(zoneId: z, token: t)
        let domain = Domain(id: UUID().uuidString, name: d, zoneId: z)
        Keychain.set(t, for: domain.id)
        domains.append(domain)
        Prefs.domains = domains
        select(domain)
        show("\(d) lagt til")
    }

    func remove(_ domain: Domain) {
        domains.removeAll { $0.id == domain.id }
        Prefs.domains = domains
        Keychain.delete(domain.id)
        Prefs.removeSnapshots(domain.id)
        let id = domain.id
        try? context.delete(model: DailyPoint.self, where: #Predicate { $0.domainId == id })
        try? context.save()
        if selectedId == domain.id { selectedId = domains.first?.id }
        Task { await load(network: false) }
    }

    // MARK: background refresh (~30 min)

    nonisolated static func scheduleRefresh() {
        let req = BGAppRefreshTaskRequest(identifier: refreshTaskId)
        req.earliestBeginDate = Date(timeIntervalSinceNow: 30 * 60)
        try? BGTaskScheduler.shared.submit(req)
    }
}
