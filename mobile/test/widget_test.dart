import 'package:cito_mobile/core/cito_theme.dart';
import 'package:cito_mobile/widgets/cito_widgets.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('shows an unmistakable production environment warning', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: buildCitoTheme(Brightness.light),
        home: const Scaffold(
          body: EnvironmentBanner(environment: 'PRODUCTION'),
        ),
      ),
    );

    expect(find.text('PRODUCTION · Real customer and financial data'), findsOneWidget);
    expect(find.byIcon(Icons.warning_amber_rounded), findsOneWidget);
  });

  testWidgets('renders the Cito brand mark', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: buildCitoTheme(Brightness.light),
        home: const Scaffold(body: CitoLogo()),
      ),
    );

    expect(find.text('C'), findsOneWidget);
  });
}
