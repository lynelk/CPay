import 'package:flutter/foundation.dart';

import '../core/cito_api_client.dart';
import '../models/cito_models.dart';
import '../services/cito_repository.dart';

enum CitoAuthState { initializing, signedOut, signedIn }

class CitoController extends ChangeNotifier {
  CitoController({required this.repository});

  final CitoRepository repository;

  CitoAuthState authState = CitoAuthState.initializing;
  MerchantSession? session;
  DashboardSummary? dashboard;
  List<CitoTransaction> transactions = const <CitoTransaction>[];
  MerchantStatement? statement;
  List<ServiceEntitlement> services = const <ServiceEntitlement>[];
  List<CitoNotification> notifications = const <CitoNotification>[];
  List<SupportCase> supportCases = const <SupportCase>[];
  bool refreshing = false;
  String? lastError;
  DateTime? lastUpdated;

  Future<void> initialize() async {
    try {
      session = await repository.restoreSession();
      authState = session == null ? CitoAuthState.signedOut : CitoAuthState.signedIn;
      notifyListeners();
      if (session != null) await refreshAll();
    } on Object catch (error) {
      lastError = _message(error);
      authState = CitoAuthState.signedOut;
      notifyListeners();
    }
  }

  Future<void> login({
    required String accountNumber,
    required String username,
    required String password,
  }) async {
    lastError = null;
    notifyListeners();
    try {
      session = await repository.login(
        accountNumber: accountNumber,
        username: username,
        password: password,
      );
      authState = CitoAuthState.signedIn;
      notifyListeners();
      await refreshAll();
    } on Object catch (error) {
      lastError = _message(error);
      notifyListeners();
      rethrow;
    }
  }

  Future<void> logout() async {
    refreshing = true;
    notifyListeners();
    try {
      await repository.logout();
    } finally {
      session = null;
      dashboard = null;
      transactions = const <CitoTransaction>[];
      statement = null;
      services = const <ServiceEntitlement>[];
      notifications = const <CitoNotification>[];
      supportCases = const <SupportCase>[];
      refreshing = false;
      authState = CitoAuthState.signedOut;
      notifyListeners();
    }
  }

  Future<void> refreshAll() async {
    if (refreshing) return;
    refreshing = true;
    lastError = null;
    notifyListeners();
    final errors = <String>[];

    await Future.wait<void>(<Future<void>>[
      _load(() async => dashboard = await repository.dashboard(), errors),
      _load(() async => transactions = await repository.transactions(), errors),
      _load(() async => statement = await repository.statement(), errors),
      _load(() async => services = await repository.services(), errors),
      _load(() async => notifications = await repository.notifications(), errors),
      _load(() async => supportCases = await repository.supportCases(), errors),
    ]);

    refreshing = false;
    lastUpdated = DateTime.now();
    if (errors.isNotEmpty) lastError = errors.first;
    notifyListeners();
  }

  Future<void> searchTransactions(String query) async {
    try {
      transactions = await repository.transactions(query: query);
      lastError = null;
    } on Object catch (error) {
      lastError = _message(error);
    }
    notifyListeners();
  }

  Future<String> initiateCollection({
    required String account,
    required String description,
    required double amount,
  }) async {
    final message = await repository.initiateCollection(
      account: account,
      description: description,
      amount: amount,
    );
    await refreshAll();
    return message;
  }

  Future<String> createSupportCase({
    required String subject,
    required String description,
    String category = 'GENERAL_SUPPORT',
    String severity = 'MEDIUM',
    String? transactionReference,
  }) async {
    final reference = await repository.createSupportCase(
      subject: subject,
      description: description,
      category: category,
      severity: severity,
      transactionReference: transactionReference,
    );
    supportCases = await repository.supportCases();
    notifyListeners();
    return reference;
  }

  Future<Map<String, dynamic>> transactionTimeline(String reference) =>
      repository.transactionTimeline(reference);

  Future<void> markNotificationRead(String reference) async {
    await repository.markNotificationRead(reference);
    notifications = notifications
        .map(
          (item) => item.reference == reference
              ? CitoNotification(
                  reference: item.reference,
                  title: item.title,
                  message: item.message,
                  severity: item.severity,
                  createdAt: item.createdAt,
                  readAt: DateTime.now(),
                )
              : item,
        )
        .toList(growable: false);
    notifyListeners();
  }

  Future<void> _load(Future<void> Function() operation, List<String> errors) async {
    try {
      await operation();
    } on CitoApiException catch (error) {
      if (error.code == '107' || error.statusCode == 401) {
        await repository.api.clearSession();
        session = null;
        authState = CitoAuthState.signedOut;
      }
      errors.add(error.message);
    } on Object catch (error) {
      errors.add(_message(error));
    }
  }

  static String _message(Object error) =>
      error is CitoApiException ? error.message : error.toString().replaceFirst('Exception: ', '');
}
