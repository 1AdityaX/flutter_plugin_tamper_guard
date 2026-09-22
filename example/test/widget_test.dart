import 'package:flutter_test/flutter_test.dart';
import 'package:tamper_guard_example/main.dart';

void main() {
  testWidgets('shows the controls', (tester) async {
    await tester.pumpWidget(const ExampleApp());

    expect(find.text('Accessibility service'), findsOneWidget);
    expect(find.text('Device admin'), findsOneWidget);
  });
}
