package com.saysharp.saysharp.telemetry

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.saysharp.saysharp.BuildConfig
import com.saysharp.shared.telemetry.TelemetryBackend

class FirebaseTelemetry(context: Context) : TelemetryBackend {
    private val analytics = FirebaseAnalytics.getInstance(context.applicationContext)
    private val crashes = FirebaseCrashlytics.getInstance()
    private val reported = mutableSetOf<String>()

    init {
        analytics.setUserProperty("build_type", BuildConfig.BUILD_TYPE)
        crashes.setCustomKey("build_type", BuildConfig.BUILD_TYPE)
    }

    override fun event(name: String, parameters: Map<String, Any>) {
        val bundle = Bundle().apply {
            parameters.forEach { (key, value) -> when (value) {
                is String -> putString(key, value)
                is Boolean -> putLong(key, if (value) 1 else 0)
                is Number -> if (value is Float || value is Double) putDouble(key, value.toDouble()) else putLong(key, value.toLong())
            } }
        }
        analytics.logEvent(name, bundle)
        crashes.log("$name ${parameters.entries.joinToString { "${it.key}=${it.value}" }}")
    }

    override fun context(parameters: Map<String, Any>) {
        parameters.forEach { (key, value) -> when (value) {
            is String -> crashes.setCustomKey(key, value)
            is Boolean -> crashes.setCustomKey(key, value)
            is Number -> crashes.setCustomKey(key, value.toLong())
        } }
    }

    override fun error(operation: String, error: Throwable?) {
        val type = error?.javaClass?.simpleName ?: "PlatformCallback"
        crashes.log("error operation=$operation type=$type")
        // Preserve useful call sites without uploading exception messages, file paths or causes.
        // Bound repeated native callback/scheduling failures to one report per operation/type/process.
        val first = synchronized(reported) { reported.add("$operation:$type") }
        if (first) {
            crashes.recordException(sanitizedException(operation, error))
        }
    }
}

private class OperationalException(message: String) : RuntimeException(message)

internal fun sanitizedException(operation: String, error: Throwable?): RuntimeException =
    OperationalException("$operation: ${error?.javaClass?.simpleName ?: "PlatformCallback"}").also { sanitized ->
        error?.let { sanitized.stackTrace = it.stackTrace }
    }
