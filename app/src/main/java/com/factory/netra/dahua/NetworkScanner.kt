package com.factory.netra.dahua

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

data class ScanHit(
    val ip: String,
    val http: Boolean,
    val rtsp: Boolean,
    /** TCP 37777 is Dahua's private SDK port — a strong "this is Dahua" signal. */
    val dahuaPort: Boolean,
)

sealed interface ScanEvent {
    data class Progress(val done: Int, val total: Int) : ScanEvent
    data class Found(val hit: ScanHit) : ScanEvent
}

/** TCP port sweep of a /24 subnet looking for Dahua NVRs and cameras. */
class NetworkScanner {
    fun scan(prefix: String, from: Int = 1, to: Int = 254): Flow<ScanEvent> = channelFlow {
        val total = (to - from) + 1
        val done = AtomicInteger(0)
        val gate = Semaphore(48)
        for (i in from..to) {
            launch(Dispatchers.IO) {
                gate.withPermit {
                    val ip = prefix + i
                    val http = open(ip, 80)
                    val dahua = open(ip, 37777)
                    val rtsp = if (http || dahua) open(ip, 554) else false
                    if (dahua || (http && rtsp)) send(ScanEvent.Found(ScanHit(ip, http, rtsp, dahua)))
                    send(ScanEvent.Progress(done.incrementAndGet(), total))
                }
            }
        }
    }

    private fun open(ip: String, port: Int, timeoutMs: Int = 400): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(ip, port), timeoutMs); true }
    } catch (_: Exception) {
        false
    }
}
