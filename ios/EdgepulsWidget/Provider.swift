import AppIntents
import WidgetKit

// MARK: - Domain selection (per widget)

struct DomainEntity: AppEntity {
    let id: String
    let name: String
    let zoneId: String

    static let typeDisplayRepresentation: TypeDisplayRepresentation = "Domene"
    static let defaultQuery = DomainQuery()
    var displayRepresentation: DisplayRepresentation { DisplayRepresentation(title: "\(name)") }

    init(_ d: Domain) { id = d.id; name = d.name; zoneId = d.zoneId }
}

struct DomainQuery: EntityQuery {
    func entities(for identifiers: [String]) async throws -> [DomainEntity] {
        Prefs.domains.filter { identifiers.contains($0.id) }.map(DomainEntity.init)
    }

    func suggestedEntities() async throws -> [DomainEntity] { Prefs.domains.map(DomainEntity.init) }

    func defaultResult() async -> DomainEntity? { Prefs.domains.first.map(DomainEntity.init) }
}

struct SelectDomainIntent: WidgetConfigurationIntent {
    static let title: LocalizedStringResource = "Velg domene"
    static let description = IntentDescription("Hvilket domene widgeten viser.")

    @Parameter(title: "Domene")
    var domain: DomainEntity?
}

// MARK: - Timeline

struct StatsEntry: TimelineEntry {
    let date: Date
    let domain: Domain?
    let snapshot: Snapshot?
    /// Visits in the 24 h before the current window, for «mot i går».
    let previousVisits: Double?
    /// True when the numbers come from cache because the fetch failed.
    let stale: Bool

    static let placeholder = StatsEntry(
        date: .now,
        domain: Domain(id: "x", name: "kristo.no", zoneId: ""),
        snapshot: Snapshot(
            fetchedAt: .now,
            values: [.visits: 12_400, .requests: 48_000, .bytesMB: 3_200, .cachedRequests: 41_760, .blocked: 142],
            hourly: [20, 24, 22, 30, 28, 36, 34, 40, 38, 44],
            countries: [CountryStat(code: "NO", visits: 6_100), CountryStat(code: "SE", visits: 2_300), CountryStat(code: "US", visits: 1_800)],
            pages: [], okQueries: 4, failedQueries: 0, errors: []
        ),
        previousVisits: 11_480,
        stale: false
    )
}

struct Provider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> StatsEntry { .placeholder }

    func snapshot(for configuration: SelectDomainIntent, in context: Context) async -> StatsEntry {
        context.isPreview ? .placeholder : await entry(for: configuration)
    }

    func timeline(for configuration: SelectDomainIntent, in context: Context) async -> Timeline<StatsEntry> {
        let e = await entry(for: configuration)
        return Timeline(entries: [e], policy: .after(Date(timeIntervalSinceNow: 30 * 60)))
    }

    private func entry(for configuration: SelectDomainIntent) async -> StatsEntry {
        let domains = Prefs.domains
        guard let domain = domains.first(where: { $0.id == configuration.domain?.id }) ?? domains.first else {
            return StatsEntry(date: .now, domain: nil, snapshot: nil, previousVisits: nil, stale: false)
        }
        let cached = Prefs.snapshot(domain.id, .last24h)
        guard let token = Keychain.get(domain.id) else {
            return StatsEntry(date: .now, domain: domain, snapshot: cached, previousVisits: nil, stale: true)
        }
        let fetcher = Fetcher()
        do {
            let s = try await fetcher.snapshot(domain: domain, token: token, range: .last24h)
            Prefs.setSnapshot(s, domain.id, .last24h)
            let now = Date()
            let prev = try? await fetcher.visits(domain: domain, token: token,
                                                 since: now.addingTimeInterval(-2 * 86_400), until: now.addingTimeInterval(-86_400))
            return StatsEntry(date: .now, domain: domain, snapshot: s, previousVisits: prev, stale: false)
        } catch {
            return StatsEntry(date: .now, domain: domain, snapshot: cached, previousVisits: nil, stale: true)
        }
    }
}
