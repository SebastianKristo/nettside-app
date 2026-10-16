import Charts
import SwiftUI

struct ChartData {
    var bars: [Double]
    var total: Double
    var avgPerDay: Double
    var max: Double
    var min: Double
    var start: Date
    var hasData: Bool
}

/// Daily values for `metric`; visits fall back to uniques on days with no live point stored.
func series(_ history: [Metric: [String: Double]], _ metric: Metric) -> [String: Double] {
    let own = history[metric] ?? [:]
    return metric == .visits ? (history[.uniques] ?? [:]).merging(own) { _, new in new } : own
}

func buildChart(_ series: [String: Double], _ metric: Metric, _ period: Period) -> ChartData {
    let cal = Calendar.current
    let span = period.bars * period.bucketDays
    let start = cal.date(byAdding: .day, value: -(span - 1), to: cal.startOfDay(for: Date()))!
    func value(_ offset: Int) -> Double? { series[isoDay(cal.date(byAdding: .day, value: offset, to: start)!)] }
    let bars = (0..<period.bars).map { b in
        let days = (0..<period.bucketDays).compactMap { value(b * period.bucketDays + $0) }
        return metric.aggregate == .sum ? days.reduce(0, +) : (days.max() ?? 0)
    }
    let daily = (0..<span).compactMap(value)
    return ChartData(
        bars: bars,
        total: metric.aggregate == .sum ? daily.reduce(0, +) : (daily.last ?? 0),
        avgPerDay: daily.isEmpty ? 0 : daily.reduce(0, +) / Double(daily.count),
        max: daily.max() ?? 0,
        min: daily.min() ?? 0,
        start: start,
        hasData: !daily.isEmpty
    )
}

func startLabel(_ date: Date, _ period: Period) -> String {
    if period == .y1 {
        let f = DateFormatter()
        f.locale = nbLocale
        f.dateFormat = "MMM yy"
        return f.string(from: date)
    }
    let c = Calendar.current.dateComponents([.day, .month], from: date)
    return "\(c.day!).\(c.month!)."
}

/// Bars with the last one in full colour and the rest at 70 % opacity.
struct BarChartView: View {
    var values: [Double]
    var color: Color
    var height: CGFloat

    var body: some View {
        Chart(Array(values.enumerated()), id: \.offset) { i, v in
            BarMark(x: .value("i", i), y: .value("v", v))
                .foregroundStyle(i == values.count - 1 ? color : color.opacity(0.7))
                .clipShape(UnevenRoundedRectangle(topLeadingRadius: 3, topTrailingRadius: 3))
        }
        .chartXAxis(.hidden)
        .chartYAxis(.hidden)
        .frame(height: height)
    }
}

struct SparklineView: View {
    var points: [Double]
    var color: Color

    var body: some View {
        Chart(Array(points.enumerated()), id: \.offset) { i, v in
            AreaMark(x: .value("t", i), y: .value("v", v))
                .foregroundStyle(color.opacity(0.18))
                .interpolationMethod(.catmullRom)
            LineMark(x: .value("t", i), y: .value("v", v))
                .foregroundStyle(color)
                .lineStyle(StrokeStyle(lineWidth: 1.5))
                .interpolationMethod(.catmullRom)
        }
        .chartXAxis(.hidden)
        .chartYAxis(.hidden)
    }
}
