import 'package:flutter/foundation.dart';

@immutable
class MerchantSession {
  const MerchantSession({
    required this.accountNumber,
    required this.username,
    required this.displayName,
    required this.raw,
    this.merchantId,
    this.email,
    this.privileges = const <String>{},
  });

  factory MerchantSession.fromJson(Map<String, dynamic> json) {
    final privilegeRows = json['privileges'];
    final privileges = <String>{};
    if (privilegeRows is List<dynamic>) {
      for (final row in privilegeRows) {
        if (row is Map<String, dynamic> && row['privilege'] != null) {
          privileges.add(row['privilege'].toString().toUpperCase());
        } else if (row != null) {
          privileges.add(row.toString().toUpperCase());
        }
      }
    }
    final id = int.tryParse((json['merchant_id'] ?? json['merchantId'] ?? '').toString());
    return MerchantSession(
      merchantId: id,
      accountNumber: (json['account_number'] ?? json['accountNumber'] ?? '').toString(),
      username: (json['username'] ?? json['email'] ?? '').toString(),
      email: json['email']?.toString(),
      displayName: (json['name'] ?? json['username'] ?? 'Merchant User').toString(),
      privileges: privileges,
      raw: json,
    );
  }

  final int? merchantId;
  final String accountNumber;
  final String username;
  final String? email;
  final String displayName;
  final Set<String> privileges;
  final Map<String, dynamic> raw;

  bool hasPrivilege(String value) => privileges.contains(value.toUpperCase());
}

@immutable
class ChannelSummary {
  const ChannelSummary({
    required this.code,
    required this.name,
    required this.status,
    required this.environment,
  });

  factory ChannelSummary.fromJson(Map<String, dynamic> json) => ChannelSummary(
        code: (json['channel_code'] ?? json['channelCode'] ?? '').toString(),
        name: (json['display_name'] ?? json['displayName'] ?? json['channel_code'] ?? 'Channel').toString(),
        status: (json['status'] ?? 'NOT_CONFIGURED').toString(),
        environment: (json['environment'] ?? 'SANDBOX').toString(),
      );

  final String code;
  final String name;
  final String status;
  final String environment;
}

@immutable
class DashboardSummary {
  const DashboardSummary({
    this.payIns = 0,
    this.payOuts = 0,
    this.transactions = 0,
    this.environment = 'SANDBOX',
    this.channels = const <ChannelSummary>[],
    this.productionLimit = const <String, dynamic>{},
  });

  factory DashboardSummary.fromJson(Map<String, dynamic> json) {
    final channelRows = json['activeChannels'];
    return DashboardSummary(
      payIns: _number(json['payIns']),
      payOuts: _number(json['payOuts']),
      transactions: _integer(json['transactions']),
      environment: (json['environment'] ?? 'SANDBOX').toString(),
      channels: channelRows is List<dynamic>
          ? channelRows
              .whereType<Map<String, dynamic>>()
              .map(ChannelSummary.fromJson)
              .toList(growable: false)
          : const <ChannelSummary>[],
      productionLimit: json['productionLimit'] is Map<String, dynamic>
          ? json['productionLimit'] as Map<String, dynamic>
          : const <String, dynamic>{},
    );
  }

  final double payIns;
  final double payOuts;
  final int transactions;
  final String environment;
  final List<ChannelSummary> channels;
  final Map<String, dynamic> productionLimit;

  static double _number(Object? value) => double.tryParse(value?.toString() ?? '') ?? 0;
  static int _integer(Object? value) => int.tryParse(value?.toString() ?? '') ?? 0;
}

@immutable
class CitoTransaction {
  const CitoTransaction({
    required this.reference,
    required this.status,
    required this.type,
    required this.amount,
    required this.createdAt,
    this.providerReference,
    this.description,
    this.payerNumber,
  });

