import Foundation

struct APIError: LocalizedError, Sendable {
    let message: String
    var errorDescription: String? { message }
}

/// Minimal Sendable JSON tree for walking GraphQL responses.
enum JSON: Decodable, Sendable {
    case object([String: JSON]), array([JSON]), number(Double), string(String), bool(Bool), null

    init(from decoder: Decoder) throws {
        let c = try decoder.singleValueContainer()
        if c.decodeNil() { self = .null }
        else if let v = try? c.decode(Bool.self) { self = .bool(v) }
        else if let v = try? c.decode(Double.self) { self = .number(v) }
        else if let v = try? c.decode(String.self) { self = .string(v) }
        else if let v = try? c.decode([JSON].self) { self = .array(v) }
        else { self = .object(try c.decode([String: JSON].self)) }
    }

    subscript(key: String) -> JSON? { if case .object(let o) = self { o[key] } else { nil } }
    func path(_ keys: String...) -> JSON? { keys.reduce(Optional(self)) { $0?[$1] } }
    var rows: [JSON] { if case .array(let a) = self { a } else { [] } }
    var double: Double? {
        switch self {
        case .number(let n): n
        case .string(let s): Double(s)
        default: nil
        }
    }
    var string: String? {
        switch self {
        case .string(let s): s
        case .number(let n): String(Int(n))
        default: nil
        }
    }
}

struct CloudflareAPI: Sendable {
    private let base = URL(string: "https://api.cloudflare.com/client/v4")!

    static func query(_ name: String) -> String {
        let url = Bundle.main.url(forResource: name, withExtension: "graphql", subdirectory: "queries")
            ?? Bundle.main.url(forResource: name, withExtension: "graphql")
        return url.flatMap { try? String(contentsOf: $0, encoding: .utf8) } ?? ""
    }

    /// GET /user/tokens/verify — succeeds only for an active token.
    func verify(token: String) async throws {
        var req = URLRequest(url: base.appending(path: "user/tokens/verify"))
        req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        let json = try await send(req)
        let ok = { if case .bool(true) = json["success"] { true } else { false } }()
        if !ok || json.path("result", "status")?.string != "active" {
            throw APIError(message: firstError(json) ?? "Token er ugyldig eller ikke aktiv")
        }
    }

    /// Runs one GraphQL query and returns the first zone object.
    func zoneQuery(token: String, query: String, variables: [String: String]) async throws -> JSON {
        var req = URLRequest(url: base.appending(path: "graphql"))
        req.httpMethod = "POST"
        req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONSerialization.data(withJSONObject: ["query": query, "variables": variables])
        let json = try await send(req)
        if let e = firstError(json) { throw APIError(message: e) }
        guard let zone = json.path("data", "viewer", "zones")?.rows.first else {
            throw APIError(message: "Fant ikke sonen – sjekk Zone ID og token")
        }
        return zone
    }

    private func send(_ req: URLRequest) async throws -> JSON {
        let data: Data
        do { (data, _) = try await URLSession.shared.data(for: req) }
        catch { throw APIError(message: "Nettverksfeil: \(error.localizedDescription)") }
        guard let json = try? JSONDecoder().decode(JSON.self, from: data) else {
            throw APIError(message: "Uventet svar fra Cloudflare")
        }
        return json
    }

    private func firstError(_ json: JSON) -> String? {
        json["errors"]?.rows.compactMap { $0["message"]?.string }.first
    }
}

/// One stored value per domain, day and metric.
struct PointValue: Sendable { var date: String; var metric: Metric; var value: Double }

/// Fetches and derives all numbers (same rules as the Android app, see shared/queries).
struct Fetcher: Sendable {
    let api = CloudflareAPI()
    private static let mb = 1_000_000.0
    private static let cachedStatuses: Set<String> = ["hit", "stale", "revalidated", "updating"]

