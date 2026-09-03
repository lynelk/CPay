import 'package:cito_mobile/core/app_config.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('AppConfig', () {
    test('normalizes a trailing slash', () {
      expect(
        AppConfig.normalizeBaseUrl('https://cito.coresynergi.es/'),
        'https://cito.coresynergi.es',
      );
    });

    test('resolves API paths against the configured origin', () {
      const config = AppConfig(
        apiBaseUrl: 'https://cito.coresynergi.es',
        environment: 'production',
      );
      expect(
        config.resolve('/api/v2/portal/dashboard/summary').toString(),
        'https://cito.coresynergi.es/api/v2/portal/dashboard/summary',
      );
      expect(config.isProduction, isTrue);
    });

    test('rejects an empty base URL', () {
      expect(() => AppConfig.normalizeBaseUrl(''), throwsFormatException);
    });
  });
}
