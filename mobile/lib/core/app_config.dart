import 'package:flutter/foundation.dart';

@immutable
class AppConfig {
  const AppConfig({required this.apiBaseUrl, required this.environment});

  factory AppConfig.fromEnvironment() {
    const rawBase = String.fromEnvironment(
      'CITO_API_BASE_URL',
      defaultValue: 'https://cito.coresynergi.es',
    );
    const rawEnvironment = String.fromEnvironment(
      'CITO_ENVIRONMENT',
      defaultValue: 'sandbox',
    );
    return AppConfig(
      apiBaseUrl: normalizeBaseUrl(rawBase),
      environment: rawEnvironment.trim().toLowerCase(),
    );
  }

  final String apiBaseUrl;
  final String environment;

  bool get isProduction => environment == 'production';

  Uri resolve(String path) {
    final normalizedPath = path.startsWith('/') ? path : '/$path';
    return Uri.parse('$apiBaseUrl$normalizedPath');
  }

  static String normalizeBaseUrl(String value) {
    final trimmed = value.trim();
    if (trimmed.isEmpty) {
      throw const FormatException('CITO_API_BASE_URL cannot be empty.');
    }
    final uri = Uri.parse(trimmed);
    if (!uri.hasScheme || uri.host.isEmpty) {
      throw FormatException('Invalid CITO_API_BASE_URL: $trimmed');
    }
    if (uri.scheme != 'https' && !kDebugMode) {
      throw const FormatException('Production mobile builds require HTTPS.');
    }
    return trimmed.endsWith('/')
        ? trimmed.substring(0, trimmed.length - 1)
        : trimmed;
  }
}