  factory CitoTransaction.fromJson(Map<String, dynamic> json) => CitoTransaction(
        reference: (json['tx_merchant_ref'] ?? json['tx_gateway_ref'] ?? json['id'] ?? 'Unknown').toString(),
        providerReference: json['tx_gateway_ref']?.toString(),
        status: (json['status'] ?? 'UNKNOWN').toString(),
        type: (json['tx_type'] ?? 'TRANSACTION').toString(),
        amount: double.tryParse((json['original_amount'] ?? 0).toString()) ?? 0,
        createdAt: DateTime.tryParse((json['created_on'] ?? '').toString()),
        description: (json['tx_merchant_description'] ?? json['tx_description'])?.toString(),
        payerNumber: json['payer_number']?.toString(),
      );

  final String reference;
  final String? providerReference;
  final String status;
  final String type;
  final double amount;
  final DateTime? createdAt;
  final String? description;
  final String? payerNumber;
}

@immutable
class ServiceEntitlement {
  const ServiceEntitlement({
    required this.code,
    required this.name,
    required this.description,
    required this.sandboxStatus,
    required this.productionStatus,
  });

  factory ServiceEntitlement.fromJson(Map<String, dynamic> json) => ServiceEntitlement(
        code: (json['serviceCode'] ?? json['service_code'] ?? '').toString(),
        name: (json['serviceName'] ?? json['service_name'] ?? json['serviceCode'] ?? 'Service').toString(),
        description: (json['description'] ?? '').toString(),
        sandboxStatus: (json['sandboxStatus'] ?? json['sandbox_status'] ?? 'NOT_CONFIGURED').toString(),
        productionStatus: (json['productionStatus'] ?? json['production_status'] ?? 'NOT_CONFIGURED').toString(),
      );

  final String code;
  final String name;
  final String description;
  final String sandboxStatus;
  final String productionStatus;
}

@immutable
class CitoNotification {
  const CitoNotification({
    required this.reference,
    required this.title,
    required this.message,
    required this.severity,
    required this.createdAt,
    this.readAt,
  });

  factory CitoNotification.fromJson(Map<String, dynamic> json) => CitoNotification(
        reference: (json['notification_reference'] ?? json['reference'] ?? '').toString(),
        title: (json['title'] ?? 'Notification').toString(),
        message: (json['message'] ?? '').toString(),
        severity: (json['severity'] ?? 'INFO').toString(),
        createdAt: DateTime.tryParse((json['created_at'] ?? '').toString()),
        readAt: DateTime.tryParse((json['read_at'] ?? '').toString()),
      );

  final String reference;
  final String title;
  final String message;
  final String severity;
  final DateTime? createdAt;
  final DateTime? readAt;
}

@immutable
class SupportCase {
  const SupportCase({
    required this.reference,
    required this.subject,
    required this.status,
    required this.severity,
    required this.updatedAt,
  });

  factory SupportCase.fromJson(Map<String, dynamic> json) => SupportCase(
        reference: (json['case_reference'] ?? json['caseReference'] ?? '').toString(),
        subject: (json['subject'] ?? 'Support case').toString(),
        status: (json['status'] ?? 'OPEN').toString(),
        severity: (json['severity'] ?? 'MEDIUM').toString(),
        updatedAt: DateTime.tryParse((json['updated_at'] ?? '').toString()),
      );

  final String reference;
  final String subject;
  final String status;
  final String severity;
  final DateTime? updatedAt;
}

@immutable
class StatementEntry {
  const StatementEntry({
    required this.id,
    required this.description,
    required this.narrative,
    required this.type,
    required this.amount,
    required this.balance,
    required this.createdAt,
  });

  factory StatementEntry.fromJson(Map<String, dynamic> json) => StatementEntry(
        id: (json['id'] ?? '').toString(),
        description: (json['description'] ?? '').toString(),
        narrative: (json['narrative'] ?? '').toString(),
        type: (json['tx_type'] ?? '').toString(),
        amount: double.tryParse((json['amount'] ?? 0).toString()) ?? 0,
        balance: (json['balances'] ?? '').toString(),
        createdAt: DateTime.tryParse((json['created_on'] ?? '').toString()),
      );

  final String id;
  final String description;
  final String narrative;
  final String type;
  final double amount;
  final String balance;
  final DateTime? createdAt;
}

@immutable
class MerchantStatement {
  const MerchantStatement({
    required this.availableBalance,
    required this.entries,
  });

  final String availableBalance;
  final List<StatementEntry> entries;
}
