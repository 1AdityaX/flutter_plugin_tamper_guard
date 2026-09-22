package dev.aditya.tamper_guard

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Asked by Settings the moment the user taps Deactivate for this admin, and
 * only then. With a warning set, Settings confirms before deactivating;
 * with an action set, the service leaves and shields the screen first, so the
 * confirmation is never answered.
 */
class AdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence? {
        val action = GuardStore.disableAction(context)
        if (action != GuardAction.NONE) {
            GuardService.instance?.act(action, GuardStore.disableShieldMillis(context))
        }
        return GuardStore.warning(context)
    }
}
