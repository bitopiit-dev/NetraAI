package com.factory.netra.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.factory.netra.container
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** Keeps the AI patrol and Dahua event listeners alive while the screen shows other apps. */
class SurveillanceService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val c = container
        val notification = c.notifier.serviceNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        c.ai.start(scope)
        c.events.start(scope)
        running = true
        return START_STICKY
    }

    override fun onDestroy() {
        container.ai.stop()
        container.events.stop()
        scope.cancel()
        running = false
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "com.factory.netra.STOP"

        @Volatile var running = false
            private set

        fun start(context: Context) =
            ContextCompat.startForegroundService(context, Intent(context, SurveillanceService::class.java))

        fun stop(context: Context) {
            context.startService(Intent(context, SurveillanceService::class.java).setAction(ACTION_STOP))
        }
    }
}
