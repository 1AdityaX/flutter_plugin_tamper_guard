import 'dart:async';

import 'package:flutter/material.dart';
import 'package:tamper_guard/tamper_guard.dart';

void main() => runApp(const ExampleApp());

class ExampleApp extends StatelessWidget {
  const ExampleApp({super.key});

  @override
  Widget build(BuildContext context) {
    return const MaterialApp(home: ExamplePage());
  }
}

class ExamplePage extends StatefulWidget {
  const ExamplePage({super.key});

  @override
  State<ExamplePage> createState() => _ExamplePageState();
}

class _ExamplePageState extends State<ExamplePage> {
  bool _serviceEnabled = false;
  bool _adminActive = false;
  bool _guardingSettings = false;
  final List<String> _windows = [];
  String _address = '';
  StreamSubscription<bool>? _enabled;
  StreamSubscription<WindowChange>? _windowChanges;
  StreamSubscription<TextChange>? _textChanges;

  @override
  void initState() {
    super.initState();
    _enabled = TamperGuard.serviceEnabledChanges.listen(
      (enabled) => setState(() => _serviceEnabled = enabled),
    );
    _windowChanges = TamperGuard.windowChanges.listen(
      (window) => setState(() {
        _windows.insert(0, '${window.packageName} ${window.className ?? ''}');
        if (_windows.length > 8) _windows.removeLast();
      }),
    );
    _textChanges = TamperGuard.textChanges.listen(
      (change) => setState(() => _address = change.text),
    );
    unawaited(
      TamperGuard.setTextWatches(const [
        TextWatch(
          packageName: 'com.android.chrome',
          viewId: 'com.android.chrome:id/url_bar',
        ),
      ]),
    );
    unawaited(_refreshAdmin());
  }

  @override
  void dispose() {
    unawaited(_enabled?.cancel());
    unawaited(_windowChanges?.cancel());
    unawaited(_textChanges?.cancel());
    super.dispose();
  }

  Future<void> _refreshAdmin() async {
    final active = await TamperGuard.isDeviceAdminActive;
    if (mounted) setState(() => _adminActive = active);
  }

  Future<void> _guardSettings(bool on) async {
    await TamperGuard.setRules([
      if (on)
        const GuardRule(
          packages: {'com.android.settings'},
          shield: Duration(milliseconds: 600),
        ),
    ]);
    setState(() => _guardingSettings = on);
  }

  Future<void> _toggleAdmin() async {
    if (_adminActive) {
      await TamperGuard.removeDeviceAdmin();
    } else {
      await TamperGuard.setDeviceAdminDisableWarning(
        'Tamper Guard example is guarding this admin.',
      );
      await TamperGuard.requestDeviceAdmin(
        explanation: 'Shows the deactivation warning in Settings.',
      );
    }
    await _refreshAdmin();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Tamper Guard')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          SwitchListTile(
            title: const Text('Accessibility service'),
            subtitle: Text(
              _serviceEnabled ? 'On' : 'Off. Tap to open Settings.',
            ),
            value: _serviceEnabled,
            onChanged: (_) => TamperGuard.openAccessibilitySettings(),
          ),
          SwitchListTile(
            title: const Text('Close the Settings app on sight'),
            subtitle: const Text('Back plus a 600 ms shield'),
            value: _guardingSettings,
            onChanged: _serviceEnabled ? _guardSettings : null,
          ),
          SwitchListTile(
            title: const Text('Device admin'),
            subtitle: Text(
              _adminActive
                  ? 'Active. Deactivating in Settings shows a warning.'
                  : 'Inactive',
            ),
            value: _adminActive,
            onChanged: (_) => _toggleAdmin(),
          ),
          const Divider(),
          ListTile(
            title: const Text('Chrome address bar'),
            subtitle: Text(
              _address.isEmpty ? 'Open Chrome to see it' : _address,
            ),
          ),
          const ListTile(title: Text('Recent windows')),
          for (final window in _windows)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
              child: Text(window, style: const TextStyle(fontSize: 12)),
            ),
        ],
      ),
    );
  }
}
