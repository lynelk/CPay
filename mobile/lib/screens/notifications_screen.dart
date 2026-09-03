import 'package:flutter/material.dart';

import '../models/cito_models.dart';
import '../state/cito_controller.dart';
import '../widgets/cito_widgets.dart';

class NotificationsScreen extends StatelessWidget {
  const NotificationsScreen({required this.controller, super.key});

  final CitoController controller;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Notifications')),
      body: RefreshIndicator(
        onRefresh: controller.refreshAll,
        child: controller.notifications.isEmpty
            ? const ListView(
                physics: AlwaysScrollableScrollPhysics(),
                children: <Widget>[
                  SizedBox(height: 140),
                  EmptyView(
                    title: 'You are all caught up',
                    message: 'Payment, account, support and operational updates will appear here.',
                    icon: Icons.notifications_none_rounded,
                  ),
                ],
              )
            : ListView.separated(
                physics: const AlwaysScrollableScrollPhysics(),
                padding: const EdgeInsets.fromLTRB(16, 12, 16, 30),
                itemCount: controller.notifications.length,
                separatorBuilder: (_, __) => const SizedBox(height: 10),
                itemBuilder: (context, index) {
                  final item = controller.notifications[index];
                  return _NotificationCard(
                    item: item,
                    onRead: item.readAt == null
                        ? () => controller.markNotificationRead(item.reference)
                        : null,
                  );
                },
              ),
      ),
    );
  }
}

class _NotificationCard extends StatelessWidget {
  const _NotificationCard({required this.item, required this.onRead});

  final CitoNotification item;
  final Future<void> Function()? onRead;

  @override
  Widget build(BuildContext context) {
    return Card(
      color: item.readAt == null
          ? Theme.of(context).colorScheme.primaryContainer.withValues(alpha: .28)
          : null,
      child: Padding(
        padding: const EdgeInsets.all(17),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Row(
              children: <Widget>[
                StatusPill(item.severity),
                const Spacer(),
                if (item.readAt == null)
                  const Text('UNREAD', style: TextStyle(fontSize: 10, fontWeight: FontWeight.w900)),
              ],
            ),
            const SizedBox(height: 12),
            Text(
              item.title,
              style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w900),
            ),
            const SizedBox(height: 6),
            Text(item.message),
            const SizedBox(height: 10),
            Row(
              children: <Widget>[
                Expanded(
                  child: Text(
                    formatDateTime(item.createdAt),
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                          color: Theme.of(context).colorScheme.onSurfaceVariant,
                        ),
                  ),
                ),
                if (onRead != null)
                  TextButton(
                    onPressed: () async {
                      try {
                        await onRead!();
                      } on Object catch (error) {
                        if (!context.mounted) return;
                        ScaffoldMessenger.of(context).showSnackBar(
                          SnackBar(content: Text(error.toString().replaceFirst('Exception: ', ''))),
                        );
                      }
                    },
                    child: const Text('Mark read'),
                  ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
