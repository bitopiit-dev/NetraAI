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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
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
        val lastCrash = CrashLog.takeLast(this)
        setContent {
            NetraTheme {
                NetraShell(openRequest.value) { openRequest.value = null }
                var crash by remember { mutableStateOf(lastCrash) }
                crash?.let { report -> CrashDialog(report) { crash = null } }
            }
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
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

    @Composable
    private fun CrashDialog(report: String, onClose: () -> Unit) {
        val clipboard = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = onClose,
            title = { Text("Netra AI closed unexpectedly last time") },
            text = {
                Column {
                    Text("Send this report to the developer (Copy, then paste in WhatsApp).")
                    Text(
                        report.take(1_500),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp).heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { clipboard.setText(AnnotatedString(report)); onClose() }) { Text("Copy report") }
            },
            dismissButton = { TextButton(onClick = onClose) { Text("Close") } },
        )
    }

    companion object {
        const val EXTRA_OPEN = "open"
        const val OPEN_ALERTS = "alerts"
    }
}
