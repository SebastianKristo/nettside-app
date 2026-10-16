package no.kristo.cfanalytics.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert

/** One value per domain, day and metric ("ett punkt per dag"). Date is ISO yyyy-MM-dd. */
@Entity(tableName = "daily_point", primaryKeys = ["domainId", "date", "metric"])
data class DailyPoint(val domainId: String, val date: String, val metric: String, val value: Double)

@Entity(tableName = "snapshot_cache", primaryKeys = ["domainId", "range"])
data class SnapshotCache(val domainId: String, val range: String, val json: String)

@Dao
interface AnalyticsDao {
    @Upsert
    suspend fun upsertPoints(points: List<DailyPoint>)

    @Query("SELECT * FROM daily_point WHERE domainId = :domainId AND date >= :from")
    suspend fun points(domainId: String, from: String): List<DailyPoint>

    @Upsert
    suspend fun upsertSnapshot(cache: SnapshotCache)

    @Query("SELECT * FROM snapshot_cache WHERE domainId = :domainId AND range = :range")
    suspend fun snapshot(domainId: String, range: String): SnapshotCache?

    @Query("DELETE FROM daily_point WHERE domainId = :domainId")
    suspend fun deletePoints(domainId: String)

    @Query("DELETE FROM snapshot_cache WHERE domainId = :domainId")
    suspend fun deleteSnapshots(domainId: String)
}

@Database(entities = [DailyPoint::class, SnapshotCache::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AnalyticsDao

    companion object {
        fun create(context: Context): AppDb =
            Room.databaseBuilder(context, AppDb::class.java, "analytics.db").build()
    }
}
