package com.saysharp.shared.platform.android

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings

/** The activity owns the result launcher; this adapter owns Android permission/settings policy. */
class AndroidMicrophonePermission(private val activity: Activity) {
    private val preferences = activity.getSharedPreferences("microphone-permission", Activity.MODE_PRIVATE)
    fun granted() = activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    fun markRequested() { preferences.edit().putBoolean("requested", true).apply() }
    fun requiresSettings() = !granted() && preferences.getBoolean("requested", false) &&
        !activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
    fun openSettings() {
        activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}")))
    }
}
