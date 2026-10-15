package com.saysharp.saysharp

import android.app.Application
import android.content.Context
import com.saysharp.saysharp.telemetry.FirebaseTelemetry
import com.saysharp.shared.telemetry.Telemetry

class SaySharpApplication : Application() {
    var telemetry: Telemetry = Telemetry.None
        private set

    override fun onCreate() {
        super.onCreate()
        // Firebase's provider initializes before Application. SDK problems must not block practice.
        telemetry = runCatching { Telemetry(FirebaseTelemetry(this)) }.getOrDefault(Telemetry.None)
    }
}

val Context.telemetry: Telemetry
    get() = (applicationContext as? SaySharpApplication)?.telemetry ?: Telemetry.None
