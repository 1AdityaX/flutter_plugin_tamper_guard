package dev.aditya.tamper_guard

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/** With a warning set, Settings confirms before deactivating; without one it does not ask. */
class AdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence? =
        GuardStore.warning(context)
}
