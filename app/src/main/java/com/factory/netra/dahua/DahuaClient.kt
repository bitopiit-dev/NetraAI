package com.factory.netra.dahua

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class Endpoint(
    val host: String,
    val httpPort: Int,
    val rtspPort: Int,
    val user: String,
    val pass: String,
) {
    val base get() = "http://$host:$httpPort"
}

data class ChannelInfo(val channel: Int, val title: String, val connected: Boolean)

data class DeviceInfo(
    val model: String,
    val serial: String,
    val isRecorder: Boolean,
    val channels: List<ChannelInfo>,
)

class AuthException(msg: String) : IOException(msg)

/**
 * Thin client for the Dahua HTTP API (works on Dahua / Dahua-OEM NVRs & IPC).
 * Reference: the "Dahua HTTP API for IPC & NVR" document (the cgi-bin endpoints).
 */
class DahuaClient(private val base: OkHttpClient) {
    private val clients = ConcurrentHashMap<String, OkHttpClient>()

    fun client(ep: Endpoint, streaming: Boolean = false): OkHttpClient {
        val key = "${ep.host}:${ep.httpPort}:${ep.user}:${ep.pass.hashCode()}:$streaming"
        return clients.getOrPut(key) {
            base.newBuilder()
                .authenticator(DigestAuthenticator(ep.user, ep.pass))
                .apply { if (streaming) readTimeout(30, TimeUnit.SECONDS) }
                .build()
        }
    }

    /** rtsp://user:pass@ip:554/cam/realmonitor?channel=N&subtype=0|1 */
    fun rtspUrl(ep: Endpoint, channel: Int, subStream: Boolean): String =
        "rtsp://${Uri.encode(ep.user)}:${Uri.encode(ep.pass)}@${ep.host}:${ep.rtspPort}" +
            "/cam/realmonitor?channel=$channel&subtype=${if (subStream) 1 else 0}"

    private suspend fun get(ep: Endpoint, pathAndQuery: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(ep.base + pathAndQuery).build()
        client(ep).newCall(req).execute().use { r ->
            if (r.code == 401) throw AuthException("Wrong username or password for ${ep.host}")
            if (!r.isSuccessful) throw IOException("HTTP ${r.code} from ${ep.host}")
            r.body?.string().orEmpty()
        }
    }

    /** JPEG snapshot of a channel (1-based). Returns null when unreachable. */
    suspend fun snapshot(ep: Endpoint, channel: Int): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("${ep.base}/cgi-bin/snapshot.cgi?channel=$channel").build()
            client(ep).newCall(req).execute().use { r ->
                val type = r.header("Content-Type").orEmpty()
                if (r.isSuccessful && type.startsWith("image")) r.body?.bytes() else null
            }
        }.getOrNull()
    }

    /** Identifies the device and lists its channels (with connection state on NVRs). */
    suspend fun probe(ep: Endpoint): Result<DeviceInfo> = runCatching {
        val type = kv(get(ep, "/cgi-bin/magicBox.cgi?action=getDeviceType"))["type"].orEmpty()
        val serial = runCatching { kv(get(ep, "/cgi-bin/magicBox.cgi?action=getSerialNo"))["sn"] }
            .getOrNull().orEmpty()

        val titles = runCatching {
            val text = get(ep, "/cgi-bin/configManager.cgi?action=getConfig&name=ChannelTitle")
            Regex("""table\.ChannelTitle\[(\d+)]\.Name=(.*)""").findAll(text)
                .associate { it.groupValues[1].toInt() to it.groupValues[2].trim() }
        }.getOrDefault(emptyMap())

        val upper = type.uppercase()
        // Trust the model string (DH-IPC-..., DH-NVR...). Some IP cameras report extra
        // ChannelTitle rows, so the title count is only used when the type is unknown.
        val isRecorder = when {
            listOf("NVR", "XVR", "DVR", "HCVR").any { it in upper } -> true
            listOf("IPC", "SD", "HFW", "HDW", "HDBW", "PFW").any { it in upper } -> false
            else -> titles.size > 1
        }

        // NVR: which channels actually have a camera connected?
        val states: Map<Int, Boolean> = if (!isRecorder) emptyMap() else runCatching {
            val text = get(ep, "/cgi-bin/LogicDeviceManager.cgi?action=getCameraState&uniqueChannels[0]=-1")
            val ch = HashMap<Int, Int>()
            val st = HashMap<Int, String>()
            Regex("""states\[(\d+)]\.(channel|connectionState)=(.*)""").findAll(text).forEach {
                val i = it.groupValues[1].toInt()
                if (it.groupValues[2] == "channel") ch[i] = it.groupValues[3].trim().toInt()
                else st[i] = it.groupValues[3].trim()
            }
            ch.entries.associate { (i, c) -> c to (st[i]?.equals("Connected", true) ?: true) }
        }.getOrDefault(emptyMap())

        val count = if (!isRecorder) 1 else titles.keys.maxOrNull()?.plus(1) ?: 1
        val channels = (0 until count).map { i ->
            ChannelInfo(
                channel = i + 1,
                title = titles[i]?.takeIf { it.isNotBlank() } ?: "Channel ${i + 1}",
                connected = states[i] ?: true,
            )
        }
        DeviceInfo(model = type.ifBlank { "Dahua" }, serial = serial, isRecorder = isRecorder, channels = channels)
    }

    private fun kv(text: String): Map<String, String> =
        text.lineSequence().mapNotNull { line ->
            val i = line.indexOf('=')
            if (i <= 0) null else line.substring(0, i).trim() to line.substring(i + 1).trim()
        }.toMap()
}
