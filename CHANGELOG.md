# Changelog

## 0.3.0 - 2026-09-22

- `setDeviceAdminDisableAction`: the service leaves and shields the screen the
  moment the user taps Deactivate for the app's admin, so the confirmation a
  warning adds is never answered
- `classNameSuffixes` on a rule, for manufacturers that rename a screen
- Text rules also match a view's content description
- Logs rule fires and rule updates under the `TamperGuard` tag
- Homepage points at the package's repository

## 0.2.0 - 2026-09-22

- A guard rule with `text` but no `viewId` matches that text anywhere in the
  window, so a screen can be caught by its wording across OEM builds that name
  their views and activities differently
- Windows-changed events are handled, so a floating window still triggers a rule

## 0.1.0 - 2026-09-22

- Accessibility service that enforces guard rules natively: press Back or
  Home and shield the screen the instant a matching window appears
- Window and view-text change streams
- Device admin receiver with a configurable deactivation warning, plus
  activate, status and remove calls
