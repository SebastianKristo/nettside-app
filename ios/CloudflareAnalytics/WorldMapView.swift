import SwiftUI

/// Equirectangular dot matrix (64 × 26): per row, inclusive column ranges that are land.
private let land: [[(Int, Int)]] = [
    [(14, 20), (22, 28), (50, 56)], [(10, 20), (22, 29), (44, 58)], [(3, 20), (23, 28), (33, 36), (40, 63)],
    [(2, 19), (23, 27), (32, 36), (38, 63)], [(5, 8), (10, 21), (31, 32), (33, 62)], [(9, 23), (31, 60)],
    [(10, 24), (31, 58)], [(11, 22), (31, 35), (36, 57)], [(12, 21), (31, 55)], [(13, 20), (29, 54)],
    [(14, 19), (29, 53)], [(15, 19), (29, 40), (44, 48), (49, 52)], [(16, 19), (29, 40), (45, 47), (49, 52)],
    [(18, 20), (28, 41), (50, 51)], [(18, 23), (30, 40), (50, 53)], [(18, 25), (33, 39), (49, 58)],
    [(18, 26), (34, 40), (52, 58)], [(19, 26), (34, 39), (53, 58)], [(20, 25), (34, 38), (39, 39), (52, 59)],
    [(20, 24), (34, 37), (39, 39), (52, 59)], [(20, 23), (34, 37), (52, 59)], [(20, 22), (35, 36), (53, 58)],
    [(20, 22), (56, 57), (61, 62)], [(20, 21), (61, 61)], [(20, 21)],
]

/// Approximate country centroids (lat, lon) by ISO 3166-1 alpha-2.
let centroids: [String: (Double, Double)] = [
    "US": (39, -98), "CA": (56, -106), "MX": (23, -102), "BR": (-10, -53), "AR": (-34, -64), "CL": (-30, -71),
    "CO": (4, -72), "PE": (-10, -76), "NO": (61, 9), "SE": (62, 15), "DK": (56, 10), "FI": (64, 26),
    "IS": (65, -18), "GB": (54, -2), "IE": (53, -8), "FR": (46, 2), "DE": (51, 10), "NL": (52.5, 5.75),
    "BE": (50.8, 4), "CH": (47, 8), "AT": (47.3, 13.3), "IT": (42.8, 12.8), "ES": (40, -4), "PT": (39.5, -8),
    "PL": (52, 20), "CZ": (49.75, 15.5), "HU": (47, 20), "RO": (46, 25), "GR": (39, 22), "TR": (39, 35),
    "UA": (49, 32), "RU": (60, 100), "LT": (56, 24), "LV": (57, 25), "EE": (59, 26), "IL": (31.5, 34.75),
    "SA": (25, 45), "AE": (24, 54), "IR": (32, 53), "EG": (27, 30), "MA": (32, -5), "NG": (10, 8),
    "KE": (1, 38), "ZA": (-29, 24), "IN": (20, 77), "PK": (30, 70), "BD": (24, 90), "CN": (35, 105),
    "JP": (36, 138), "KR": (36, 128), "TW": (23.5, 121), "HK": (22.25, 114.17), "SG": (1.37, 103.8),
    "MY": (2.5, 112.5), "TH": (15, 100), "VN": (16, 106), "PH": (13, 122), "ID": (-5, 120),
    "AU": (-25, 134), "NZ": (-41, 174), "KZ": (48, 68),
]

func countryName(_ code: String) -> String { nbLocale.localizedString(forRegionCode: code) ?? code }

struct WorldMapView: View {
    var countries: [CountryStat]
    @Binding var selected: Int
    var accent: Color

    private func point(_ code: String, _ size: CGSize) -> CGPoint? {
        guard let (lat, lon) = centroids[code] else { return nil }
        return CGPoint(x: (lon + 180) / 360 * size.width, y: (78 - lat) / 138 * size.height)
    }

    private func radius(_ v: Double) -> CGFloat {
        let m = max(countries.map(\.visits).max() ?? 1, 1)
        return (10 + v / m * 16) / 2
    }

    var body: some View {
        GeometryReader { geo in
            Canvas { ctx, size in
                let cw = size.width / 64, ch = size.height / 26
                for (r, ranges) in land.enumerated() {
                    for (a, b) in ranges {
                        for c in a...b {
                            let p = CGPoint(x: (CGFloat(c) + 0.5) * cw, y: (CGFloat(r) + 0.5) * ch)
                            let d = cw * 0.32
                            ctx.fill(Path(ellipseIn: CGRect(x: p.x - d, y: p.y - d, width: d * 2, height: d * 2)), with: .color(.white.opacity(0.25)))
                        }
                    }
                }
                for (i, c) in countries.enumerated() {
                    guard let p = point(c.code, size) else { continue }
                    let r = radius(c.visits)
                    let rect = CGRect(x: p.x - r, y: p.y - r, width: r * 2, height: r * 2)
                    ctx.fill(Path(ellipseIn: rect), with: .color(i == selected ? accent : accent.opacity(0.3)))
                    ctx.stroke(Path(ellipseIn: rect.insetBy(dx: 1, dy: 1)), with: .color(accent), lineWidth: 2)
                }
            }
            .contentShape(Rectangle())
            .onTapGesture { loc in
                let hit = countries.enumerated().compactMap { i, c -> (Int, CGFloat)? in
                    guard let p = point(c.code, geo.size) else { return nil }
                    return (i, hypot(p.x - loc.x, p.y - loc.y) - radius(c.visits))
                }.min { $0.1 < $1.1 }
                if let hit, hit.1 < 12 { selected = hit.0 }
            }
        }
        .aspectRatio(64.0 / 26.0, contentMode: .fit)
    }
}
