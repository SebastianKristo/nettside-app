import Foundation
import Security
import SwiftData

/// One value per domain, day and metric ("ett punkt per dag").
@Model
final class DailyPoint {
    @Attribute(.unique) var key: String
    var domainId: String
    var date: String
    var metric: String
    var value: Double

    init(domainId: String, date: String, metric: String, value: Double) {
        self.key = "\(domainId)|\(date)|\(metric)"
        self.domainId = domainId
        self.date = date
        self.metric = metric
        self.value = value
    }
}

/// API tokens in the Keychain, one per domain id. New items land in the first keychain-access-group
/// in the entitlements (shared with the widget), so no explicit access group is needed here.
enum Keychain {
    private static let service = "no.kristo.cfanalytics.token"

    static func set(_ token: String, for id: String) {
        delete(id)
        let q: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: id,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            kSecValueData as String: Data(token.utf8),
        ]
        SecItemAdd(q as CFDictionary, nil)
    }

    static func get(_ id: String) -> String? {
        let q: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: id,
            kSecReturnData as String: true,
        ]
        var out: AnyObject?
        guard SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess, let d = out as? Data else { return nil }
        return String(data: d, encoding: .utf8)
    }

    static func delete(_ id: String) {
        let q: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: id,
        ]
        SecItemDelete(q as CFDictionary)
    }
}

/// Domain list and cached snapshots, in the App Group so the widget can read them.
enum Prefs {
    static let appGroup = "group.no.kristo.edgepuls"
    private static var d: UserDefaults { UserDefaults(suiteName: appGroup) ?? .standard }

    /// Moves data saved before the App Group existed, and re-saves tokens into the shared Keychain group.
    static func migrateIfNeeded() {
        guard d.data(forKey: "domains") == nil, let old = UserDefaults.standard.data(forKey: "domains") else { return }
        d.set(old, forKey: "domains")
        for domain in domains {
            for r in TimeRange.allCases {
                let key = "snap.\(domain.id).\(r.rawValue)"
                if let v = UserDefaults.standard.data(forKey: key) { d.set(v, forKey: key) }
            }
            if let token = Keychain.get(domain.id) { Keychain.set(token, for: domain.id) }
        }
    }

    static var domains: [Domain] {
        get { d.data(forKey: "domains").flatMap { try? JSONDecoder().decode([Domain].self, from: $0) } ?? [] }
        set { d.set(try? JSONEncoder().encode(newValue), forKey: "domains") }
    }

    static func snapshot(_ domainId: String, _ range: TimeRange) -> Snapshot? {
        d.data(forKey: "snap.\(domainId).\(range.rawValue)").flatMap { try? JSONDecoder().decode(Snapshot.self, from: $0) }
    }

    static func setSnapshot(_ s: Snapshot, _ domainId: String, _ range: TimeRange) {
        d.set(try? JSONEncoder().encode(s), forKey: "snap.\(domainId).\(range.rawValue)")
    }

    static func removeSnapshots(_ domainId: String) {
        TimeRange.allCases.forEach { d.removeObject(forKey: "snap.\(domainId).\($0.rawValue)") }
    }
}
