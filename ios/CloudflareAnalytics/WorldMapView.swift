import SwiftUI

/// One country from shared/countries.json (world-atlas 110m, Antarctica left out).
/// `rings` are flat [lon, lat, lon, lat, …] arrays.
struct CountryShape: Decodable, Sendable {
    let code: String
    let name: String
    let lat: Double
    let lon: Double
    let rings: [[Double]]
}

enum WorldData {
    static let countries: [CountryShape] = {
        guard let url = Bundle.main.url(forResource: "countries", withExtension: "json"),
              let data = try? Data(contentsOf: url) else { return [] }
        return (try? JSONDecoder().decode([CountryShape].self, from: data)) ?? []
    }()

    static let byCode: [String: CountryShape] = Dictionary(uniqueKeysWithValues: countries.map { ($0.code, $0) })
}

func countryName(_ code: String) -> String {
    WorldData.byCode[code]?.name ?? nbLocale.localizedString(forRegionCode: code) ?? code
}

/// Equirectangular projection matching the design: lon −180…180, lat 78…−60.
private func project(_ lon: Double, _ lat: Double, _ size: CGSize) -> CGPoint {
    CGPoint(x: (lon + 180) / 360 * size.width, y: (78 - lat) / 138 * size.height)
}

private func path(_ shape: CountryShape, _ size: CGSize) -> Path {
    var p = Path()
    for ring in shape.rings where ring.count >= 6 {
        p.move(to: project(ring[0], ring[1], size))
        for i in stride(from: 2, to: ring.count - 1, by: 2) { p.addLine(to: project(ring[i], ring[i + 1], size)) }
        p.closeSubpath()
    }
    return p
}

/// Choropleth world map: countries with traffic in accent pink with opacity by share, selected one in full colour.
struct WorldMapView: View {
    var countries: [CountryStat]
    @Binding var selected: Int
    var accent: Color

    var body: some View {
        GeometryReader { geo in
            let maxV = max(countries.map(\.visits).max() ?? 1, 1)
            let index = Dictionary(countries.enumerated().map { ($1.code, ($0, $1.visits)) }, uniquingKeysWith: { a, _ in a })
            Canvas { ctx, size in
                for shape in WorldData.countries {
                    let p = path(shape, size)
                    let fill: Color = if let hit = index[shape.code] {
                        hit.0 == selected ? accent : accent.opacity(0.35 + hit.1 / maxV * 0.5)
                    } else {
                        .white.opacity(0.18)
                    }
                    ctx.fill(p, with: .color(fill))
                    ctx.stroke(p, with: .color(.white.opacity(0.35)), lineWidth: 0.3)
                }
            }
            .contentShape(Rectangle())
            .onTapGesture { loc in
                for shape in WorldData.countries where path(shape, geo.size).contains(loc, eoFill: true) {
                    if let hit = index[shape.code] { selected = hit.0 }
                    return
                }
            }
        }
        .aspectRatio(360.0 / 138.0, contentMode: .fit)
    }
}
