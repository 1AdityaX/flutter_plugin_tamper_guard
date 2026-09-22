import 'package:flutter/services.dart';

import 'models.dart';

/// An Android accessibility service that closes matching windows natively
/// the instant they appear, streams window and view-text changes, and a
/// device admin whose deactivation shows a warning.
class TamperGuard {
  const TamperGuard._();

  static const MethodChannel _methods = MethodChannel('tamper_guard');
  static const EventChannel _windows = EventChannel('tamper_guard/windows');
  static const EventChannel _texts = EventChannel('tamper_guard/texts');
  static const EventChannel _enabled = EventChannel('tamper_guard/enabled');

  /// Whether the user has switched the service on in Accessibility settings.
  static Future<bool> get isServiceEnabled async =>
      await _methods.invokeMethod<bool>('isServiceEnabled') ?? false;

  /// Emits the current state on listen, then every change.
  static Stream<bool> get serviceEnabledChanges =>
      _enabled.receiveBroadcastStream().map((event) => event as bool);

  static Future<void> openAccessibilitySettings() =>
      _methods.invokeMethod<void>('openAccessibilitySettings');

  /// Replaces the rules the service enforces. They persist across restarts
  /// and apply even when no Flutter engine is running.
  static Future<void> setRules(List<GuardRule> rules) => _methods
      .invokeMethod<void>('setRules', [for (final rule in rules) rule.toMap()]);

  /// Replaces the views whose text feeds [textChanges].
  static Future<void> setTextWatches(List<TextWatch> watches) =>
      _methods.invokeMethod<void>('setTextWatches', [
        for (final watch in watches) watch.toMap(),
      ]);

  /// Every window that comes to the front in a package the service is
  /// configured for.
  static Stream<WindowChange> get windowChanges => _windows
      .receiveBroadcastStream()
      .map((event) => WindowChange.fromMap(event as Map<Object?, Object?>));

  /// Text changes of watched views, one event per change.
  static Stream<TextChange> get textChanges => _texts
      .receiveBroadcastStream()
      .map((event) => TextChange.fromMap(event as Map<Object?, Object?>));

  /// Presses Back or Home. False when the service is off or [action] is
  /// [GuardAction.none].
  static Future<bool> performGlobalAction(GuardAction action) async =>
      await _methods.invokeMethod<bool>('performGlobalAction', action.name) ??
      false;

  static Future<bool> get isDeviceAdminActive async =>
      await _methods.invokeMethod<bool>('isDeviceAdminActive') ?? false;

  /// Shows Android's activation screen and resolves once the user has
  /// answered. Throws a [PlatformException] (`NO_ACTIVITY`) when no activity
  /// is in the foreground, so call it from the UI, and (`IN_PROGRESS`) while
  /// an earlier prompt is still open.
  static Future<bool> requestDeviceAdmin({String? explanation}) async =>
      await _methods.invokeMethod<bool>('requestDeviceAdmin', {
        'explanation': explanation,
      }) ??
      false;

  /// Resolves once the admin is gone; false if Android has not removed it
  /// within a couple of seconds.
  static Future<bool> removeDeviceAdmin() async =>
      await _methods.invokeMethod<bool>('removeDeviceAdmin') ?? false;

  /// The text Settings shows in a confirmation dialog when the user taps
  /// Deactivate. With null, Android deactivates without asking.
  static Future<void> setDeviceAdminDisableWarning(String? warning) =>
      _methods.invokeMethod<void>('setDeviceAdminDisableWarning', warning);

  /// What the service does the moment the user taps Deactivate for this
  /// app's admin: leave the screen and shield it, so the confirmation that a
  /// warning adds is never answered. [GuardAction.none] turns it off. Needs
  /// the service to be on; the warning alone still shows without it.
  static Future<void> setDeviceAdminDisableAction(
    GuardAction action, {
    Duration shield = Duration.zero,
  }) =>
      _methods.invokeMethod<void>('setDeviceAdminDisableAction', {
        'action': action.name,
        'shieldMillis': shield.inMilliseconds,
      });
}
