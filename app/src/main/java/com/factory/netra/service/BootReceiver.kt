package com.factory.netra.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.factory.netra.container

/** Control-room TV boxes reboot after power cuts: resume monitoring automatically. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && context.container.settings.current.startOnBoot) {
            runCatching { SurveillanceService.start(context) }
        }
    }
}
