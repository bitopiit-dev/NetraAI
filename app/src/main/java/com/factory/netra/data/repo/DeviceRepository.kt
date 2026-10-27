package com.factory.netra.data.repo

import com.factory.netra.dahua.DahuaClient
import com.factory.netra.dahua.Endpoint
import com.factory.netra.data.db.CameraEntity
import com.factory.netra.data.db.CameraWithDevice
import com.factory.netra.data.db.DeviceEntity
import com.factory.netra.data.db.DeviceType
import com.factory.netra.data.db.NetraDatabase
import com.factory.netra.data.security.CryptoBox
import kotlinx.coroutines.flow.Flow

class DeviceRepository(
    private val db: NetraDatabase,
    private val crypto: CryptoBox,
    private val dahua: DahuaClient,
) {
    private val deviceDao = db.deviceDao()
    private val cameraDao = db.cameraDao()

    fun observeDevices(): Flow<List<DeviceEntity>> = deviceDao.observeAll()
    fun observeCameras(): Flow<List<CameraWithDevice>> = cameraDao.observeAll()

    fun endpoint(d: DeviceEntity) =
        Endpoint(d.host, d.httpPort, d.rtspPort, d.username, crypto.decrypt(d.passwordEnc))

    fun rtspUrl(c: CameraWithDevice, hd: Boolean): String =
        dahua.rtspUrl(endpoint(c.device), c.camera.channel, subStream = !hd)

    /**
     * Connects to a Dahua NVR or IP camera, reads its model and channel names,
     * and stores it with one camera row per connected channel.
     * Returns the number of cameras added.
     */
    suspend fun addDevice(
        name: String,
        host: String,
        httpPort: Int,
        rtspPort: Int,
        user: String,
        pass: String,
        zone: String,
        forceType: DeviceType? = null,
    ): Result<Int> = runCatching {
        require(deviceDao.findByHost(host.trim()) == null) { "$host is already added" }
        val ep = Endpoint(host.trim(), httpPort, rtspPort, user, pass)
        val info = dahua.probe(ep).getOrThrow()
        val type = forceType ?: if (info.isRecorder) DeviceType.NVR else DeviceType.CAMERA
        val deviceName = name.ifBlank {
            if (type == DeviceType.NVR) "NVR ${host.substringAfterLast('.')}" else info.channels.firstOrNull()?.title
                ?: "Camera ${host.substringAfterLast('.')}"
        }
        val id = deviceDao.insert(
            DeviceEntity(
                name = deviceName,
                type = type,
                host = ep.host,
                httpPort = httpPort,
                rtspPort = rtspPort,
                username = user,
                passwordEnc = crypto.encrypt(pass),
                model = info.model,
                serial = info.serial,
                channelCount = info.channels.size,
                online = true,
                lastSeen = System.currentTimeMillis(),
            )
        )
        val channels = if (type == DeviceType.CAMERA) info.channels.take(1) else info.channels.filter { it.connected }
        val rows = channels.map {
            CameraEntity(
                deviceId = id,
                channel = it.channel,
                name = if (type == DeviceType.CAMERA && name.isNotBlank()) name else it.title,
                zone = zone.ifBlank { "Unassigned" },
            )
        }
        cameraDao.insertAll(rows).count { it > 0 }
    }

    /** Re-reads the recorder's channels and adds any newly connected cameras. */
    suspend fun resync(device: DeviceEntity): Result<Int> = runCatching {
        val info = dahua.probe(endpoint(device)).getOrThrow()
        val existing = cameraDao.channelsOf(device.id).toSet()
        deviceDao.update(
            device.copy(
                model = info.model, channelCount = info.channels.size,
                online = true, lastSeen = System.currentTimeMillis()
            )
        )
        val fresh = info.channels.filter { it.connected && it.channel !in existing }
            .map { CameraEntity(deviceId = device.id, channel = it.channel, name = it.title) }
        cameraDao.insertAll(fresh).count { it > 0 }
    }

    suspend fun delete(device: DeviceEntity) = deviceDao.delete(device)
    suspend fun updateCamera(camera: CameraEntity) = cameraDao.update(camera)
    suspend fun setDeviceOnline(id: Long, online: Boolean) =
        deviceDao.setOnline(id, online, System.currentTimeMillis())
}
