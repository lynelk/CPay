import 'package:flutter/material.dart';

import '../models/cito_models.dart';
import '../state/cito_controller.dart';
import '../widgets/cito_widgets.dart';

class BalancesScreen extends StatelessWidget {
  const BalancesScreen({required this.controller, super.key});

  final CitoController controller;

  @override
  Widget build(BuildContext context) {
    final dashboard = controller.dashboard;
    final statement = controller.statement;
    final limit = dashboard?.productionLimit ?? const <String, dynamic>{};
    final limitEnabled = limit['enabled'] != false;
    final remaining = limit['remainingToday'] ?? limit['remaining_today'];
    final totalLimit = limit['limit'];

    return RefreshIndicator(
      onRefresh: controller.refreshAll,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 30),
        children: <Widget>[
          EnvironmentBanner(environment: dashboard?.environment ?? 'SANDBOX'),
          const SizedBox(height: 18),
          const SectionHeader(
            title: 'Balances & settlement context',
            subtitle: 'Ledger-derived account balance, statement entries and configured payment-channel readiness.',
          ),
          const SizedBox(height: 12),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(20),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  Text(
                    'Available balance',
                    style: Theme.of(context).textTheme.labelLarge?.copyWith(
                          color: Theme.of(context).colorScheme.onSurfaceVariant,
                        ),
                  ),
                  const SizedBox(height: 7),
                  Text(
                    statement?.availableBalance.trim().isNotEmpty == true
                        ? statement!.availableBalance
                        : 'Not available',
                    style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                          fontWeight: FontWeight.w900,
                        ),
                  ),
                  const SizedBox(height: 10),
                  Text(
                    statement == null
                        ? 'Cito has not returned a statement balance yet. No substitute figure is being displayed.'
                        : 'Balance supplied by the merchant statement service.',
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                          color: Theme.of(context).colorScheme.onSurfaceVariant,
                        ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),
          Row(
            children: <Widget>[
              Expanded(
                child: MetricCard(
                  label: 'Collected today',
                  value: formatMoney(dashboard?.payIns ?? 0),
                  icon: Icons.south_west_rounded,
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: MetricCard(
                  label: 'Paid out today',
                  value: formatMoney(dashboard?.payOuts ?? 0),
                  icon: Icons.north_east_rounded,
                ),
              ),
            ],
          ),
          const SizedBox(height: 20),
          const SectionHeader(
            title: 'Production transaction limit',
            subtitle: 'This is an operational guard, not a wallet balance.',
          ),
          const SizedBox(height: 10),
          Card(
            child: ListTile(
              leading: CircleAvatar(
                backgroundColor: Theme.of(context).colorScheme.primaryContainer,
                child: const Icon(Icons.speed_outlined),
              ),
              title: Text(limitEnabled ? 'Limit enabled' : 'Limit disabled'),
              subtitle: Text(
                totalLimit == null
                    ? 'No production limit was returned.'
                    : '${remaining ?? totalLimit} of $totalLimit remaining today',
              ),
              trailing: StatusPill(limitEnabled ? 'ACTIVE' : 'DISABLED'),
            ),
          ),
          const SizedBox(height: 22),
          const SectionHeader(
            title: 'Payment channels',
            subtitle: 'A channel can be configured without being production certified. The status below comes from Cito.',
          ),
          const SizedBox(height: 10),
          if (dashboard?.channels.isEmpty ?? true)
            const Card(
              child: Padding(
                padding: EdgeInsets.all(18),
                child: EmptyView(
                  title: 'No payment channels reported',
                  message: 'Set up or activate channels in the Cito Merchant Workspace.',
                  icon: Icons.hub_outlined,
                ),
              ),
            )
          else
            ...dashboard!.channels.map((channel) => Padding(
                  padding: const EdgeInsets.only(bottom: 9),
                  child: _ChannelCard(channel: channel),
                )),
          const SizedBox(height: 18),
          const SectionHeader(
            title: 'Recent statement entries',
            subtitle: 'Credits and debits from the merchant account statement.',
          ),
          const SizedBox(height: 10),
          if (statement == null && controller.refreshing)
            const Card(child: LoadingView(label: 'Loading statement'))
          else if (statement?.entries.isEmpty ?? true)
            const Card(
              child: Padding(
                padding: EdgeInsets.all(18),
                child: EmptyView(
                  title: 'No statement entries yet',
                  message: 'Your first posted collection, payout or adjustment will appear here.',
                  icon: Icons.account_balance_wallet_outlined,
                ),
              ),
            )
          else
            Card(
              child: Column(
                children: statement!.entries.take(20).map((entry) => _StatementTile(entry: entry)).toList(growable: false),
              ),
            ),
        ],
      ),
    );
  }
}

class _ChannelCard extends StatelessWidget {
  const _ChannelCard({required this.channel});

  final ChannelSummary channel;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: <Widget>[
            Container(
              width: 44,
              height: 44,
              decoration: BoxDecoration(
                color: Theme.of(context).colorScheme.primaryContainer,
                borderRadius: BorderRadius.circular(13),
              ),
              child: const Icon(Icons.account_balance_wallet_outlined),
            ),
            const SizedBox(width: 13),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  Text(channel.name, style: const TextStyle(fontWeight: FontWeight.w900)),
                  const SizedBox(height: 3),
                  Text(
                    '${channel.environment} · ${channel.code}',
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                          color: Theme.of(context).colorScheme.onSurfaceVariant,
                        ),
                  ),
                ],
              ),
            ),
            StatusPill(channel.status),
          ],
        ),
      ),
    );
  }
}

class _StatementTile extends StatelessWidget {
  const _StatementTile({required this.entry});

  final StatementEntry entry;

  @override
  Widget build(BuildContext context) {
    final credit = entry.type.toUpperCase() == 'CR';
    return ListTile(
      leading: CircleAvatar(
        backgroundColor: statusColour(context, credit ? 'SUCCESSFUL' : 'FAILED').withValues(alpha: .10),
        child: Icon(
          credit ? Icons.add_rounded : Icons.remove_rounded,
          color: statusColour(context, credit ? 'SUCCESSFUL' : 'FAILED'),
        ),
      ),
      title: Text(
        '${credit ? '+' : '-'}${formatMoney(entry.amount.abs())}',
        style: const TextStyle(fontWeight: FontWeight.w900),
      ),
      subtitle: Text(
        '${entry.narrative}${entry.description.isEmpty ? '' : ': ${entry.description}'}\n${formatDateTime(entry.createdAt)}',
      ),
      isThreeLine: true,
      trailing: entry.balance.isEmpty
          ? null
          : Text(entry.balance, style: const TextStyle(fontWeight: FontWeight.w800)),
    );
  }
}
