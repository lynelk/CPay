import 'package:cito_mobile/models/cito_models.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('merchant session normalizes identifiers and privileges', () {
    final session = MerchantSession.fromJson(<String, dynamic>{
      'merchant_id': 17,
      'account_number': 'CITO-17',
      'username': 'merchant@example.com',
      'name': 'Acme Merchant',
      'privileges': <Map<String, String>>[
        <String, String>{'privilege': 'ACCESS_TRANSACTION_LOG'},
      ],
    });

    expect(session.merchantId, 17);
    expect(session.displayName, 'Acme Merchant');
    expect(session.hasPrivilege('access_transaction_log'), isTrue);
  });

  test('dashboard summary never invents missing values', () {
    final summary = DashboardSummary.fromJson(<String, dynamic>{});
    expect(summary.payIns, 0);
    expect(summary.payOuts, 0);
    expect(summary.transactions, 0);
    expect(summary.channels, isEmpty);
  });

  test('transaction maps the merchant and provider references', () {
    final transaction = CitoTransaction.fromJson(<String, dynamic>{
      'tx_merchant_ref': 'ORDER-100',
      'tx_gateway_ref': 'PROVIDER-200',
      'status': 'SUCCESSFUL',
      'tx_type': 'PAY_IN',
      'original_amount': '25000',
      'created_on': '2026-09-04T08:00:00Z',
    });

    expect(transaction.reference, 'ORDER-100');
    expect(transaction.providerReference, 'PROVIDER-200');
    expect(transaction.amount, 25000);
  });
}
