import '../core/cito_api_client.dart';
import '../models/cito_models.dart';

class CitoRepository {
  const CitoRepository(this.api);

  final CitoApiClient api;

  Future<MerchantSession?> restoreSession() async {
    final stored = await api.readStoredUser();
    if (stored == null) return null;
    final response = await api.postJson('/auth/isMerchantUserLoggedIn', body: <String, dynamic>{});
    if (response['code']?.toString() == '000' && response['message']?.toString() == 'true') {
      return MerchantSession.fromJson(stored);
    }
    await api.clearSession();
    return null;
  }

  Future<MerchantSession> login({
    required String accountNumber,
    required String username,
    required String password,
  }) async {
    final response = await api.postJson(
      '/auth/authenticateMerchantUser',
      body: <String, dynamic>{
        'account_number': accountNumber.trim(),
        'username': username.trim(),
        'password': password,
      },
    );
    final code = response['code']?.toString();
    if (code != '000') {
      throw CitoApiException(
        response['message']?.toString() ?? 'Sign-in failed.',
        code: code,
      );
    }
    final rawUser = response['user'];
    if (rawUser is! Map<String, dynamic>) {
      throw const CitoApiException('Cito did not return a valid merchant profile.');
    }
    await api.storeUser(rawUser);
    return MerchantSession.fromJson(rawUser);
  }

  Future<void> logout() async {
    try {
      await api.postJson('/auth/logoutMerchantUser', body: <String, dynamic>{});
    } finally {
      await api.clearSession();
    }
  }

  Future<DashboardSummary> dashboard() async {
    final response = await api.getJson('/api/v2/portal/dashboard/summary');
    return DashboardSummary.fromJson(response);
  }

  Future<MerchantStatement> statement() async {
    final now = DateTime.now();
    final from = DateTime(now.year, now.month - 6, now.day);
    final response = await api.postJson(
      '/transactions/getMerchantStatementByMerchant',
      body: <String, dynamic>{
        'search_rules': <String, dynamic>{
          'start_date': _date(from),
          'end_date': _date(now),
        },
        'pageSize': 100,
        'searchingValue': <String, dynamic>{'value': '', 'category': 'all'},
        'sort': 'asc',
      },
    );
    _requireLegacySuccess(response);
    final rows = response['data'];
    return MerchantStatement(
      availableBalance: (response['balances'] ?? '').toString(),
      entries: rows is List<dynamic>
          ? rows
              .whereType<Map<String, dynamic>>()
              .map(StatementEntry.fromJson)
              .toList(growable: false)
          : const <StatementEntry>[],
    );
  }

  Future<List<CitoTransaction>> transactions({String query = ''}) async {
    final now = DateTime.now();
    final from = DateTime(now.year, now.month - 6, now.day);
    final response = await api.postJson(
      '/transactions/getMerchantTransactions',
      body: <String, dynamic>{
        'search_rules': <String, dynamic>{
          'start_date': _date(from),
          'end_date': _date(now),
          'status': '',
          'tx_type': '',
        },
        'pageSize': 100,
        'searchingValue': <String, dynamic>{
          'value': query.trim(),
          'category': 'all',
        },
        'sort': 'asc',
      },
    );
    _requireLegacySuccess(response);
    final rows = response['data'];
    if (rows is! List<dynamic>) return const <CitoTransaction>[];
    return rows
        .whereType<Map<String, dynamic>>()
        .map(CitoTransaction.fromJson)
        .toList(growable: false);
  }

  Future<String> initiateCollection({
    required String account,
    required String description,
    required double amount,
  }) async {
    final response = await api.postJson(
      '/transactions/addPayInTransaction',
      body: <String, dynamic>{
        'account': account.trim(),
        'tx_description': description.trim(),
        'amount': amount.toStringAsFixed(0),
      },
    );
    _requireLegacySuccess(response);
    return response['message']?.toString() ?? 'Collection initiated.';
  }

  Future<List<ServiceEntitlement>> services() async {
    final response = await api.getJson('/api/v2/merchant-self-service/cito/overview');
    final rows = response['features'];
    if (rows is! List<dynamic>) return const <ServiceEntitlement>[];
    return rows
        .whereType<Map<String, dynamic>>()
        .map(ServiceEntitlement.fromJson)
        .toList(growable: false);
  }

  Future<List<CitoNotification>> notifications() async {
    final response = await api.getJson('/api/v2/notifications');
    final rows = response['notifications'];
    if (rows is! List<dynamic>) return const <CitoNotification>[];
    return rows
        .whereType<Map<String, dynamic>>()
        .map(CitoNotification.fromJson)
        .toList(growable: false);
  }

  Future<void> markNotificationRead(String reference) async {
    await api.patchJson('/api/v2/notifications/${Uri.encodeComponent(reference)}/read');
  }

  Future<List<SupportCase>> supportCases() async {
    final response = await api.getJson('/api/v2/support/cases');
    final rows = response['cases'];
    if (rows is! List<dynamic>) return const <SupportCase>[];
    return rows
        .whereType<Map<String, dynamic>>()
        .map(SupportCase.fromJson)
        .toList(growable: false);
  }

  Future<String> createSupportCase({
    required String subject,
    required String description,
    String category = 'GENERAL_SUPPORT',
    String severity = 'MEDIUM',
    String? transactionReference,
  }) async {
    final response = await api.postJson(
      '/api/v2/support/cases',
      body: <String, dynamic>{
        'subject': subject.trim(),
        'description': description.trim(),
        'category': category,
        'severity': severity,
        if (transactionReference?.trim().isNotEmpty == true)
          'transactionReference': transactionReference!.trim(),
      },
    );
    return (response['caseReference'] ?? response['case_reference'] ?? 'CREATED').toString();
  }

  Future<Map<String, dynamic>> transactionTimeline(String reference) =>
      api.getJson('/api/v2/transactions/${Uri.encodeComponent(reference)}/timeline');

  static void _requireLegacySuccess(Map<String, dynamic> response) {
    final code = response['code']?.toString();
    if (code == '000') return;
    if (code == '107') {
      throw const CitoApiException('Your Cito session has expired.', code: '107');
    }
    if (code == '110') {
      throw CitoApiException(
        response['message']?.toString() ?? 'Your role does not permit this action.',
        code: code,
      );
    }
    throw CitoApiException(
      response['message']?.toString() ?? response['error']?.toString() ?? 'Cito could not complete the request.',
      code: code,
    );
  }

  static String _date(DateTime value) =>
      '${value.year.toString().padLeft(4, '0')}-${value.month.toString().padLeft(2, '0')}-${value.day.toString().padLeft(2, '0')}';
}
