package com.saysharp.saysharp

import android.content.Context
import com.saysharp.shared.platform.android.AndroidAudioRecorder
import com.saysharp.saysharp.reminder.AndroidReminders
import com.saysharp.saysharp.storage.AndroidEngagementStore
import com.saysharp.saysharp.storage.AndroidSessionStore
import com.saysharp.shared.engagement.EngagementController
import com.saysharp.shared.practice.PracticeController
import com.saysharp.shared.reminder.ReminderController
import com.saysharp.shared.telemetry.Telemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** Activity-owned dependencies; platform resources never escape into a global singleton. */
class AndroidAppDependencies(context: Context, telemetry: Telemetry) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audio = AndroidAudioRecorder(context.applicationContext)
    val practice = PracticeController(audio = audio, player = audio,
        store = AndroidSessionStore(context.noBackupFilesDir), telemetry = telemetry, scope = scope)
    val reminders = ReminderController(AndroidReminders(context.applicationContext, diagnostics = telemetry), telemetry)
    val engagement = EngagementController(AndroidEngagementStore(context.noBackupFilesDir), telemetry)

    fun close() {
        practice.onBackground()
        scope.cancel()
    }
}
