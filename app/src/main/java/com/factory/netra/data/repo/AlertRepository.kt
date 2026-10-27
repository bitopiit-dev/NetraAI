package com.factory.netra.data.repo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.factory.netra.data.db.AlertDao
import com.factory.netra.data.db.AlertEntity
import com.factory.netra.data.db.AlertSource
import com.factory.netra.data.db.AlertType
import com.factory.netra.data.db.Severity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class AlertRepository(context: Context, private val dao: AlertDao) {
    private val dir = File(context.filesDir, "alerts").apply { mkdirs() }
    @Volatile private var lastPrune = 0L

    fun observeRecent(limit: Int = 300) = dao.observeRecent(limit)
    fun observeSince(since: Long) = dao.observeSince(since)
    fun observeUnackCount() = dao.observeUnackCount()
    suspend fun acknowledge(id: Long) = dao.acknowledge(id)
    suspend fun acknowledgeAll() = dao.acknowledgeAll()

    suspend fun record(
        cameraId: Long?,
        cameraName: String,
        zone: String,
        type: AlertType,
        severity: Severity,
        source: AlertSource,
        message: String,
        frame: Bitmap?,
        jpeg: ByteArray? = null,
    ): AlertEntity = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val path = runCatching {
            val file = File(dir, "${now}_${cameraId ?: 0}_${type.name.lowercase()}.jpg")
            when {
                frame != null -> file.outputStream().use { frame.compress(Bitmap.CompressFormat.JPEG, 82, it) }
                jpeg != null && BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) != null -> file.writeBytes(jpeg)
                else -> return@runCatching null
            }
            file.absolutePath
        }.getOrNull()
        val entity = AlertEntity(
            cameraId = cameraId, cameraName = cameraName, zone = zone, type = type,
            severity = severity, source = source, message = message,
            snapshotPath = path, createdAt = now,
        )
        entity.copy(id = dao.insert(entity))
    }

    /** Deletes alerts (and their snapshot files) older than [days]; runs at most hourly. */
    suspend fun pruneIfDue(days: Int, force: Boolean = false): Int = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && now - lastPrune < TimeUnit.HOURS.toMillis(1)) return@withContext 0
        lastPrune = now
        val before = now - TimeUnit.DAYS.toMillis(days.toLong())
        dao.snapshotsBefore(before).forEach { runCatching { File(it).delete() } }
        dao.deleteBefore(before)
    }
}
