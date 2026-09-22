import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:tamper_guard/tamper_guard.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const methods = MethodChannel('tamper_guard');
  final calls = <MethodCall>[];
  Object? reply;

  setUp(() {
    calls.clear();
    reply = null;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(methods, (call) async {
      calls.add(call);
      return reply;
    });
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(methods, null);
  });

  test('rules are sent as maps with defaults filled in', () async {
    await TamperGuard.setRules(const [
      GuardRule(
        packages: {'com.android.settings'},
        classNames: {'com.android.settings.DeviceAdminAdd'},
        viewId: 'com.android.settings:id/admin_name',
        text: 'My app',
        shield: Duration(milliseconds: 600),
      ),
      GuardRule(packages: {'com.example'}, action: GuardAction.home),
    ]);

    expect(calls.single.method, 'setRules');
    expect(calls.single.arguments, [
      {
        'packages': ['com.android.settings'],
        'classNames': ['com.android.settings.DeviceAdminAdd'],
        'classNameSuffixes': <String>[],
        'viewId': 'com.android.settings:id/admin_name',
        'text': 'My app',
        'action': 'back',
        'shieldMillis': 600,
      },
      {
        'packages': ['com.example'],
        'classNames': <String>[],
        'classNameSuffixes': <String>[],
        'viewId': null,
        'text': null,
        'action': 'home',
        'shieldMillis': 0,
      },
    ]);
  });

  test('text watches and global actions pass their arguments', () async {
    reply = true;
    await TamperGuard.setTextWatches(const [
      TextWatch(
        packageName: 'com.android.chrome',
        viewId: 'com.android.chrome:id/url_bar',
      ),
    ]);
    expect(await TamperGuard.performGlobalAction(GuardAction.back), isTrue);

    expect(calls.first.arguments, [
      {
        'packageName': 'com.android.chrome',
        'viewId': 'com.android.chrome:id/url_bar',
      },
    ]);
    expect(calls.last.method, 'performGlobalAction');
    expect(calls.last.arguments, 'back');
  });

  test('device admin calls carry the explanation, warning and action',
      () async {
    reply = true;
    expect(
      await TamperGuard.requestDeviceAdmin(explanation: 'Keeps the app.'),
      isTrue,
    );
    await TamperGuard.setDeviceAdminDisableWarning('Locked.');
    await TamperGuard.setDeviceAdminDisableAction(
      GuardAction.home,
      shield: const Duration(seconds: 1),
    );
    reply = null;
    expect(await TamperGuard.isDeviceAdminActive, isFalse);

    expect(calls[0].arguments, {'explanation': 'Keeps the app.'});
    expect(calls[1].arguments, 'Locked.');
    expect(calls[2].method, 'setDeviceAdminDisableAction');
    expect(calls[2].arguments, {'action': 'home', 'shieldMillis': 1000});
    expect(calls[3].method, 'isDeviceAdminActive');
  });

  test('streams decode the maps the service emits', () async {
    const windows = EventChannel('tamper_guard/windows');
    const texts = EventChannel('tamper_guard/texts');
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockStreamHandler(
      windows,
      MockStreamHandler.inline(
        onListen: (_, sink) {
          sink.success({
            'packageName': 'com.android.settings',
            'className': 'com.android.settings.DeviceAdminAdd',
          });
          sink.endOfStream();
        },
      ),
    );
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockStreamHandler(
      texts,
      MockStreamHandler.inline(
        onListen: (_, sink) {
          sink.success({
            'packageName': 'com.android.chrome',
            'viewId': 'com.android.chrome:id/url_bar',
            'text': 'example.com',
          });
          sink.endOfStream();
        },
      ),
    );

    final window = await TamperGuard.windowChanges.first;
    final text = await TamperGuard.textChanges.first;

    expect(window.packageName, 'com.android.settings');
    expect(window.className, 'com.android.settings.DeviceAdminAdd');
    expect(text.viewId, 'com.android.chrome:id/url_bar');
    expect(text.text, 'example.com');
  });
}
