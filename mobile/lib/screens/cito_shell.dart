import 'package:flutter/material.dart';

import '../core/app_config.dart';
import '../state/cito_controller.dart';
import '../widgets/cito_widgets.dart';
import 'balances_screen.dart';
import 'home_screen.dart';
import 'more_screen.dart';
import 'notifications_screen.dart';
import 'payments_screen.dart';
import 'services_screen.dart';

class CitoShell extends StatefulWidget {
  const CitoShell({
    required this.config,
    required this.controller,
    super.key,
  });

  final AppConfig config;
  final CitoController controller;

  @override
  State<CitoShell> createState() => _CitoShellState();
}

class _CitoShellState extends State<CitoShell> {
  int _index = 0;

  static const _titles = <String>[
    'Home',
    'Payments',
    'Balances',
    'Services',
    'More',
  ];

  void _navigate(int index) {
    if (index < 0 || index >= _titles.length) return;
    setState(() => _index = index);
  }

  Future<void> _openNotifications() async {
    await Navigator.of(context).push<void>(
      MaterialPageRoute<void>(
        builder: (_) => NotificationsScreen(controller: widget.controller),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final pages = <Widget>[
      HomeScreen(controller: widget.controller, onNavigate: _navigate),
      PaymentsScreen(config: widget.config, controller: widget.controller),
      BalancesScreen(controller: widget.controller),
      ServicesScreen(config: widget.config, controller: widget.controller),
      MoreScreen(config: widget.config, controller: widget.controller),
    ];
    final unread = widget.controller.notifications.where((item) => item.readAt == null).length;

    return Scaffold(
      appBar: AppBar(
        titleSpacing: 16,
        title: Row(
          children: <Widget>[
            const CitoLogo(size: 34),
            const SizedBox(width: 11),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  Text(
                    _titles[_index],
                    style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w900),
                  ),
                  Text(
                    widget.config.isProduction ? 'PRODUCTION' : 'SANDBOX',
                    style: TextStyle(
                      fontSize: 10,
                      letterSpacing: .8,
                      fontWeight: FontWeight.w900,
                      color: widget.config.isProduction
                          ? Theme.of(context).colorScheme.error
                          : const Color(0xFF0369A1),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
        actions: <Widget>[
          IconButton(
            tooltip: 'Refresh',
            onPressed: widget.controller.refreshing ? null : widget.controller.refreshAll,
            icon: widget.controller.refreshing
                ? const SizedBox.square(
                    dimension: 19,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.refresh_rounded),
          ),
          Badge(
            isLabelVisible: unread > 0,
            label: Text(unread > 99 ? '99+' : '$unread'),
            child: IconButton(
              tooltip: 'Notifications',
              onPressed: _openNotifications,
              icon: const Icon(Icons.notifications_none_rounded),
            ),
          ),
          const SizedBox(width: 6),
        ],
      ),
      body: IndexedStack(index: _index, children: pages),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _index,
        onDestinationSelected: _navigate,
        destinations: const <NavigationDestination>[
          NavigationDestination(
            icon: Icon(Icons.home_outlined),
            selectedIcon: Icon(Icons.home_rounded),
            label: 'Home',
          ),
          NavigationDestination(
            icon: Icon(Icons.receipt_long_outlined),
            selectedIcon: Icon(Icons.receipt_long_rounded),
            label: 'Payments',
          ),
          NavigationDestination(
            icon: Icon(Icons.account_balance_wallet_outlined),
            selectedIcon: Icon(Icons.account_balance_wallet_rounded),
            label: 'Balances',
          ),
          NavigationDestination(
            icon: Icon(Icons.grid_view_outlined),
            selectedIcon: Icon(Icons.grid_view_rounded),
            label: 'Services',
          ),
          NavigationDestination(
            icon: Icon(Icons.more_horiz_rounded),
            selectedIcon: Icon(Icons.more_rounded),
            label: 'More',
          ),
        ],
      ),
    );
  }
}
