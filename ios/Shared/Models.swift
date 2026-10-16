import Foundation

struct Domain: Codable, Identifiable, Hashable, Sendable {
    var id: String
    var name: String
    var zoneId: String
}

enum TimeRange: String, CaseIterable, Identifiable, Sendable {
    case last24h, today
    var id: String { rawValue }
    var label: String { self == .today ? "I dag" : "Siste 24 t" }
}

enum Period: String, CaseIterable, Identifiable, Sendable {
    case d7, d30, d90, y1
    var id: String { rawValue }
    var days: Int { [.d7: 7, .d30: 30, .d90: 90, .y1: 364][self]! }
    var bars: Int { [.d7: 7, .d30: 30, .d90: 45, .y1: 52][self]! }
    var bucketDays: Int { days / bars }
    var label: String { [.d7: "7 d", .d30: "30 d", .d90: "90 d", .y1: "1 år"][self]! }
}

enum Aggregate: Sendable { case sum, max }

enum Metric: String, Codable, CaseIterable, Sendable, CodingKeyRepresentable {
    case visits, requests, bytesMB, countries, pageViews, uniques, p50, p95
    case rate4xx, rate5xx, cachedRequests, cachedMB, cacheHitRate, bandwidthSaved
    case dnsQueries, blocked, nxdomainRate

    var aggregate: Aggregate {
        switch self {
        case .countries, .p50, .p95, .rate4xx, .rate5xx, .cacheHitRate, .bandwidthSaved, .nxdomainRate: .max
        default: .sum
        }
    }

    var decimals: Int {
        switch self {
        case .bytesMB, .cachedMB, .rate4xx, .rate5xx, .bandwidthSaved, .nxdomainRate: 2
        case .cacheHitRate: 1
        default: 0
        }
    }
}

struct CountryStat: Codable, Hashable, Sendable { var code: String; var visits: Double }
struct PageStat: Codable, Hashable, Sendable { var path: String; var count: Double }
struct QueryError: Codable, Hashable, Sendable { var section: String; var message: String }

/// Live numbers for one domain and range. A metric missing from `values` means its query failed.
struct Snapshot: Codable, Sendable {
    var fetchedAt: Date
    var values: [Metric: Double]
    var hourly: [Double]
    var countries: [CountryStat]
    var pages: [PageStat]
    var okQueries: Int
    var failedQueries: Int
    var errors: [QueryError]

    func text(_ m: Metric, decimals: Int? = nil) -> String {
        values[m].map { nf($0, decimals ?? m.decimals) } ?? "—"
    }
}

let nbLocale = Locale(identifier: "nb_NO")

/// nb-NO number format: space as thousands separator, comma as decimal.
func nf(_ v: Double, _ decimals: Int = 0) -> String {
    v.formatted(.number.locale(nbLocale).precision(.fractionLength(decimals)))
}

func isoDay(_ date: Date) -> String {
    var cal = Calendar(identifier: .gregorian)
    cal.timeZone = .current
    let c = cal.dateComponents([.year, .month, .day], from: date)
    return String(format: "%04d-%02d-%02d", c.year!, c.month!, c.day!)
}

/// Compact nb-NO numbers for widgets: 12,4k · 3,2 mill.
func compact(_ v: Double) -> String {
    switch abs(v) {
    case 1_000_000...: return "\(nf(v / 1_000_000, 1)) mill."
    case 10_000...: return "\(nf(v / 1_000, 0))k"
    case 1_000...: return "\(nf(v / 1_000, 1))k"
    default: return nf(v)
    }
}

/// Megabytes as MB or GB: 3,2 GB.
func compactBytes(mb: Double) -> String {
    mb >= 1_000 ? "\(nf(mb / 1_000, 1)) GB" : "\(nf(mb, mb < 10 ? 1 : 0)) MB"
}

/// Flag emoji from an ISO 3166 alpha-2 code.
func flag(_ code: String) -> String {
    String(code.uppercased().unicodeScalars.compactMap { UnicodeScalar(127_397 + $0.value) }.map(Character.init))
}
