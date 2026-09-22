package dev.aditya.tamper_guard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Rules, watches and what to do when the admin is asked to deactivate: held
 * in memory for the service and in preferences for the next process, so the
 * guard works before any Flutter engine has started.
 */
internal object GuardStore {
    private const val PREFS = "tamper_guard"
    private const val RULES = "rules"
    private const val WATCHES = "watches"
    private const val WARNING = "admin_warning"
    private const val DISABLE_ACTION = "admin_disable_action"
    private const val DISABLE_SHIELD = "admin_disable_shield"

    @Volatile private var rules: List<GuardRule>? = null
    @Volatile private var watches: List<TextWatch>? = null

    fun rules(context: Context): List<GuardRule> = rules ?: load(context, RULES) {
        GuardRule.fromJson(it)
    }.also { rules = it }

    fun watches(context: Context): List<TextWatch> = watches ?: load(context, WATCHES) {
        TextWatch.fromJson(it)
    }.also { watches = it }

    fun saveRules(context: Context, value: List<GuardRule>) {
        rules = value
        prefs(context).edit().putString(RULES, JSONArray(value.map { it.toJson() }).toString()).apply()
    }

    fun saveWatches(context: Context, value: List<TextWatch>) {
        watches = value
        prefs(context).edit().putString(WATCHES, JSONArray(value.map { it.toJson() }).toString()).apply()
    }

    fun warning(context: Context): String? = prefs(context).getString(WARNING, null)

    fun saveWarning(context: Context, value: String?) {
        prefs(context).edit().putString(WARNING, value).apply()
    }

    fun disableAction(context: Context): GuardAction =
        GuardAction.valueOf(prefs(context).getString(DISABLE_ACTION, GuardAction.NONE.name)!!)

    fun disableShieldMillis(context: Context): Long = prefs(context).getLong(DISABLE_SHIELD, 0)

    fun saveDisableAction(context: Context, action: GuardAction, shieldMillis: Long) {
        prefs(context).edit()
            .putString(DISABLE_ACTION, action.name)
            .putLong(DISABLE_SHIELD, shieldMillis)
            .apply()
    }

    private fun <T> load(context: Context, key: String, parse: (JSONObject) -> T): List<T> {
        val array = JSONArray(prefs(context).getString(key, "[]"))
        return (0 until array.length()).map { parse(array.getJSONObject(it)) }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
