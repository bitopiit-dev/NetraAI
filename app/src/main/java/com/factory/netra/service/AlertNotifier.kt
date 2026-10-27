package com.factory.netra.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.factory.netra.MainActivity
import com.factory.netra.R
import com.factory.netra.data.db.AlertEntity
import com.factory.netra.data.db.Severity

class AlertNotifier(private val context: Context) {
    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CH_CRITICAL, "Critical security alerts", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "Restricted area, after-hours movement, tampering, tripwire" }
            )
            nm.createNotificationChannel(
                NotificationChannel(CH_ALERTS, "Other alerts", NotificationManager.IMPORTANCE_DEFAULT)
            )
            nm.createNotificationChannel(
                NotificationChannel(CH_SERVICE, "Monitoring service", NotificationManager.IMPORTANCE_MIN)
            )
        }
    }

    private fun openApp(requestCode: Int, openAlerts: Boolean): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (openAlerts) putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_ALERTS)
        }
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun serviceNotification(): Notification =
        NotificationCompat.Builder(context, CH_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_netra)
            .setContentTitle("Netra AI is watching")
            .setContentText("AI patrol and recorder events are active")
            .setOngoing(true)
            .setContentIntent(openApp(0, false))
            .build()

    fun notify(alert: AlertEntity) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val critical = alert.severity == Severity.HIGH
        val builder = NotificationCompat.Builder(context, if (critical) CH_CRITICAL else CH_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_netra)
            .setContentTitle("${alert.type.title} · ${alert.cameraName}")
            .setContentText("${alert.zone}: ${alert.message}")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(if (critical) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openApp(alert.id.toInt(), true))

        alert.snapshotPath?.let { path ->
            val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
            BitmapFactory.decodeFile(path, opts)?.let { bmp ->
                builder.setLargeIcon(bmp)
                builder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(bmp))
            }
        }
        try {
            NotificationManagerCompat.from(context).notify(alert.id.toInt(), builder.build())
        } catch (_: SecurityException) {
        }
    }

    companion object {
        const val CH_CRITICAL = "netra_critical"
        const val CH_ALERTS = "netra_alerts"
        const val CH_SERVICE = "netra_service"
    }
}
