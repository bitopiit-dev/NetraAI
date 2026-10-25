package com.factory.netra.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices ORDER BY type, name")
    fun observeAll(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices")
    suspend fun getAll(): List<DeviceEntity>

    @Query("SELECT * FROM devices WHERE id = :id")
    suspend fun get(id: Long): DeviceEntity?

    @Query("SELECT * FROM devices WHERE host = :host LIMIT 1")
    suspend fun findByHost(host: String): DeviceEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(d: DeviceEntity): Long

    @Update suspend fun update(d: DeviceEntity)
    @Delete suspend fun delete(d: DeviceEntity)

    @Query("UPDATE devices SET online = :online, lastSeen = CASE WHEN :online THEN :ts ELSE lastSeen END WHERE id = :id")
    suspend fun setOnline(id: Long, online: Boolean, ts: Long)
}

@Dao
interface CameraDao {
    @Transaction
    @Query("SELECT * FROM cameras ORDER BY zone, name")
    fun observeAll(): Flow<List<CameraWithDevice>>

    @Transaction
    @Query("SELECT * FROM cameras WHERE enabled = 1 AND aiEnabled = 1")
    suspend fun aiTargets(): List<CameraWithDevice>

    @Transaction
    @Query("SELECT * FROM cameras WHERE deviceId = :deviceId AND channel = :channel LIMIT 1")
    suspend fun findByChannel(deviceId: Long, channel: Int): CameraWithDevice?

    @Query("SELECT channel FROM cameras WHERE deviceId = :deviceId")
    suspend fun channelsOf(deviceId: Long): List<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(list: List<CameraEntity>): List<Long>

    @Update suspend fun update(c: CameraEntity)

    @Query("UPDATE cameras SET lastPeople = :people, lastAnalyzedAt = :ts, online = 1 WHERE id = :id")
    suspend fun updateAnalysis(id: Long, people: Int, ts: Long)

    @Query("UPDATE cameras SET online = :online WHERE id = :id")
    suspend fun setOnline(id: Long, online: Boolean)
}

@Dao
interface AlertDao {
    @Query("SELECT * FROM alerts ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alerts WHERE createdAt >= :since ORDER BY createdAt DESC")
    fun observeSince(since: Long): Flow<List<AlertEntity>>

    @Query("SELECT COUNT(*) FROM alerts WHERE acknowledged = 0")
    fun observeUnackCount(): Flow<Int>

    @Insert suspend fun insert(a: AlertEntity): Long

    @Query("UPDATE alerts SET acknowledged = 1 WHERE id = :id")
    suspend fun acknowledge(id: Long)

    @Query("UPDATE alerts SET acknowledged = 1 WHERE acknowledged = 0")
    suspend fun acknowledgeAll()

    @Query("SELECT snapshotPath FROM alerts WHERE createdAt < :before AND snapshotPath IS NOT NULL")
    suspend fun snapshotsBefore(before: Long): List<String>

    @Query("DELETE FROM alerts WHERE createdAt < :before")
    suspend fun deleteBefore(before: Long): Int
}
