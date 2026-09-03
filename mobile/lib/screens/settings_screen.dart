import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../core/app_config.dart';
import '../core/cito_theme.dart';
import '../state/cito_controller.dart';
import '../widgets/cito_widgets.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({
    required this.config,
    required this.controller,
    super.key,
  });

  final AppConfig config;
  final CitoController controller;

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  bool _deleting = false;
  bool _loggingOut = false;

  Future<void> _open(String path) async {
    final launched = await launchUrl(widget.config.resolve(path), mode: LaunchMode.externalApplication);
    if (!launched && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('The Cito page could not be opened.')),
      );
    }
  }

  Future<void> _requestDeletion() async {
    final acknowledged = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        icon: const Icon(Icons.delete_forever_outlined, color: CitoColours.danger),
        title: const Text('Request account deletion'),
        content: const Text(
          'This starts an authenticated deletion request. Cito may need to retain transaction, ledger, tax, fraud-prevention or regulatory records where the law requires it. The support team will confirm scope and timing before access is removed.',
        ),
        actions: <Widget>[
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: CitoColours.danger),
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Request deletion'),
          ),
        ],
      ),
    );
    if (acknowledged != true || !mounted) return;
    setState(() => _deleting = true);
    try {
      final reference = await widget.controller.createSupportCase(
        subject: 'Authenticated Cito account deletion request',
        description: 'The signed-in user initiated account deletion from Cito Business mobile. Please verify authority, confirm data-retention obligations, disable access and complete deletion of data that is not legally required to be retained.',
        category: 'ACCOUNT_DELETION',
        severity: 'HIGH',
      );
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Deletion request $reference has been created.')),
      );
    } on Object catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(error.toString().replaceFirst('Exception: ', ''))),
      );
    } finally {
      if (mounted) setState(() => _deleting = false);
    }
  }

  Future<void> _logout() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Sign out of Cito Business?'),
        content: const Text('The secure mobile session will be removed from this device.'),
        actions: <Widget>[
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Sign out')),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    setState(() => _loggingOut = true);
    await widget.controller.logout();
  }

  @override
  Widget build(BuildContext context) {
    final session = widget.controller.session;
    return Scaffold(
      appBar: AppBar(title: const Text('Settings & security')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 30),
        children: <Widget>[
          EnvironmentBanner(environment: widget.config.environment),
          const SizedBox(height: 18),
          const SectionHeader(title: 'Account'),
          const SizedBox(height: 10),
          Card(
            child: Column(
              children: <Widget>[
                ListTile(
                  leading: const Icon(Icons.storefront_outlined),
                  title: const Text('Merchant account'),
                  subtitle: Text(session?.accountNumber.isEmpty == false ? session!.accountNumber : 'Not returned'),
                ),
                const Divider(height: 1),
                ListTile(
                  leading: const Icon(Icons.person_outline),
                  title: const Text('Signed-in user'),
                  subtitle: Text(session?.email ?? session?.username ?? 'Not returned'),
                ),
                const Divider(height: 1),
                const ListTile(
                  leading: Icon(Icons.lock_outline),
                  title: Text('Session protection'),
                  subtitle: Text('Cookies are held in the operating-system secure store. Passwords are not stored.'),
                ),
              ],
            ),
          ),
          const SizedBox(height: 20),
          const SectionHeader(title: 'Privacy & legal'),
          const SizedBox(height: 10),
          Card(
            child: Column(
              children: <Widget>[
                ListTile(
                  leading: const Icon(Icons.privacy_tip_outlined),
                  title: const Text('Privacy policy'),
                  trailing: const Icon(Icons.open_in_new_rounded),
                  onTap: () => _open('/privacy'),
                ),
                const Divider(height: 1),
                ListTile(
                  leading: const Icon(Icons.description_outlined),
                  title: const Text('Terms of service'),
                  trailing: const Icon(Icons.open_in_new_rounded),
                  onTap: () => _open('/terms'),
                ),
                const Divider(height: 1),
                ListTile(
                  leading: const Icon(Icons.delete_outline, color: CitoColours.danger),
                  title: const Text('Request account deletion'),
                  subtitle: const Text('Start deletion from inside the authenticated app.'),
                  trailing: _deleting
                      ? const SizedBox.square(dimension: 20, child: CircularProgressIndicator(strokeWidth: 2))
                      : const Icon(Icons.chevron_right_rounded),
                  onTap: _deleting ? null : _requestDeletion,
                ),
              ],
            ),
          ),
          const SizedBox(height: 20),
          OutlinedButton.icon(
            style: OutlinedButton.styleFrom(
              minimumSize: const Size.fromHeight(52),
              foregroundColor: CitoColours.danger,
              side: const BorderSide(color: CitoColours.danger),
            ),
            onPressed: _loggingOut ? null : _logout,
            icon: _loggingOut
                ? const SizedBox.square(dimension: 18, child: CircularProgressIndicator(strokeWidth: 2))
                : const Icon(Icons.logout_rounded),
            label: Text(_loggingOut ? 'Signing out…' : 'Sign out'),
          ),
        ],
      ),
    );
  }
}
