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
    fun scanIsNeededOnlyForViewOrText() {
        assertTrue(rule.needsScan)
        val classOnly = GuardRule.fromMap(
            mapOf("packages" to listOf("com.android.settings"), "classNames" to listOf("A"), "action" to "back"),
        )
        val textOnly = GuardRule.fromMap(
            mapOf("packages" to listOf("com.android.settings"), "text" to "My app", "action" to "home"),
        )
        assertFalse(classOnly.needsScan)
        assertTrue(textOnly.needsScan)
        // A text-only rule reaches content events; a class rule does not.
        assertTrue(textOnly.matchesContent("com.android.settings"))
        assertFalse(classOnly.matchesContent("com.android.settings"))
        assertTrue(textOnly.matchesWindow("com.android.settings", "anything"))
    }

    @Test
    fun classSuffixesCoverRenamedScreens() {
        val bySuffix = GuardRule.fromMap(
            mapOf(
                "packages" to listOf("com.android.settings"),
                "classNameSuffixes" to listOf("DeviceAdminAdd"),
                "action" to "home",
            ),
        )
        assertTrue(bySuffix.matchesWindow("com.android.settings", "com.android.settings.applications.specialaccess.deviceadmin.DeviceAdminAdd"))
        assertTrue(bySuffix.matchesWindow("com.android.settings", "com.samsung.android.settings.deviceadmin.SecDeviceAdminAdd"))
        assertFalse(bySuffix.matchesWindow("com.android.settings", "com.android.settings.SubSettings"))
        assertFalse(bySuffix.matchesWindow("com.android.settings", null))
        assertFalse(bySuffix.matchesContent("com.android.settings"))
        assertFalse(bySuffix.needsScan)
        assertEquals(bySuffix, GuardRule.fromJson(bySuffix.toJson()))
    }

    @Test
    fun watchesRoundTrip() {
        val watch = TextWatch.fromMap(mapOf("packageName" to "com.android.chrome", "viewId" to "com.android.chrome:id/url_bar"))
        assertEquals(watch, TextWatch.fromJson(watch.toJson()))
    }
}
