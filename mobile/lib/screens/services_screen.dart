import 'package:flutter/material.dart';

import '../core/app_config.dart';
import '../models/cito_models.dart';
import '../state/cito_controller.dart';
import '../widgets/cito_widgets.dart';
import 'support_screen.dart';

class ServicesScreen extends StatelessWidget {
  const ServicesScreen({
    required this.config,
    required this.controller,
    super.key,
  });

  final AppConfig config;
  final CitoController controller;

  static const _families = <_ServiceFamily>[
    _ServiceFamily(
      code: 'CPAY',
      title: 'Payments',
      description: 'Collections, payouts, refunds, transaction status, reconciliation and settlement through Cito Payments / CPay.',
      icon: Icons.payments_outlined,
      capabilities: <String>['CPay', 'MTN MoMo', 'Airtel Money', 'Yo! Payments', 'FlexiPay', 'M-Pesa'],
      hints: <String>['CPAY', 'PAYMENT', 'PAYOUT', 'COLLECTION'],
    ),
    _ServiceFamily(
      code: 'COMMUNICATIONS',
      title: 'Communications',
      description: 'Customer and operational messaging with provider routing, delivery evidence, failover and usage billing.',
      icon: Icons.forum_outlined,
      capabilities: <String>['SMS', 'WhatsApp Business', 'USSD', 'Notifications', 'Routing', 'Delivery logs'],
      hints: <String>['COMMUNICATION', 'SMS', 'WHATSAPP', 'USSD', 'MESSAGE'],
    ),
    _ServiceFamily(
      code: 'IDENTITY_SCORING',
      title: 'Identity, Credit & Scoring',
      description: 'Identity verification and credit intelligence from approved providers with original evidence retained alongside normalized results.',
      icon: Icons.verified_user_outlined,
      capabilities: <String>['NIN verification', 'KYC / KYB', 'CRB reports', '0–1000 scoring', 'Bank verification', 'TIN / registry'],
      hints: <String>['IDENTITY', 'KYC', 'KYB', 'CRB', 'SCOR', 'CREDIT', 'NIN'],
    ),
    _ServiceFamily(
      code: 'VENDING',
      title: 'Vending & Value-Added Services',
      description: 'A unified vending layer for airtime, data, utilities, devices and other provider-backed services.',
      icon: Icons.confirmation_number_outlined,
      capabilities: <String>['Airtime', 'Data bundles', 'Utilities', 'Devices', 'QR journeys'],
      hints: <String>['VENDING', 'AIRTIME', 'UTILITY', 'DATA_BUNDLE', 'DEVICE'],
    ),
    _ServiceFamily(
      code: 'BILLING',
      title: 'Billing & Monetisation',
      description: 'Meter usage, apply effective-dated pricing, issue invoices and use Cito as a Billing-as-a-Service layer.',
      icon: Icons.request_quote_outlined,
      capabilities: <String>['Metering', 'Rating', 'BaaS', 'Invoices', 'Recurring', 'Tax & FX evidence'],
      hints: <String>['BILLING', 'BAAS', 'INVOICE', 'METERING', 'RATING', 'RECURRING'],
    ),
    _ServiceFamily(
      code: 'INTEGRATIONS',
      title: 'Integrations & Automation',
      description: 'Connect business systems through APIs, webhooks, developer projects, certified providers and workflow automation.',
      icon: Icons.hub_outlined,
      capabilities: <String>['APIs', 'Webhooks', 'Connectors', 'Routing', 'Certification', 'Automation'],
      hints: <String>['API', 'WEBHOOK', 'INTEGRATION', 'CONNECTOR', 'ROUTING'],
    ),
  ];

  @override
  Widget build(BuildContext context) {
    return RefreshIndicator(
      onRefresh: controller.refreshAll,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 30),
        children: <Widget>[
          EnvironmentBanner(environment: controller.dashboard?.environment ?? config.environment),
          const SizedBox(height: 18),
          const SectionHeader(
            title: 'Cito services',
            subtitle: 'One account for the capabilities your business is approved to use. Availability is entitlement and provider controlled.',
          ),
          const SizedBox(height: 12),
          ..._families.map(
            (family) => Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: _ServiceCard(
                family: family,
                entitlement: _match(family, controller.services),
                onRequestAccess: () => Navigator.of(context).push<void>(
                  MaterialPageRoute<void>(
                    builder: (_) => SupportScreen(
                      controller: controller,
                      initialSubject: 'Service access request: ${family.title}',
                      initialDescription: 'Please review and advise on enabling ${family.title} for this merchant account.',
                      initialCategory: 'SERVICE_ACCESS',
                    ),
                  ),
                ),
              ),
            ),
          ),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(18),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  Icon(Icons.info_outline_rounded, color: Theme.of(context).colorScheme.primary),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      'A listed capability is not automatically live. Production use still depends on provider configuration, certification, commercial approval and Cito entitlement.',
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(height: 1.5),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  static ServiceEntitlement? _match(
    _ServiceFamily family,
    List<ServiceEntitlement> entitlements,
  ) {
    for (final item in entitlements) {
      final source = '${item.code} ${item.name} ${item.description}'.toUpperCase();
      if (family.hints.any(source.contains)) return item;
    }
    return null;
  }
}

class _ServiceCard extends StatelessWidget {
  const _ServiceCard({
    required this.family,
    required this.entitlement,
    required this.onRequestAccess,
  });

  final _ServiceFamily family;
  final ServiceEntitlement? entitlement;
  final VoidCallback onRequestAccess;

  @override
  Widget build(BuildContext context) {
    final status = entitlement == null
        ? 'AVAILABLE_BY_ENTITLEMENT'
        : entitlement!.productionStatus;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(18),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Container(
                  width: 48,
                  height: 48,
                  decoration: BoxDecoration(
                    color: Theme.of(context).colorScheme.primaryContainer,
                    borderRadius: BorderRadius.circular(14),
                  ),
                  child: Icon(family.icon, color: Theme.of(context).colorScheme.primary),
                ),
                const SizedBox(width: 13),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: <Widget>[
                      Text(
                        family.title,
                        style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w900),
                      ),
                      const SizedBox(height: 5),
                      StatusPill(status),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 14),
            Text(
              entitlement?.description.trim().isNotEmpty == true
                  ? entitlement!.description
                  : family.description,
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: Theme.of(context).colorScheme.onSurfaceVariant,
                    height: 1.45,
                  ),
            ),
            const SizedBox(height: 13),
            Wrap(
              spacing: 7,
              runSpacing: 7,
              children: family.capabilities
                  .map(
                    (capability) => Chip(
                      label: Text(capability),
                      visualDensity: VisualDensity.compact,
                      side: BorderSide.none,
                    ),
                  )
                  .toList(growable: false),
            ),
            const SizedBox(height: 14),
            Row(
              children: <Widget>[
                Expanded(
                  child: Text(
                    entitlement == null
                        ? 'No active entitlement was returned.'
                        : 'Sandbox: ${entitlement!.sandboxStatus.replaceAll('_', ' ')}',
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                ),
                TextButton.icon(
                  onPressed: onRequestAccess,
                  icon: const Icon(Icons.support_agent_rounded),
                  label: Text(entitlement == null ? 'Request access' : 'Get help'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _ServiceFamily {
  const _ServiceFamily({
    required this.code,
    required this.title,
    required this.description,
    required this.icon,
    required this.capabilities,
    required this.hints,
  });

  final String code;
  final String title;
  final String description;
  final IconData icon;
  final List<String> capabilities;
  final List<String> hints;
}
