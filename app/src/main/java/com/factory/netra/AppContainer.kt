package com.factory.netra

import android.app.Application
import com.factory.netra.ai.AiScheduler
import com.factory.netra.ai.ObjectDetectorEngine
import com.factory.netra.ai.RuleEngine
import com.factory.netra.dahua.DahuaClient
import com.factory.netra.dahua.DahuaEventService
import com.factory.netra.dahua.NetworkScanner
import com.factory.netra.data.db.NetraDatabase
import com.factory.netra.data.prefs.SettingsStore
import com.factory.netra.data.repo.AlertRepository
import com.factory.netra.data.repo.DeviceRepository
import com.factory.netra.data.security.CryptoBox
import com.factory.netra.service.AlertNotifier
import okhttp3.ConnectionPool
import org.videolan.libvlc.LibVLC
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Manual dependency container (no DI framework needed). */
class AppContainer(val app: Application) {
    val db: NetraDatabase = NetraDatabase.build(app)
    val settings = SettingsStore(app)
    val crypto = CryptoBox()

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(16, 2, TimeUnit.MINUTES))
        .retryOnConnectionFailure(false)
        .build()

    val dahua = DahuaClient(http)
    val devices = DeviceRepository(db, crypto, dahua)
    val alerts = AlertRepository(app, db.alertDao())
    val notifier = AlertNotifier(app)
    val detector: ObjectDetectorEngine by lazy { ObjectDetectorEngine(app) }
    val rules = RuleEngine(this)
    val ai = AiScheduler(this)
    val events = DahuaEventService(this)
    val scanner = NetworkScanner()

    /** One shared libVLC instance (creating one per tile is slow and wastes memory). */
    val libVlc: LibVLC by lazy {
        LibVLC(app, arrayListOf("--no-audio", "--rtsp-tcp", "--network-caching=400", "--no-stats", "--no-sub-autodetect-file"))
    }
}
