import 'dart:async';

import 'package:flutter/material.dart';

import 'app.dart';
import 'core/app_config.dart';
import 'core/cito_api_client.dart';
import 'services/cito_repository.dart';
import 'state/cito_controller.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  final config = AppConfig.fromEnvironment();
  final api = CitoApiClient(config: config);
  await api.initialize();
  final controller = CitoController(repository: CitoRepository(api));

  runApp(CitoApp(config: config, controller: controller));
  unawaited(controller.initialize());
}
