import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../core/app_config.dart';
import '../state/cito_controller.dart';
import '../widgets/cito_widgets.dart';
import 'notifications_screen.dart';
import 'settings_screen.dart';
import 'support_screen.dart';

class MoreScreen extends StatelessWidget {
  const MoreScreen({
    required this.config,
    required this.controller,
    super.key,
  });

  final AppConfig config;
  final CitoController controller;

  Future<void> _open(BuildContext context, String path) async {
    final launched = await launchUrl(config.resolve(path), mode: LaunchMode.externalApplication);
    if (!launched && context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('The Cito page could not be opened.')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final session = controller.session;
    final unread = controller.notifications.where((item) => item.readAt == null).length;
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 30),
      children: <Widget>[
        Card(
          child: Padding(
            padding: const EdgeInsets.all(18),
            child: Row(
              children: <Widget>[
                const CitoLogo(size: 50),
                const SizedBox(width: 14),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: <Widget>[
                      Text(
                        session?.displayName ?? 'Merchant user',
                        style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w900),
                      ),
                      const SizedBox(height: 3),
                      Text(
                        session?.email ?? session?.username ?? '',
                        style: Theme.of(context).textTheme.bodySmall?.copyWith(
                              color: Theme.of(context).colorScheme.onSurfaceVariant,
                            ),
                      ),
                      if (session?.accountNumber.isNotEmpty == true)
                        Text(
                          'Account ${session!.accountNumber}',
                          style: Theme.of(context).textTheme.bodySmall,
                        ),
                    ],
                  ),
                ),
                StatusPill(config.isProduction ? 'PRODUCTION' : 'SANDBOX'),
              ],
            ),
          ),
        ),
        const SizedBox(height: 20),
        const SectionHeader(title: 'Support & account'),
        const SizedBox(height: 10),
        Card(
          child: Column(
            children: <Widget>[
              _MoreTile(
                icon: Icons.notifications_none_rounded,
                title: 'Notifications',
                subtitle: unread > 0 ? '$unread unread update${unread == 1 ? '' : 's'}' : 'You are up to date',
                onTap: () => Navigator.of(context).push<void>(
                  MaterialPageRoute<void>(
                    builder: (_) => NotificationsScreen(controller: controller),
                  ),
                ),
              ),
              const Divider(height: 1),
              _MoreTile(
                icon: Icons.support_agent_rounded,
                title: 'Help & support',
                subtitle: 'Create and track support cases',
                onTap: () => Navigator.of(context).push<void>(
                  MaterialPageRoute<void>(
                    builder: (_) => SupportScreen(controller: controller),
                  ),
                ),
              ),
              const Divider(height: 1),
              _MoreTile(
                icon: Icons.settings_outlined,
                title: 'Settings & security',
                subtitle: 'Session, legal information and account deletion',
                onTap: () => Navigator.of(context).push<void>(
                  MaterialPageRoute<void>(
                    builder: (_) => SettingsScreen(config: config, controller: controller),
                  ),
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 20),
        const SectionHeader(title: 'Cito resources'),
        const SizedBox(height: 10),
        Card(
          child: Column(
            children: <Widget>[
              _MoreTile(
                icon: Icons.public_rounded,
                title: 'Cito website',
                subtitle: config.apiBaseUrl,
                onTap: () => _open(context, '/'),
              ),
              const Divider(height: 1),
              _MoreTile(
                icon: Icons.privacy_tip_outlined,
                title: 'Privacy policy',
                subtitle: 'How Cito handles mobile-app data',
                onTap: () => _open(context, '/privacy'),
              ),
              const Divider(height: 1),
              _MoreTile(
                icon: Icons.description_outlined,
                title: 'Terms of service',
                subtitle: 'Rules governing use of Cito Business',
                onTap: () => _open(context, '/terms'),
              ),
              const Divider(height: 1),
              _MoreTile(
                icon: Icons.monitor_heart_outlined,
                title: 'Service status',
                subtitle: 'Current Cito and provider incidents',
                onTap: () => _open(context, '/status'),
              ),
            ],
          ),
        ),
        const SizedBox(height: 20),
        Text(
          'Cito Business 1.0.0 (1)',
          textAlign: TextAlign.center,
          style: Theme.of(context).textTheme.labelSmall?.copyWith(
                color: Theme.of(context).colorScheme.onSurfaceVariant,
              ),
        ),
      ],
    );
  }
}

class _MoreTile extends StatelessWidget {
  const _MoreTile({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.onTap,
  });

  final IconData icon;
  final String title;
  final String subtitle;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      minTileHeight: 72,
      leading: CircleAvatar(
        backgroundColor: Theme.of(context).colorScheme.primaryContainer,
        child: Icon(icon, color: Theme.of(context).colorScheme.primary),
      ),
      title: Text(title, style: const TextStyle(fontWeight: FontWeight.w800)),
      subtitle: Text(subtitle, maxLines: 2, overflow: TextOverflow.ellipsis),
      trailing: const Icon(Icons.chevron_right_rounded),
      onTap: onTap,
    );
  }
}
