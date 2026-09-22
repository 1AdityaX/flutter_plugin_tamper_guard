# Tamper Guard

Keeps an Android app from being switched off behind its back. An accessibility
service reacts to windows *inside the service itself*, with no round trip to
Dart, so a guarded screen is gone within a few frames of appearing, and a
device admin receiver that shows a warning before it can be deactivated.

Built for app blockers, parental controls and focus apps that need a
lock the user cannot race, but general enough for anything that must react
to a window the moment it shows.

## Features

- Guard rules enforced natively: press Back or Home, and optionally cover the
  screen with a touch-consuming shield, the instant a matching window appears
- Match a window by package, by activity or dialog class name, and/or by a
  view id with a given text anywhere inside it
- Rules persist and run even when no Flutter engine is alive
- Stream every window that comes to the front, with its class name
- Stream the text of chosen views (an address bar, a title) as it changes
- Device admin: activate, check, remove, a deactivation warning that turns
  Settings' one-tap Deactivate into a confirmation dialog, and a deactivation
  action that leaves and shields the screen the moment Deactivate is tapped,
  so that confirmation is never answered

## Getting started

```yaml
dependencies:
  tamper_guard: ^0.3.0
```

| Android | iOS | macOS | Web | Linux | Windows |
|---------|-----|-------|-----|-------|---------|
| ✅      | ❌  | ❌    | ❌  | ❌    | ❌      |

### Declare the service and the receiver

In `android/app/src/main/AndroidManifest.xml`, inside `<application>`:

```xml
<service
    android:name="dev.aditya.tamper_guard.GuardService"
    android:exported="false"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_service" />
</service>

<receiver
    android:name="dev.aditya.tamper_guard.AdminReceiver"
    android:exported="true"
    android:label="@string/device_admin_label"
    android:description="@string/device_admin_description"
    android:permission="android.permission.BIND_DEVICE_ADMIN">
    <meta-data
        android:name="android.app.device_admin"
        android:resource="@xml/tamper_guard_admin" />
    <intent-filter>
        <action android:name="android.app.action.DEVICE_ADMIN_ENABLED" />
        <action android:name="android.app.action.DEVICE_ADMIN_DISABLE_REQUESTED" />
        <action android:name="android.app.action.DEVICE_ADMIN_DISABLED" />
    </intent-filter>
</receiver>
```

`@xml/tamper_guard_admin` ships with the plugin and requests no policy. The
three strings are yours to define in `res/values/strings.xml`; Android shows
them on the accessibility and device-admin screens:

```xml
<string name="accessibility_service_description">Closes the Settings app while a lock is on.</string>
<string name="device_admin_label">My app lock</string>
<string name="device_admin_description">Keeps My app installed while a lock is on.</string>
```

### Configure the service

`android/app/src/main/res/xml/accessibility_service.xml`:

```xml
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/accessibility_service_description"
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged|typeViewTextChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagReportViewIds|flagRetrieveInteractiveWindows"
    android:canRetrieveWindowContent="true"
    android:notificationTimeout="0"
    android:packageNames="com.android.settings,com.android.chrome" />
```

`packageNames` decides which packages the service hears from at all: a rule
or a watch for a package missing from it never fires, silently. Keep the list
to the packages you react to, which is what keeps the service fast, or omit
the attribute to hear from every package. `notificationTimeout="0"` delivers
each event at once instead of batching. `flagReportViewIds` is needed for
view rules and text watches, `flagRetrieveInteractiveWindows` for view rules
to see a screen under a dialog.

## Usage

```dart
import 'package:tamper_guard/tamper_guard.dart';

if (!await TamperGuard.isServiceEnabled) {
  await TamperGuard.openAccessibilitySettings();
}

// Send the screen where this app's device admin could be deactivated home,
// and keep taps off it while that happens. That screen disables ordinary
// overlay windows, so only the service's shield can cover it; Home rather
// than Back, because Settings clears the screen from its task on re-entry
// where Back would only return to the admin list.
await TamperGuard.setRules([
  GuardRule(
    packages: {'com.android.settings'},
    viewId: 'com.android.settings:id/admin_name',
    text: 'My app',
    action: GuardAction.home,
    shield: Duration(milliseconds: 700),
  ),
  GuardRule(
    packages: {'com.android.settings'},
    classNames: {
      'com.android.settings.applications.specialaccess.deviceadmin.DeviceAdminAdd',
    },
    action: GuardAction.home,
    shield: Duration(milliseconds: 700),
  ),
]);

// Read Chrome's address bar as it changes.
await TamperGuard.setTextWatches([
  TextWatch(packageName: 'com.android.chrome', viewId: 'com.android.chrome:id/url_bar'),
]);
TamperGuard.textChanges.listen((change) => debugPrint(change.text));

// Every window that comes to the front, with its activity class.
TamperGuard.windowChanges.listen((window) => debugPrint(window.className));

// Device admin that cannot be deactivated while the service is on: the tap
// on Deactivate asks this app first, the service goes Home and shields the
// screen, and the confirmation the warning adds is never answered.
await TamperGuard.setDeviceAdminDisableWarning('My app is locked.');
await TamperGuard.setDeviceAdminDisableAction(
  GuardAction.home,
  shield: Duration(seconds: 1),
);
await TamperGuard.requestDeviceAdmin(explanation: 'Keeps My app installed.');
```

### How rules match

- `packages` (required): the window's package.
- `classNames`: the activity or dialog class the window reports. Such rules
  fire once, when the window appears.
- `classNameSuffixes`: matched against the end of that class name, so
  `DeviceAdminAdd` covers the stock screen and a manufacturer's
  `SecDeviceAdminAdd` alike.
- `viewId` and `text`: a view found anywhere in a matching package's windows,
  with exactly that (trimmed) text when `text` is given.
- `text` without `viewId`: that exact (trimmed) text on any view in the
  window, whatever its id, which holds across OEM builds that name their views
  differently.
  Rules with a `viewId` or `text` also fire on later changes inside the
  window, and after firing they look again a few times while the user is still
  inside the package, so a dialog on top takes the first Back and the screen
  takes the next.
- `action`: `back` (default), `home`, or `none` to only shield.
- `shield`: how long a translucent, touch-consuming overlay covers the screen
  after the rule fires.

Rules are checked in order and only the first match acts on an event; the
service acts at most once every 250 ms.

## Limits

Nothing here stops Safe mode, a computer with developer tools, or the user
switching the accessibility service off first. Deactivating a device admin
from Settings cannot be refused by any app; the warning only adds a
confirmation, and the rules are what keep the screen out of reach.
