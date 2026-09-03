import 'package:flutter/material.dart';

import 'core/app_config.dart';
import 'core/cito_theme.dart';
import 'screens/cito_shell.dart';
import 'screens/login_screen.dart';
import 'state/cito_controller.dart';
import 'widgets/cito_widgets.dart';

class CitoApp extends StatelessWidget {
  const CitoApp({
    required this.config,
    required this.controller,
    super.key,
  });

  final AppConfig config;
  final CitoController controller;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Cito Business',
      debugShowCheckedModeBanner: false,
      theme: buildCitoTheme(Brightness.light),
      darkTheme: buildCitoTheme(Brightness.dark),
      themeMode: ThemeMode.system,
      home: AnimatedBuilder(
        animation: controller,
        builder: (context, _) {
          return switch (controller.authState) {
            CitoAuthState.initializing => const _LaunchGate(),
            CitoAuthState.signedOut => LoginScreen(config: config, controller: controller),
            CitoAuthState.signedIn => CitoShell(config: config, controller: controller),
          };
        },
      ),
    );
  }
}

class _LaunchGate extends StatelessWidget {
  const _LaunchGate();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: <Widget>[
              const CitoLogo(size: 72),
              const SizedBox(height: 20),
              Text(
                'Cito Business',
                style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                      fontWeight: FontWeight.w900,
                      color: CitoColours.navy,
                    ),
              ),
              const SizedBox(height: 18),
              const SizedBox.square(
                dimension: 28,
                child: CircularProgressIndicator(strokeWidth: 3),
              ),
              const SizedBox(height: 12),
              Text(
                'Restoring your secure session…',
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: Theme.of(context).colorScheme.onSurfaceVariant,
                    ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
