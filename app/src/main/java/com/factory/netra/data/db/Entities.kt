package com.factory.netra.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.TypeConverter

enum class DeviceType { NVR, CAMERA }

enum class Severity(val label: String) {
    HIGH("High"), MEDIUM("Medium"), LOW("Low"), INFO("Info")
}

enum class AlertSource { AI, RECORDER }

enum class AlertType(val title: String) {
    RESTRICTED_AREA("Restricted area entry"),
    AFTER_HOURS("Movement after hours"),
    CROWD("Crowd gathering"),
    LINE_IDLE("Workstation unattended"),
    TRIPWIRE("Tripwire crossed"),
    INTRUSION("Zone intrusion"),
    TAMPERING("Camera tampering"),
    VIDEO_LOSS("Video signal lost"),
    STORAGE("Recorder storage problem"),
    ABANDONED_OBJECT("Object left behind"),
    MISSING_OBJECT("Object removed"),
    HUMAN_MOTION("Person detected by camera"),
    VEHICLE_MOTION("Vehicle detected by camera"),
}

@Entity(tableName = "devices", indices = [Index(value = ["host", "httpPort"], unique = true)])
data class DeviceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: DeviceType,
    val host: String,
    val httpPort: Int = 80,
    val rtspPort: Int = 554,
    val username: String,
    val passwordEnc: String,
    val model: String = "",
    val serial: String = "",
    val channelCount: Int = 1,
    val online: Boolean = false,
    val lastSeen: Long = 0,
)

@Entity(
    tableName = "cameras",
    foreignKeys = [
        ForeignKey(
            entity = DeviceEntity::class,
            parentColumns = ["id"],
            childColumns = ["deviceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("deviceId"), Index(value = ["deviceId", "channel"], unique = true)],
)
data class CameraEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceId: Long,
    /** 1-based channel number (NVR channel, or 1 for a direct camera). */
    val channel: Int,
    val name: String,
    val zone: String = "Unassigned",
    val enabled: Boolean = true,
    val aiEnabled: Boolean = true,
    /** Any person here = alert (fabric store, finished-goods store, server room...). */
    val restricted: Boolean = false,
    /** Alert when no operator is seen at this line/workstation during working hours. */
    val monitorIdle: Boolean = false,
    val online: Boolean = true,
    val lastPeople: Int = 0,
    val lastAnalyzedAt: Long = 0,
)

data class CameraWithDevice(
    @Embedded val camera: CameraEntity,
    @Relation(parentColumn = "deviceId", entityColumn = "id")
    val device: DeviceEntity,
)

@Entity(tableName = "alerts", indices = [Index("cameraId"), Index("createdAt")])
data class AlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cameraId: Long?,
    val cameraName: String,
    val zone: String,
    val type: AlertType,
    val severity: Severity,
    val source: AlertSource,
    val message: String,
    val snapshotPath: String?,
    val createdAt: Long,
    val acknowledged: Boolean = false,
)

class Converters {
    @TypeConverter fun deviceTypeTo(v: DeviceType): String = v.name
    @TypeConverter fun deviceTypeFrom(v: String): DeviceType = DeviceType.valueOf(v)
    @TypeConverter fun severityTo(v: Severity): String = v.name
    @TypeConverter fun severityFrom(v: String): Severity = Severity.valueOf(v)
    @TypeConverter fun sourceTo(v: AlertSource): String = v.name
    @TypeConverter fun sourceFrom(v: String): AlertSource = AlertSource.valueOf(v)
    @TypeConverter fun alertTypeTo(v: AlertType): String = v.name
    @TypeConverter fun alertTypeFrom(v: String): AlertType = AlertType.valueOf(v)
}
