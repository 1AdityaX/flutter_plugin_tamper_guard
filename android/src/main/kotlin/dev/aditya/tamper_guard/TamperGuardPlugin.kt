package dev.aditya.tamper_guard

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry

class TamperGuardPlugin :
    FlutterPlugin,
    ActivityAware,
    MethodChannel.MethodCallHandler,
    PluginRegistry.ActivityResultListener {
    private lateinit var context: Context
    private lateinit var methods: MethodChannel
    private lateinit var windows: EventChannel
    private lateinit var texts: EventChannel
    private lateinit var enabled: EventChannel
    private val windowSinks = SinkHandler(Sinks.windows)
    private val textSinks = SinkHandler(Sinks.texts)
    private lateinit var enabledHandler: EnabledHandler
    private val handler = Handler(Looper.getMainLooper())
    private var activity: ActivityPluginBinding? = null
    private var pendingAdmin: MethodChannel.Result? = null

    private val admin: ComponentName by lazy { ComponentName(context, AdminReceiver::class.java) }
    private val policy: DevicePolicyManager by lazy {
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        context = binding.applicationContext
        methods = MethodChannel(binding.binaryMessenger, "tamper_guard")
        methods.setMethodCallHandler(this)
        windows = EventChannel(binding.binaryMessenger, "tamper_guard/windows")
        windows.setStreamHandler(windowSinks)
        texts = EventChannel(binding.binaryMessenger, "tamper_guard/texts")
        texts.setStreamHandler(textSinks)
        enabledHandler = EnabledHandler(context)
        enabled = EventChannel(binding.binaryMessenger, "tamper_guard/enabled")
        enabled.setStreamHandler(enabledHandler)
    }

    // Detaching a stream handler does not cancel a live stream, so each one
    // is cancelled by hand; a dead engine must not keep receiving events.
    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        methods.setMethodCallHandler(null)
        windows.setStreamHandler(null)
        texts.setStreamHandler(null)
        enabled.setStreamHandler(null)
        windowSinks.onCancel(null)
        textSinks.onCancel(null)
        enabledHandler.onCancel(null)
        settleAdmin()
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "isServiceEnabled" -> result.success(GuardService.isEnabled(context))
            "openAccessibilitySettings" -> {
                context.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                result.success(null)
            }
            "setRules" -> {
                val rules = (call.arguments as List<*>).map { GuardRule.fromMap(it as Map<*, *>) }
                GuardStore.saveRules(context, rules)
                result.success(null)
            }
            "setTextWatches" -> {
                val watches = (call.arguments as List<*>).map { TextWatch.fromMap(it as Map<*, *>) }
                GuardStore.saveWatches(context, watches)
                GuardService.instance?.resetTexts()
                result.success(null)
            }
            "performGlobalAction" -> {
                val action = when (call.arguments as String) {
                    "back" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
                    "home" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
                    else -> null
                }
                result.success(action != null && GuardService.instance?.performGlobalAction(action) == true)
            }
            "isDeviceAdminActive" -> result.success(policy.isAdminActive(admin))
            "requestDeviceAdmin" -> requestAdmin(call.argument<String>("explanation"), result)
            "removeDeviceAdmin" -> removeAdmin(result, attempt = 0)
            "setDeviceAdminDisableWarning" -> {
                GuardStore.saveWarning(context, call.arguments as String?)
                result.success(null)
            }
            else -> result.notImplemented()
        }
    }

    private fun requestAdmin(explanation: String?, result: MethodChannel.Result) {
        if (policy.isAdminActive(admin)) {
            result.success(true)
            return
        }
        val binding = activity
        if (binding == null) {
            result.error("NO_ACTIVITY", "Device admin can only be requested from a foreground activity", null)
            return
        }
        if (pendingAdmin != null) {
            result.error("IN_PROGRESS", "A device admin request is already showing", null)
            return
        }
        pendingAdmin = result
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
        if (explanation != null) intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, explanation)
        binding.activity.startActivityForResult(intent, REQUEST_ADMIN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_ADMIN) return false
        settleAdmin()
        return true
    }

    private fun settleAdmin() {
        pendingAdmin?.success(policy.isAdminActive(admin))
        pendingAdmin = null
    }

    // Removal is asynchronous, so the answer waits until the admin is gone.
    private fun removeAdmin(result: MethodChannel.Result, attempt: Int) {
        if (!policy.isAdminActive(admin)) {
            result.success(true)
            return
        }
        if (attempt == 0) policy.removeActiveAdmin(admin)
        if (attempt >= REMOVE_ATTEMPTS) {
            result.success(false)
            return
        }
        handler.postDelayed({ removeAdmin(result, attempt + 1) }, REMOVE_POLL_MILLIS)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding
        binding.addActivityResultListener(this)
    }

    // A rotation keeps the admin prompt and its result; a real detach ends it.
    override fun onDetachedFromActivityForConfigChanges() {
        activity?.removeActivityResultListener(this)
        activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) =
        onAttachedToActivity(binding)

    override fun onDetachedFromActivity() {
        onDetachedFromActivityForConfigChanges()
        settleAdmin()
    }

    private class SinkHandler(
        private val sinks: MutableSet<EventChannel.EventSink>,
    ) : EventChannel.StreamHandler {
        private var sink: EventChannel.EventSink? = null

        override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
            sink = events
            sinks.add(events)
        }

        override fun onCancel(arguments: Any?) {
            sink?.let { sinks.remove(it) }
            sink = null
        }
    }

    private class EnabledHandler(private val context: Context) : EventChannel.StreamHandler {
        private var observer: ContentObserver? = null

        override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    events.success(GuardService.isEnabled(context))
                }
            }
            // The service's own switch, and the global accessibility switch.
            for (key in listOf(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, Settings.Secure.ACCESSIBILITY_ENABLED)) {
                context.contentResolver.registerContentObserver(Settings.Secure.getUriFor(key), false, observer)
            }
            this.observer = observer
            events.success(GuardService.isEnabled(context))
        }

        override fun onCancel(arguments: Any?) {
            observer?.let { context.contentResolver.unregisterContentObserver(it) }
            observer = null
        }
    }

    private companion object {
        const val REQUEST_ADMIN = 0x7a6d
        const val REMOVE_ATTEMPTS = 20
        const val REMOVE_POLL_MILLIS = 100L
    }
}
