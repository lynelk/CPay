import 'package:flutter/material.dart';

import '../models/cito_models.dart';
import '../state/cito_controller.dart';
import '../widgets/cito_widgets.dart';
import 'transaction_detail_screen.dart';

class HomeScreen extends StatelessWidget {
  const HomeScreen({
    required this.controller,
    required this.onNavigate,
    super.key,
  });

  final CitoController controller;
  final ValueChanged<int> onNavigate;

  @override
  Widget build(BuildContext context) {
    final dashboard = controller.dashboard;
    final session = controller.session;
    final recent = controller.transactions.take(5).toList(growable: false);

    return RefreshIndicator(
      onRefresh: controller.refreshAll,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 28),
        children: <Widget>[
          EnvironmentBanner(environment: dashboard?.environment ?? 'SANDBOX'),
          const SizedBox(height: 18),
          Text(
            'Welcome, ${session?.displayName ?? 'Merchant'}',
            style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w900),
          ),
          const SizedBox(height: 5),
          Text(
            controller.lastUpdated == null
                ? 'Your Cito business snapshot'
                : 'Updated ${formatDateTime(controller.lastUpdated)}',
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: Theme.of(context).colorScheme.onSurfaceVariant,
                ),
          ),
          if (controller.lastError != null) ...<Widget>[
            const SizedBox(height: 14),
            ErrorNotice(message: controller.lastError!, onRetry: controller.refreshAll),
          ],
          const SizedBox(height: 20),
          const SectionHeader(
            title: 'Today',
            subtitle: 'Live values from your Cito account. Nothing is guessed when a source is unavailable.',
          ),
          const SizedBox(height: 12),
          GridView.count(
            crossAxisCount: 2,
            childAspectRatio: 1.18,
            crossAxisSpacing: 12,
            mainAxisSpacing: 12,
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            children: <Widget>[
              MetricCard(
                label: 'Collections',
                value: formatMoney(dashboard?.payIns ?? 0),
                icon: Icons.south_west_rounded,
                note: 'Incoming payments',
              ),
              MetricCard(
                label: 'Disbursements',
                value: formatMoney(dashboard?.payOuts ?? 0),
                icon: Icons.north_east_rounded,
                note: 'Outgoing payments',
              ),
              MetricCard(
                label: 'Transactions',
                value: '${dashboard?.transactions ?? 0}',
                icon: Icons.receipt_long_outlined,
                note: 'Recorded today',
              ),
              MetricCard(
                label: 'Channels',
                value: '${dashboard?.channels.length ?? 0}',
                icon: Icons.hub_outlined,
                note: 'Reported in this environment',
              ),
            ],
          ),
          const SizedBox(height: 22),
          const SectionHeader(
            title: 'Quick actions',
            subtitle: 'The jobs a merchant is most likely to need away from a desk.',
          ),
          const SizedBox(height: 12),
          Row(
            children: <Widget>[
              Expanded(
                child: _ActionCard(
                  icon: Icons.add_card_rounded,
                  label: 'Receive payment',
                  onTap: () => onNavigate(1),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: _ActionCard(
                  icon: Icons.account_balance_wallet_outlined,
                  label: 'View balances',
                  onTap: () => onNavigate(2),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: _ActionCard(
                  icon: Icons.support_agent_rounded,
                  label: 'Get support',
                  onTap: () => onNavigate(4),
                ),
              ),
            ],
          ),
          const SizedBox(height: 24),
          SectionHeader(
            title: 'Payment channels',
            subtitle: 'Only channels reported by the live account are shown.',
            trailing: TextButton(
              onPressed: () => onNavigate(2),
              child: const Text('View all'),
            ),
          ),
          const SizedBox(height: 10),
          if (dashboard == null && controller.refreshing)
            const LoadingView(label: 'Loading account summary')
          else if (dashboard?.channels.isEmpty ?? true)
            const Card(
              child: Padding(
                padding: EdgeInsets.all(18),
                child: EmptyView(
                  title: 'No channels reported',
                  message: 'Connect or activate a payment channel in the Merchant Workspace before live traffic.',
                  icon: Icons.hub_outlined,
                ),
              ),
            )
          else
            ...dashboard!.channels.take(4).map(
                  (channel) => Padding(
                    padding: const EdgeInsets.only(bottom: 9),
                    child: _ChannelTile(channel: channel),
                  ),
                ),
          const SizedBox(height: 18),
          SectionHeader(
            title: 'Recent activity',
            subtitle: 'Collections, payouts and transaction status in one timeline.',
            trailing: TextButton(
              onPressed: () => onNavigate(1),
              child: const Text('See all'),
            ),
          ),
          const SizedBox(height: 10),
          if (recent.isEmpty)
            const Card(
              child: Padding(
                padding: EdgeInsets.all(18),
                child: EmptyView(
                  title: 'No recent transactions',
                  message: 'Your first collection or payout will appear here.',
                  icon: Icons.receipt_long_outlined,
                ),
              ),
            )
          else
            Card(
              child: Column(
                children: recent
                    .map(
                      (transaction) => _TransactionRow(
                        transaction: transaction,
                        onTap: () => Navigator.of(context).push<void>(
                          MaterialPageRoute<void>(
                            builder: (_) => TransactionDetailScreen(
                              controller: controller,
                              transaction: transaction,
                            ),
                          ),
                        ),
                      ),
                    )
                    .toList(growable: false),
              ),
            ),
          const SizedBox(height: 24),
          Card(
            child: InkWell(
              borderRadius: BorderRadius.circular(18),
              onTap: () => onNavigate(3),
              child: const Padding(
                padding: EdgeInsets.all(18),
                child: Row(
                  children: <Widget>[
                    Icon(Icons.grid_view_rounded, size: 30),
                    SizedBox(width: 14),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: <Widget>[
                          Text('Beyond payments', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w900)),
                          SizedBox(height: 4),
                          Text('Communications, identity and scoring, vending, billing and integrations are available through Cito entitlements.'),
                        ],
                      ),
                    ),
                    Icon(Icons.chevron_right_rounded),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _ActionCard extends StatelessWidget {
  const _ActionCard({required this.icon, required this.label, required this.onTap});

  final IconData icon;
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: InkWell(
        borderRadius: BorderRadius.circular(18),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 15),
          child: Column(
            children: <Widget>[
              Icon(icon, color: Theme.of(context).colorScheme.primary),
              const SizedBox(height: 8),
              Text(
                label,
                textAlign: TextAlign.center,
                maxLines: 2,
                style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w800),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _ChannelTile extends StatelessWidget {
  const _ChannelTile({required this.channel});

  final ChannelSummary channel;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: ListTile(
        leading: CircleAvatar(
          backgroundColor: Theme.of(context).colorScheme.primaryContainer,
          child: const Icon(Icons.account_balance_wallet_outlined),
        ),
        title: Text(channel.name, style: const TextStyle(fontWeight: FontWeight.w800)),
        subtitle: Text('${channel.environment} · ${channel.code}'),
        trailing: StatusPill(channel.status),
      ),
    );
  }
}

class _TransactionRow extends StatelessWidget {
  const _TransactionRow({required this.transaction, required this.onTap});

  final CitoTransaction transaction;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      onTap: onTap,
      leading: CircleAvatar(
        backgroundColor: statusColour(context, transaction.status).withValues(alpha: .10),
        child: Icon(
          transaction.type.toUpperCase().contains('PAY_OUT')
              ? Icons.north_east_rounded
              : Icons.south_west_rounded,
          color: statusColour(context, transaction.status),
        ),
      ),
      title: Text(formatMoney(transaction.amount), style: const TextStyle(fontWeight: FontWeight.w900)),
      subtitle: Text('${transaction.reference}\n${formatDateTime(transaction.createdAt)}'),
      isThreeLine: true,
      trailing: StatusPill(transaction.status),
    );
  }
}
