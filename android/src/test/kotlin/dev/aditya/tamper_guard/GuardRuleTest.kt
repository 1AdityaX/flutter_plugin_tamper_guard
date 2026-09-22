package dev.aditya.tamper_guard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class GuardRuleTest {
    private val rule = GuardRule.fromMap(
        mapOf(
            "packages" to listOf("com.android.settings"),
            "classNames" to listOf("com.android.settings.DeviceAdminAdd"),
            "viewId" to "com.android.settings:id/admin_name",
            "text" to "My app",
            "action" to "back",
            "shieldMillis" to 600,
        ),
    )

    @Test
    fun roundTripsThroughJson() {
        assertEquals(rule, GuardRule.fromJson(rule.toJson()))
        val bare = GuardRule.fromMap(mapOf("packages" to listOf("a"), "action" to "home"))
        assertEquals(bare, GuardRule.fromJson(bare.toJson()))
        assertEquals(null, bare.viewId)
        assertEquals(0L, bare.shieldMillis)
    }

    @Test
    fun classRulesOnlySeeNewWindows() {
        assertTrue(rule.matchesWindow("com.android.settings", "com.android.settings.DeviceAdminAdd"))
        assertFalse(rule.matchesWindow("com.android.settings", "android.app.AlertDialog"))
        assertFalse(rule.matchesWindow("com.example", "com.android.settings.DeviceAdminAdd"))
        assertFalse(rule.matchesContent("com.android.settings"))
    }

    @Test
    fun packageRulesSeeEveryChange() {
        val any = GuardRule.fromMap(mapOf("packages" to listOf("com.android.settings"), "action" to "back"))
        assertTrue(any.matchesWindow("com.android.settings", null))
        assertTrue(any.matchesContent("com.android.settings"))
        assertFalse(any.matchesContent("com.example"))
    }

    @Test
    fun watchesRoundTrip() {
        val watch = TextWatch.fromMap(mapOf("packageName" to "com.android.chrome", "viewId" to "com.android.chrome:id/url_bar"))
        assertEquals(watch, TextWatch.fromJson(watch.toJson()))
    }
}
