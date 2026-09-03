import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../core/cito_theme.dart';

final NumberFormat _currency = NumberFormat.currency(
  locale: 'en_UG',
  symbol: 'UGX ',
  decimalDigits: 0,
);

String formatMoney(num value) => _currency.format(value);

String formatDateTime(DateTime? value) => value == null
    ? 'Not recorded'
    : DateFormat('d MMM yyyy, HH:mm').format(value.toLocal());

Color statusColour(BuildContext context, String status) {
  final normalized = status.toUpperCase();
  if (<String>{'ACTIVE', 'APPROVED', 'COMPLETED', 'SUCCESSFUL', 'DONE', 'LIVE'}.contains(normalized)) {
    return CitoColours.success;
  }
  if (<String>{'FAILED', 'REJECTED', 'BLOCKED', 'SUSPENDED', 'CRITICAL', 'HIGH'}.contains(normalized)) {
    return CitoColours.danger;
  }
  if (<String>{'PENDING', 'REQUESTED', 'IN_REVIEW', 'PROCESSING', 'DEGRADED'}.contains(normalized)) {
    return CitoColours.warning;
  }
  return Theme.of(context).colorScheme.onSurfaceVariant;
}

class CitoLogo extends StatelessWidget {
  const CitoLogo({super.key, this.size = 48});

  final double size;

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          colors: <Color>[Color(0xFF0F766E), Color(0xFF0EA5C6)],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(size * .28),
      ),
      child: SizedBox.square(
        dimension: size,
        child: Center(
          child: Text(
            'C',
            style: TextStyle(
              color: Colors.white,
              fontWeight: FontWeight.w900,
              fontSize: size * .48,
            ),
          ),
        ),
      ),
    );
  }
}

class EnvironmentBanner extends StatelessWidget {
  const EnvironmentBanner({required this.environment, super.key});

  final String environment;

  @override
  Widget build(BuildContext context) {
    final production = environment.toUpperCase() == 'PRODUCTION';
    final colour = production ? CitoColours.danger : const Color(0xFF0369A1);
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 9),
      decoration: BoxDecoration(
        color: colour.withValues(alpha: .08),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: colour.withValues(alpha: .22)),
      ),
      child: Row(
        children: <Widget>[
          Icon(production ? Icons.warning_amber_rounded : Icons.science_outlined, color: colour, size: 19),
          const SizedBox(width: 9),
          Expanded(
            child: Text(
              production
                  ? 'PRODUCTION · Real customer and financial data'
                  : 'SANDBOX · Test environment, no real money',
              style: TextStyle(color: colour, fontWeight: FontWeight.w800, fontSize: 12),
            ),
          ),
        ],
      ),
    );
  }
}

class SectionHeader extends StatelessWidget {
  const SectionHeader({
    required this.title,
    super.key,
    this.subtitle,
    this.trailing,
  });

  final String title;
  final String? subtitle;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Text(title, style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w800)),
              if (subtitle != null) ...<Widget>[
                const SizedBox(height: 3),
                Text(subtitle!, style: Theme.of(context).textTheme.bodySmall?.copyWith(color: Theme.of(context).colorScheme.onSurfaceVariant)),
              ],
            ],
          ),
        ),
        if (trailing != null) trailing!,
      ],
    );
  }
}

class MetricCard extends StatelessWidget {
  const MetricCard({
    required this.label,
    required this.value,
    required this.icon,
    super.key,
    this.note,
  });

  final String label;
  final String value;
  final IconData icon;
  final String? note;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Icon(icon, color: CitoColours.primary, size: 22),
            const Spacer(),
            Text(label, style: Theme.of(context).textTheme.bodySmall?.copyWith(color: Theme.of(context).colorScheme.onSurfaceVariant)),
            const SizedBox(height: 4),
            FittedBox(
              fit: BoxFit.scaleDown,
              alignment: Alignment.centerLeft,
              child: Text(value, style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w900)),
            ),
            if (note != null) ...<Widget>[
              const SizedBox(height: 4),
              Text(note!, style: Theme.of(context).textTheme.labelSmall),
            ],
          ],
        ),
      ),
    );
  }
}

class StatusPill extends StatelessWidget {
  const StatusPill(this.status, {super.key});

  final String status;

  @override
  Widget build(BuildContext context) {
    final colour = statusColour(context, status);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 5),
      decoration: BoxDecoration(
        color: colour.withValues(alpha: .10),
        borderRadius: BorderRadius.circular(999),
      ),
      child: Text(
        status.replaceAll('_', ' ').toUpperCase(),
        style: TextStyle(color: colour, fontWeight: FontWeight.w800, fontSize: 10),
      ),
    );
  }
}

class LoadingView extends StatelessWidget {
  const LoadingView({super.key, this.label = 'Loading Cito'});

  final String label;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            const CircularProgressIndicator(),
            const SizedBox(height: 16),
            Text(label),
          ],
        ),
      ),
    );
  }
}

class EmptyView extends StatelessWidget {
  const EmptyView({required this.title, required this.message, super.key, this.icon = Icons.inbox_outlined});

  final String title;
  final String message;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(28),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            Icon(icon, size: 42, color: Theme.of(context).colorScheme.onSurfaceVariant),
            const SizedBox(height: 14),
            Text(title, style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w800), textAlign: TextAlign.center),
            const SizedBox(height: 6),
            Text(message, style: Theme.of(context).textTheme.bodyMedium?.copyWith(color: Theme.of(context).colorScheme.onSurfaceVariant), textAlign: TextAlign.center),
          ],
        ),
      ),
    );
  }
}

class ErrorNotice extends StatelessWidget {
  const ErrorNotice({required this.message, super.key, this.onRetry});

  final String message;
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: CitoColours.danger.withValues(alpha: .08),
        border: Border.all(color: CitoColours.danger.withValues(alpha: .2)),
        borderRadius: BorderRadius.circular(14),
      ),
      child: Row(
        children: <Widget>[
          const Icon(Icons.error_outline, color: CitoColours.danger),
          const SizedBox(width: 10),
          Expanded(child: Text(message, style: const TextStyle(color: CitoColours.danger))),
          if (onRetry != null) TextButton(onPressed: onRetry, child: const Text('Retry')),
        ],
      ),
    );
  }
}
