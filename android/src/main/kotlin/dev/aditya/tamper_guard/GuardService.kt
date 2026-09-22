package dev.aditya.tamper_guard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Reacts to windows inside the service itself, with no trip through Dart, so
 * a guarded screen is gone within a few frames of appearing.
 */
class GuardService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val hideShield = Runnable { removeShield() }
    private val lastText = HashMap<Pair<String, String>, String>()
    private var lastFired = 0L
    private var shield: View? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "service connected")
    }

    override fun onDestroy() {
        removeShield()
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // A windows-changed event carries no package, so the front window's is used.
        val packageName = event.packageName?.toString() ?: activePackage() ?: return
        val newWindow = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        val className = if (newWindow) event.className?.toString() else null
        if (newWindow) {
            Log.d(TAG, "window pkg=$packageName class=$className rules=${GuardStore.rules(this).size}")
            Sinks.windows.emit(mapOf("packageName" to packageName, "className" to className))
        }
        // One action per event, and none while the last one still lands.
        if (SystemClock.uptimeMillis() - lastFired >= THROTTLE_MILLIS) {
            val rule = GuardStore.rules(this).firstOrNull { rule ->
                val about = if (newWindow) rule.matchesWindow(packageName, className)
                else rule.matchesContent(packageName)
                about && (!rule.needsScan || hasMatch(rule))
            }
            if (rule != null) fire(rule, attempt = 0)
        }
        watchText(packageName)
    }

    /** Forgets the last texts so the next event re-emits them. */
    fun resetTexts() = lastText.clear()

    /** Shields the screen and leaves it; also what the device admin's deactivation triggers. */
    fun act(action: GuardAction, shieldMillis: Long) {
        Log.i(TAG, "act $action shield=$shieldMillis")
        lastFired = SystemClock.uptimeMillis()
        if (shieldMillis > 0) showShield(shieldMillis)
        when (action) {
            GuardAction.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            GuardAction.HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
            GuardAction.NONE -> {}
        }
    }

    private fun fire(rule: GuardRule, attempt: Int) {
        if (attempt == 0) {
            act(rule.action, rule.shieldMillis)
        } else {
            lastFired = SystemClock.uptimeMillis()
            when (rule.action) {
                GuardAction.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
                GuardAction.HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
                GuardAction.NONE -> {}
            }
        }
        // A dialog over the window takes the first Back; the window itself
        // takes the next, so a view rule looks again while the user is still
        // inside the package and the view is still on screen.
        if (rule.needsScan && rule.action != GuardAction.NONE && attempt < RETRIES) {
            handler.postDelayed({
                if (activePackage() in rule.packages && hasMatch(rule)) fire(rule, attempt + 1)
            }, RETRY_MILLIS)
        }
    }

    // Every window of the rule's packages is searched, so a screen under a
    // dialog still counts.
    private fun hasMatch(rule: GuardRule): Boolean {
        var found = false
        for (root in roots(rule.packages)) {
            if (!found) {
                found = if (rule.viewId != null) hasViewId(root, rule)
                else hasText(root, rule.text!!, intArrayOf(MAX_NODES))
            }
            root.release()
        }
        return found
    }

    private fun hasViewId(root: AccessibilityNodeInfo, rule: GuardRule): Boolean {
        var found = false
        for (node in root.findAccessibilityNodeInfosByViewId(rule.viewId!!)) {
            if (rule.text == null || node.text?.toString()?.trim() == rule.text) found = true
            node.release()
        }
        return found
    }

    // A bounded walk, so a text rule needs no view id and holds across OEM
    // screens that name their views differently. [node] is released by the
    // caller; children fetched here are not.
    private fun hasText(node: AccessibilityNodeInfo, text: String, budget: IntArray): Boolean {
        if (node.text?.toString()?.trim() == text ||
            node.contentDescription?.toString()?.trim() == text
        ) {
            return true
        }
        if (budget[0]-- <= 0) return false
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val found = hasText(child, text, budget)
            child.release()
            if (found) return true
        }
        return false
    }

    // Some builds leave a floating screen out of the window list, so the
    // active window is read as well.
    private fun roots(packages: Set<String>): List<AccessibilityNodeInfo> {
        val roots = ArrayList<AccessibilityNodeInfo>()
        val seen = HashSet<Int>()
        val candidates = windows.mapNotNull { window -> window.root.also { window.release() } }
        for (root in candidates + listOfNotNull(rootInActiveWindow)) {
            if (root.packageName?.toString() in packages && seen.add(root.windowId)) {
                roots.add(root)
            } else {
                root.release()
            }
        }
        return roots
    }

    private fun activePackage(): String? {
        val root = rootInActiveWindow ?: return null
        val packageName = root.packageName?.toString()
        root.release()
        return packageName
    }

    // Touches land on the shield instead of the screen underneath while the
    // action takes effect.
    @SuppressLint("ClickableViewAccessibility")
    private fun showShield(millis: Long) {
        handler.removeCallbacks(hideShield)
        if (shield == null) {
            val view = View(this).apply {
                setBackgroundColor(SHIELD_COLOR)
                setOnTouchListener { _, _ -> true }
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            )
            try {
                (getSystemService(Context.WINDOW_SERVICE) as WindowManager).addView(view, params)
            } catch (_: RuntimeException) {
                return
            }
            shield = view
        }
        handler.postDelayed(hideShield, millis)
    }

    private fun removeShield() {
        handler.removeCallbacks(hideShield)
        val view = shield ?: return
        shield = null
        try {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(view)
        } catch (_: RuntimeException) {
            // Already gone with the service.
        }
    }

    private fun watchText(packageName: String) {
        val watches = GuardStore.watches(this).filter { it.packageName == packageName }
        if (watches.isEmpty()) return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() == packageName) {
            for (watch in watches) {
                val nodes = root.findAccessibilityNodeInfosByViewId(watch.viewId)
                val text = nodes.firstOrNull()?.text?.toString()
                nodes.forEach { it.release() }
                if (text == null) continue
                val key = packageName to watch.viewId
                if (lastText[key] == text) continue
                lastText[key] = text
                Sinks.texts.emit(
                    mapOf("packageName" to packageName, "viewId" to watch.viewId, "text" to text),
                )
            }
        }
        root.release()
    }

    companion object {
        private const val THROTTLE_MILLIS = 250L
        private const val RETRY_MILLIS = 150L
        private const val RETRIES = 4
        private const val MAX_NODES = 600
        private const val SHIELD_COLOR = 0xE6000000.toInt()
        private const val TAG = "TamperGuard"

        @Volatile
        var instance: GuardService? = null
            private set

        /** Whether the user has switched this service on, and accessibility with it. */
        fun isEnabled(context: Context): Boolean {
            val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            return manager
                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { info ->
                    val service = info.resolveInfo.serviceInfo
                    service.packageName == context.packageName &&
                        service.name == GuardService::class.java.name
                }
        }
    }
}

// Nodes and windows are pooled before API 33.
private fun AccessibilityNodeInfo.release() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) @Suppress("DEPRECATION") recycle()
}

private fun AccessibilityWindowInfo.release() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) @Suppress("DEPRECATION") recycle()
}
