package com.travelassistant.app.update

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Checks Google Play for an available update on launch and drives an IMMEDIATE (blocking)
 * update flow so the app updates itself when opened.
 *
 * Only works for builds installed from Google Play (and when a higher versionCode is live on a
 * track the user has access to). It is a safe no-op elsewhere — e.g. a sideloaded debug APK,
 * where Play returns "no update".
 */
class AppUpdater(activity: ComponentActivity) {

    private val manager = AppUpdateManagerFactory.create(activity)

    // Must be registered before the activity is STARTED (i.e. during onCreate).
    private val launcher: ActivityResultLauncher<IntentSenderRequest> =
        activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }

    /** Call from onCreate: start an immediate update if one is available. */
    fun checkForUpdate() {
        manager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
            ) {
                startImmediate(info)
            }
        }
    }

    /** Call from onResume: resume an immediate update that was interrupted. */
    fun resumeIfInProgress() {
        manager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                startImmediate(info)
            }
        }
    }

    private fun startImmediate(info: AppUpdateInfo) {
        runCatching {
            manager.startUpdateFlowForResult(
                info,
                launcher,
                AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
            )
        }
    }
}
