import 'package:flutter/material.dart';

import '../models/cito_models.dart';
import '../state/cito_controller.dart';
import '../widgets/cito_widgets.dart';

class TransactionDetailScreen extends StatefulWidget {
  const TransactionDetailScreen({
    required this.controller,
    required this.transaction,
    super.key,
  });

  final CitoController controller;
  final CitoTransaction transaction;

  @override
  State<TransactionDetailScreen> createState() => _TransactionDetailScreenState();
}

class _TransactionDetailScreenState extends State<TransactionDetailScreen> {
  late Future<Map<String, dynamic>> _timeline;

  @override
  void initState() {
    super.initState();
    _timeline = widget.controller.transactionTimeline(widget.transaction.reference);
  }

  void _reload() {
    setState(() {
      _timeline = widget.controller.transactionTimeline(widget.transaction.reference);
    });
  }

  @override
  Widget build(BuildContext context) {
    final transaction = widget.transaction;
    return Scaffold(
      appBar: AppBar(title: const Text('Transaction detail')),
      body: FutureBuilder<Map<String, dynamic>>(
        future: _timeline,
        builder: (context, snapshot) {
          return RefreshIndicator(
            onRefresh: () async {
              _reload();
              await _timeline;
            },
            child: ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 30),
              children: <Widget>[
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(18),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: <Widget>[
                        Row(
                          children: <Widget>[
                            Expanded(
                              child: Text(
                                formatMoney(transaction.amount),
                                style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                                      fontWeight: FontWeight.w900,
                                    ),
                              ),
                            ),
                            StatusPill(transaction.status),
                          ],
                        ),
                        const SizedBox(height: 16),
                        _DetailRow(label: 'Cito / merchant reference', value: transaction.reference),
                        _DetailRow(
                          label: 'Provider reference',
                          value: transaction.providerReference ?? 'Not recorded',
                        ),
                        _DetailRow(label: 'Type', value: transaction.type.replaceAll('_', ' ')),
                        _DetailRow(label: 'Customer', value: transaction.payerNumber ?? 'Not recorded'),
                        _DetailRow(label: 'Created', value: formatDateTime(transaction.createdAt)),
                        if (transaction.description?.isNotEmpty == true)
                          _DetailRow(label: 'Description', value: transaction.description!),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 18),
                const SectionHeader(
                  title: 'Processing timeline',
                  subtitle: 'Provider, finality, reconciliation and settlement evidence as recorded by Cito.',
                ),
                const SizedBox(height: 10),
                if (snapshot.connectionState == ConnectionState.waiting)
                  const Card(child: LoadingView(label: 'Loading transaction evidence'))
                else if (snapshot.hasError)
                  ErrorNotice(
                    message: snapshot.error.toString().replaceFirst('Exception: ', ''),
                    onRetry: _reload,
                  )
                else
                  _TimelineCard(data: snapshot.data ?? const <String, dynamic>{}),
              ],
            ),
          );
        },
      ),
    );
  }
}

class _TimelineCard extends StatelessWidget {
  const _TimelineCard({required this.data});

  final Map<String, dynamic> data;

  @override
  Widget build(BuildContext context) {
    final rawEvents = data['events'];
    final events = rawEvents is List<dynamic>
        ? rawEvents.whereType<Map<String, dynamic>>().toList(growable: false)
        : const <Map<String, dynamic>>[];
    final finality = (data['finality'] ?? data['status'] ?? 'UNKNOWN').toString();
    final settlement = (data['settlementState'] ?? data['settlement_state'] ?? 'Not recorded').toString();

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(18),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Row(
              children: <Widget>[
                const Expanded(
                  child: Text('Current finality', style: TextStyle(fontWeight: FontWeight.w800)),
                ),
                StatusPill(finality),
              ],
            ),
            const SizedBox(height: 8),
            Text('Settlement: $settlement'),
            const Divider(height: 28),
            if (events.isEmpty)
              const EmptyView(
                title: 'No timeline events recorded',
                message: 'The transaction summary is available, but no detailed processing events were returned.',
                icon: Icons.timeline_outlined,
              )
            else
              ...events.asMap().entries.map((entry) {
                final event = entry.value;
                final label = (event['event'] ?? event['eventType'] ?? 'Processing event')
                    .toString()
                    .replaceAll('_', ' ');
                final status = (event['status'] ?? '').toString();
                final occurredAt = DateTime.tryParse(
                  (event['occurredAt'] ?? event['occurred_at'] ?? event['createdAt'] ?? '').toString(),
                );
                final detail = (event['reason'] ?? event['detail'] ?? '').toString();
                final last = entry.key == events.length - 1;
                return IntrinsicHeight(
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: <Widget>[
                      SizedBox(
                        width: 28,
                        child: Column(
                          children: <Widget>[
                            Container(
                              width: 14,
                              height: 14,
                              decoration: BoxDecoration(
                                color: status.isEmpty
                                    ? Theme.of(context).colorScheme.primary
                                    : statusColour(context, status),
                                shape: BoxShape.circle,
                              ),
                            ),
                            if (!last)
                              Expanded(
                                child: Container(
                                  width: 2,
                                  color: Theme.of(context).dividerColor,
                                ),
                              ),
                          ],
                        ),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Padding(
                          padding: const EdgeInsets.only(bottom: 20),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: <Widget>[
                              Row(
                                children: <Widget>[
                                  Expanded(
                                    child: Text(
                                      label,
                                      style: const TextStyle(fontWeight: FontWeight.w800),
                                    ),
                                  ),
                                  if (status.isNotEmpty) StatusPill(status),
                                ],
                              ),
                              const SizedBox(height: 4),
                              Text(
                                formatDateTime(occurredAt),
                                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                                      color: Theme.of(context).colorScheme.onSurfaceVariant,
                                    ),
                              ),
                              if (detail.isNotEmpty) ...<Widget>[
                                const SizedBox(height: 5),
                                Text(detail),
                              ],
                            ],
                          ),
                        ),
                      ),
                    ],
                  ),
                );
              }),
          ],
        ),
      ),
    );
  }
}

class _DetailRow extends StatelessWidget {
  const _DetailRow({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 11),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Text(
            label,
            style: Theme.of(context).textTheme.labelSmall?.copyWith(
                  color: Theme.of(context).colorScheme.onSurfaceVariant,
                ),
          ),
          const SizedBox(height: 2),
          SelectableText(value, style: const TextStyle(fontWeight: FontWeight.w700)),
        ],
      ),
    );
  }
}
