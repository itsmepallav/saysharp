package com.saysharp.saysharp

import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.saysharp.saysharp.audio.AndroidAudioRecorder
import com.saysharp.saysharp.storage.AndroidSessionStore
import com.saysharp.shared.practice.PracticeController
import com.saysharp.shared.ui.SaySharpApp
import com.saysharp.shared.reminder.ReminderController
import com.saysharp.saysharp.reminder.AndroidReminders
import com.saysharp.shared.engagement.EngagementController
import com.saysharp.saysharp.storage.AndroidEngagementStore

class MainActivity : ComponentActivity() {
    private lateinit var controller: PracticeController
    private lateinit var reminders: ReminderController
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        telemetry.track("permission_result", "permission" to "notifications", "granted" to it)
        if (::reminders.isInitialized) {
            reminders.setEnabled(true)
        }
    }
    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        telemetry.track("permission_result", "permission" to "microphone", "granted" to granted)
        if (::controller.isInitialized) {
            if (granted) controller.startRecording() else controller.permissionDenied()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(0xFFF6F5F0.toInt(), 0xFFF6F5F0.toInt()),
        )
        controller = PracticeController(AndroidAudioRecorder(this), AndroidSessionStore(noBackupFilesDir), telemetry)
        reminders = ReminderController(AndroidReminders(this, diagnostics = telemetry), telemetry)
        val engagement = EngagementController(AndroidEngagementStore(noBackupFilesDir), telemetry)
        trackReminderOpen(intent)
        setContent {
            SaySharpApp(controller, onRequestRecording = {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    controller.startRecording()
                } else {
                    telemetry.track("permission_requested", "permission" to "microphone")
                    microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            }, reminders = reminders, onRequestReminder = {
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                        Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    telemetry.track("permission_requested", "permission" to "notifications")
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else reminders.setEnabled(true)
            }, onOpenNotificationSettings = {
                val intent = if (Build.VERSION.SDK_INT >= 26) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                } else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName"))
                startActivity(intent)
            }, backHandler = { enabled, onBack -> BackHandler(enabled, onBack) }, engagement = engagement, telemetry = telemetry)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::reminders.isInitialized) reminders.refresh()
    }

    override fun onStop() {
        if (::controller.isInitialized) telemetry.track("app_backgrounded", "day" to controller.session.day,
            "stage" to controller.session.stage.name, "was_recording" to controller.isRecording)
        if (::controller.isInitialized) controller.onBackground()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        trackReminderOpen(intent)
    }

    private fun trackReminderOpen(intent: Intent?) {
        val source = intent?.getStringExtra(AndroidReminders.EXTRA_SOURCE)
        if (source in listOf("daily", "preview")) {
            telemetry.track("reminder_opened", "source" to requireNotNull(source))
            intent?.removeExtra(AndroidReminders.EXTRA_SOURCE)
        }
    }
}
