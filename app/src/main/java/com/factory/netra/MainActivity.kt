package com.factory.netra

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.factory.netra.service.SurveillanceService
import com.factory.netra.ui.Dest
import com.factory.netra.ui.NetraShell
import com.factory.netra.ui.theme.NetraTheme

class MainActivity : ComponentActivity() {
    private val openRequest = mutableStateOf<Dest?>(null)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { startMonitoring() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Control-room screen: never sleep while Netra is in front.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        handleIntent(intent)
        setContent {
            NetraTheme {
                NetraShell(openRequest.value) { openRequest.value = null }
            }
        }
        if ((Build.VERSION.SDK_INT >= 33) &&
            (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED)
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startMonitoring()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getStringExtra(EXTRA_OPEN) == OPEN_ALERTS) openRequest.value = Dest.Alerts
    }

    private fun startMonitoring() {
        runCatching { SurveillanceService.start(this) }
    }

    companion object {
        const val EXTRA_OPEN = "open"
        const val OPEN_ALERTS = "alerts"
    }
}
