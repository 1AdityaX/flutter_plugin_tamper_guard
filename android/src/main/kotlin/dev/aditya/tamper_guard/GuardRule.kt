package dev.aditya.tamper_guard

import org.json.JSONArray
import org.json.JSONObject

/** What the service does the instant a matching window is on screen. */
enum class GuardAction { NONE, BACK, HOME }

/**
 * A window to react to: any window of one of [packages], narrowed by the
 * activity or dialog class names of the window and/or by a view with a
 * given id (and text) somewhere inside it.
 */
data class GuardRule(
    val packages: Set<String>,
    val classNames: Set<String>,
    val viewId: String?,
    val text: String?,
    val action: GuardAction,
    val shieldMillis: Long,
) {
    /** A new window from a state-changed event; a view check may still follow. */
    fun matchesWindow(packageName: String?, className: String?): Boolean =
        packageName in packages && (classNames.isEmpty() || className in classNames)

    /** A change inside an existing window; class rules only see new windows. */
    fun matchesContent(packageName: String?): Boolean =
        classNames.isEmpty() && packageName in packages

    fun toJson(): JSONObject = JSONObject().apply {
        put("packages", JSONArray(packages))
        put("classNames", JSONArray(classNames))
        putOpt("viewId", viewId)
        putOpt("text", text)
        put("action", action.name)
        put("shieldMillis", shieldMillis)
    }

    companion object {
        fun fromJson(json: JSONObject) = GuardRule(
            packages = json.getJSONArray("packages").strings(),
            classNames = json.optJSONArray("classNames").strings(),
            viewId = json.optString("viewId").ifEmpty { null },
            text = json.optString("text").ifEmpty { null },
            action = GuardAction.valueOf(json.getString("action")),
            shieldMillis = json.getLong("shieldMillis"),
        )

        fun fromMap(map: Map<*, *>) = GuardRule(
            packages = (map["packages"] as List<*>).map { it as String }.toSet(),
            classNames = (map["classNames"] as List<*>? ?: emptyList<Any>()).map { it as String }.toSet(),
            viewId = map["viewId"] as String?,
            text = map["text"] as String?,
            action = GuardAction.valueOf((map["action"] as String).uppercase()),
            shieldMillis = (map["shieldMillis"] as Number? ?: 0).toLong(),
        )
    }
}

/** A view whose text is streamed to Dart whenever it changes. */
data class TextWatch(val packageName: String, val viewId: String) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("packageName", packageName)
        put("viewId", viewId)
    }

    companion object {
        fun fromJson(json: JSONObject) =
            TextWatch(json.getString("packageName"), json.getString("viewId"))

        fun fromMap(map: Map<*, *>) =
            TextWatch(map["packageName"] as String, map["viewId"] as String)
    }
}

internal fun JSONArray?.strings(): Set<String> =
    if (this == null) emptySet() else (0 until length()).map { getString(it) }.toSet()