    func snapshot(domain: Domain, token: String, range: TimeRange) async throws -> Snapshot {
        let until = Date()
        let since = range == .today ? Calendar.current.startOfDay(for: until) : until.addingTimeInterval(-86_400)
        let f = ISO8601DateFormatter()
        let vars = ["zone": domain.zoneId, "since": f.string(from: since), "until": f.string(from: until)]

        async let http = result { try await api.zoneQuery(token: token, query: CloudflareAPI.query("http"), variables: vars) }
        async let perf = result { try await api.zoneQuery(token: token, query: CloudflareAPI.query("perf"), variables: vars) }
        async let dns = result { try await api.zoneQuery(token: token, query: CloudflareAPI.query("dns"), variables: vars) }
        async let fw = result { try await api.zoneQuery(token: token, query: CloudflareAPI.query("firewall"), variables: vars) }

        var values: [Metric: Double] = [:]
        var errors: [QueryError] = []
        var hourly: [Double] = [], countries: [CountryStat] = [], pages: [PageStat] = []

        switch await http {
        case .success(let z):
            let totals = z["totals"]?.rows.first
            values[.requests] = totals?["count"]?.double ?? 0
            values[.bytesMB] = (totals?.path("sum", "edgeResponseBytes")?.double ?? 0) / Self.mb
            values[.visits] = totals?.path("sum", "visits")?.double ?? 0
            values[.uniques] = values[.visits]
            values[.pageViews] = z["pageviews"]?.rows.first?["count"]?.double ?? 0
            hourly = z["hourly"]?.rows.map { $0.path("sum", "visits")?.double ?? 0 } ?? []
            let all = (z["countries"]?.rows ?? []).compactMap { r -> CountryStat? in
                guard let code = r.path("dimensions", "clientCountryName")?.string, !code.isEmpty, code != "XX", code != "T1" else { return nil }
                let v = r.path("sum", "visits")?.double ?? 0
                return v > 0 ? CountryStat(code: code, visits: v) : nil
            }
            values[.countries] = Double(all.count)
            countries = Array(all.prefix(10))
            pages = (z["pages"]?.rows ?? []).compactMap { r in
                r.path("dimensions", "clientRequestPath")?.string.map { PageStat(path: $0, count: r["count"]?.double ?? 0) }
            }
            let status = (z["status"]?.rows ?? []).map { (Int($0.path("dimensions", "edgeResponseStatus")?.double ?? 0), $0["count"]?.double ?? 0) }
            let st = status.reduce(0) { $0 + $1.1 }
            values[.rate4xx] = st > 0 ? status.filter { (400...499).contains($0.0) }.reduce(0) { $0 + $1.1 } / st * 100 : 0
            values[.rate5xx] = st > 0 ? status.filter { (500...599).contains($0.0) }.reduce(0) { $0 + $1.1 } / st * 100 : 0
            let cache = (z["cache"]?.rows ?? []).map { ($0.path("dimensions", "cacheStatus")?.string ?? "", $0["count"]?.double ?? 0, $0.path("sum", "edgeResponseBytes")?.double ?? 0) }
            let ct = cache.reduce(0) { $0 + $1.1 }, cb = cache.reduce(0) { $0 + $1.2 }
            let cached = cache.filter { Self.cachedStatuses.contains($0.0) }
            values[.cachedRequests] = cached.reduce(0) { $0 + $1.1 }
            values[.cachedMB] = cached.reduce(0) { $0 + $1.2 } / Self.mb
            values[.cacheHitRate] = ct > 0 ? cache.filter { $0.0 == "hit" }.reduce(0) { $0 + $1.1 } / ct * 100 : 0
            values[.bandwidthSaved] = cb > 0 ? cached.reduce(0) { $0 + $1.2 } / cb * 100 : 0
        case .failure(let e): errors.append(QueryError(section: "Trafikk", message: e.localizedDescription))
        }

        switch await perf {
        case .success(let z):
            let q = z["perf"]?.rows.first?["quantiles"]
            if let v = q?["edgeTimeToFirstByteMsP50"]?.double { values[.p50] = v }
            if let v = q?["edgeTimeToFirstByteMsP95"]?.double { values[.p95] = v }
        case .failure(let e): errors.append(QueryError(section: "Svartid", message: e.localizedDescription))
        }

        switch await dns {
        case .success(let z):
            let rows = (z["dns"]?.rows ?? []).map { ($0.path("dimensions", "responseCode")?.string ?? "", $0["count"]?.double ?? 0) }
            let t = rows.reduce(0) { $0 + $1.1 }
            values[.dnsQueries] = t
            values[.nxdomainRate] = t > 0 ? rows.filter { $0.0 == "NXDOMAIN" }.reduce(0) { $0 + $1.1 } / t * 100 : 0
        case .failure(let e): errors.append(QueryError(section: "DNS-oppslag", message: e.localizedDescription))
        }

        switch await fw {
        case .success(let z): values[.blocked] = z["firewall"]?.rows.first?["count"]?.double ?? 0
        case .failure(let e): errors.append(QueryError(section: "Stoppede forespørsler", message: e.localizedDescription))
        }

        if errors.count == 4 { throw APIError(message: errors[0].message) }
        return Snapshot(fetchedAt: Date(), values: values, hourly: hourly, countries: countries, pages: pages,
                        okQueries: 4 - errors.count, failedQueries: errors.count, errors: errors)
    }

    /// Daily points from httpRequests1dGroups for the last year.
    func daily(domain: Domain, token: String) async throws -> [PointValue] {
        let to = Date()
        let from = Calendar.current.date(byAdding: .day, value: -Period.y1.days, to: to)!
        let z = try await api.zoneQuery(token: token, query: CloudflareAPI.query("daily"),
                                        variables: ["zone": domain.zoneId, "from": isoDay(from), "to": isoDay(to)])
        return (z["days"]?.rows ?? []).flatMap { row -> [PointValue] in
            guard let date = row.path("dimensions", "date")?.string else { return [] }
            let req = row.path("sum", "requests")?.double, cached = row.path("sum", "cachedRequests")?.double
            let pairs: [(Metric, Double?)] = [
                (.requests, req),
                (.bytesMB, row.path("sum", "bytes")?.double.map { $0 / Self.mb }),
                (.pageViews, row.path("sum", "pageViews")?.double),
                (.uniques, row.path("uniq", "uniques")?.double),
                (.cachedRequests, cached),
                (.cachedMB, row.path("sum", "cachedBytes")?.double.map { $0 / Self.mb }),
                (.blocked, row.path("sum", "threats")?.double),
                (.cacheHitRate, (req ?? 0) > 0 ? (cached ?? 0) / req! * 100 : nil),
            ]
            return pairs.compactMap { m, v in v.map { PointValue(date: date, metric: m, value: $0) } }
        }
    }

    func ping(zoneId: String, token: String) async throws {
        let f = ISO8601DateFormatter()
        let now = Date()
        _ = try await api.zoneQuery(token: token, query: CloudflareAPI.query("ping"),
                                    variables: ["zone": zoneId, "since": f.string(from: now.addingTimeInterval(-3600)), "until": f.string(from: now)])
    }

    private func result(_ op: @Sendable () async throws -> JSON) async -> Result<JSON, Error> {
        do { return .success(try await op()) } catch { return .failure(error) }
    }
}
