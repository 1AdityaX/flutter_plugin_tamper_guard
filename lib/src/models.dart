/// What the service does the instant a matching window is on screen.
enum GuardAction { none, back, home }

/// A window to react to: any window of one of [packages], narrowed by the
/// activity or dialog class names of the window (exact in [classNames], or
/// by suffix in [classNameSuffixes] for manufacturers that rename a screen),
/// by a view with a given id (and text), or by [text] found anywhere in the
/// window when no [viewId] is given.
///
/// Class names come from the window's own accessibility event, so a rule
/// with [classNames] or [classNameSuffixes] fires the moment such a window
/// appears. A rule with a
/// [viewId] or [text] also fires on later changes inside a matching window,
/// and keeps acting until it is gone, so a dialog over the window does not
/// shield it. A [text] rule without a [viewId] matches the text wherever it
/// appears, which holds across builds that name their views differently.
class GuardRule {
  const GuardRule({
    required this.packages,
    this.classNames = const {},
    this.classNameSuffixes = const {},
    this.viewId,
    this.text,
    this.action = GuardAction.back,
    this.shield = Duration.zero,
  });

  final Set<String> packages;
  final Set<String> classNames;

  /// Matched against the end of the window's class name, so
  /// `DeviceAdminAdd` covers both the stock screen and a manufacturer's
  /// `SecDeviceAdminAdd`.
  final Set<String> classNameSuffixes;

  /// A fully qualified view id such as `com.android.settings:id/admin_name`.
  final String? viewId;

  /// The exact (trimmed) text the view must show; any text when null.
  final String? text;
  final GuardAction action;

  /// How long a touch-consuming overlay covers the screen after the rule
  /// fires, so taps land on it and not on the window being closed.
  final Duration shield;

  Map<String, Object?> toMap() => {
        'packages': [...packages],
        'classNames': [...classNames],
        'classNameSuffixes': [...classNameSuffixes],
        'viewId': viewId,
        'text': text,
        'action': action.name,
        'shieldMillis': shield.inMilliseconds,
      };
}

/// A view whose text is streamed whenever it changes.
class TextWatch {
  const TextWatch({required this.packageName, required this.viewId});

  final String packageName;

  /// A fully qualified view id such as `com.android.chrome:id/url_bar`.
  final String viewId;

  Map<String, Object?> toMap() =>
      {'packageName': packageName, 'viewId': viewId};
}

/// A window that came to the front.
class WindowChange {
  const WindowChange({required this.packageName, this.className});

  factory WindowChange.fromMap(Map<Object?, Object?> map) => WindowChange(
        packageName: map['packageName'] as String,
        className: map['className'] as String?,
      );

  final String packageName;

  /// The activity or dialog class, when the window reported one.
  final String? className;
}

/// The new text of a watched view.
class TextChange {
  const TextChange({
    required this.packageName,
    required this.viewId,
    required this.text,
  });

  factory TextChange.fromMap(Map<Object?, Object?> map) => TextChange(
        packageName: map['packageName'] as String,
        viewId: map['viewId'] as String,
        text: map['text'] as String,
      );

  final String packageName;
  final String viewId;
  final String text;
}
