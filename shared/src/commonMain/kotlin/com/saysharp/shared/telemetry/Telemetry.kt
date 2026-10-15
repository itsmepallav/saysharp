package com.saysharp.shared.telemetry

/** Only editorial identifiers, enum choices and numeric counts belong here. Never user text or audio. */
interface TelemetryBackend {
    fun event(name: String, parameters: Map<String, Any>)
    fun context(parameters: Map<String, Any>)
    fun error(operation: String, error: Throwable?)
}

/** Observability must never prevent a recording, save or navigation. */
class Telemetry(private val backend: TelemetryBackend? = null) {
    fun track(name: String, vararg parameters: Pair<String, Any>) {
        enqueue(name, *parameters)
    }

    /** True means the SDK accepted the call, not that Firebase confirmed server delivery. */
    fun enqueue(name: String, vararg parameters: Pair<String, Any>): Boolean {
        val target = backend ?: return false
        return runCatching { target.event(name, parameters.toMap()) }.isSuccess
    }

    fun context(vararg parameters: Pair<String, Any>) {
        runCatching { backend?.context(parameters.toMap()) }
    }

    fun error(operation: String, error: Throwable? = null) {
        runCatching { backend?.error(operation, error) }
    }

    fun screen(name: String, day: Int = 0, stage: String = "none") {
        context("screen" to name, "day" to day, "stage" to stage)
        track("screen_view", "screen_name" to name, "screen_class" to "SaySharp",
            "day" to day, "stage" to stage)
    }

    companion object { val None = Telemetry() }
}
