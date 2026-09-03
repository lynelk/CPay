import 'package:flutter/material.dart';

import '../core/app_config.dart';
import '../models/cito_models.dart';
import '../state/cito_controller.dart';
import '../widgets/cito_widgets.dart';
import 'transaction_detail_screen.dart';

class PaymentsScreen extends StatefulWidget {
  const PaymentsScreen({
    required this.config,
    required this.controller,
    super.key,
  });

  final AppConfig config;
  final CitoController controller;

  @override
  State<PaymentsScreen> createState() => _PaymentsScreenState();
}

class _PaymentsScreenState extends State<PaymentsScreen> {
  final _searchController = TextEditingController();
  bool _searching = false;

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _search() async {
    if (_searching) return;
    setState(() => _searching = true);
    await widget.controller.searchTransactions(_searchController.text);
    if (mounted) setState(() => _searching = false);
  }

  Future<void> _openCollection() async {
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      showDragHandle: true,
      builder: (_) => _CollectionSheet(config: widget.config, controller: widget.controller),
    );
  }

  @override
  Widget build(BuildContext context) {
    final transactions = widget.controller.transactions;
    return RefreshIndicator(
      onRefresh: widget.controller.refreshAll,
      child: CustomScrollView(
        physics: const AlwaysScrollableScrollPhysics(),
        slivers: <Widget>[
          SliverPadding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 10),
            sliver: SliverToBoxAdapter(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: <Widget>[
                  EnvironmentBanner(environment: widget.controller.dashboard?.environment ?? widget.config.environment),
                  const SizedBox(height: 18),
                  SectionHeader(
                    title: 'Payments',
                    subtitle: 'Search transactions, understand status and initiate a guarded collection.',
                    trailing: FilledButton.icon(
                      onPressed: _openCollection,
                      icon: const Icon(Icons.add_rounded),
                      label: const Text('Receive'),
                    ),
                  ),
                  const SizedBox(height: 14),
                  SearchBar(
                    controller: _searchController,
                    hintText: 'Reference, phone, status or amount',
                    leading: const Icon(Icons.search_rounded),
                    trailing: <Widget>[
                      if (_searchController.text.isNotEmpty)
                        IconButton(
                          tooltip: 'Clear search',
                          onPressed: () {
                            _searchController.clear();
                            widget.controller.searchTransactions('');
                            setState(() {});
                          },
                          icon: const Icon(Icons.close_rounded),
                        ),
                    ],
                    onChanged: (_) => setState(() {}),
                    onSubmitted: (_) => _search(),
                  ),
                  const SizedBox(height: 10),
                  FilledButton.tonalIcon(
                    onPressed: _searching ? null : _search,
                    icon: _searching
                        ? const SizedBox.square(
                            dimension: 17,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Icon(Icons.manage_search_rounded),
                    label: Text(_searching ? 'Searching…' : 'Search transactions'),
                  ),
                  if (widget.controller.lastError != null) ...<Widget>[
                    const SizedBox(height: 12),
                    ErrorNotice(
                      message: widget.controller.lastError!,
                      onRetry: _search,
                    ),
                  ],
                ],
              ),
            ),
          ),
          if (transactions.isEmpty)
            const SliverFillRemaining(
              hasScrollBody: false,
              child: EmptyView(
                title: 'No matching transactions',
                message: 'New collections and payouts will appear here. Search is limited to your merchant scope.',
                icon: Icons.receipt_long_outlined,
              ),
            )
          else
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(16, 4, 16, 30),
              sliver: SliverList.separated(
                itemCount: transactions.length,
                separatorBuilder: (_, __) => const SizedBox(height: 9),
                itemBuilder: (context, index) {
                  final transaction = transactions[index];
                  return _PaymentCard(
                    transaction: transaction,
                    onTap: () => Navigator.of(context).push<void>(
                      MaterialPageRoute<void>(
                        builder: (_) => TransactionDetailScreen(
                          controller: widget.controller,
                          transaction: transaction,
                        ),
                      ),
                    ),
                  );
                },
              ),
            ),
        ],
      ),
    );
  }
}

class _PaymentCard extends StatelessWidget {
  const _PaymentCard({required this.transaction, required this.onTap});

  final CitoTransaction transaction;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: InkWell(
        borderRadius: BorderRadius.circular(18),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Container(
                width: 44,
                height: 44,
                decoration: BoxDecoration(
                  color: statusColour(context, transaction.status).withValues(alpha: .10),
                  borderRadius: BorderRadius.circular(13),
                ),
                child: Icon(
                  transaction.type.toUpperCase().contains('PAY_OUT')
                      ? Icons.north_east_rounded
                      : Icons.south_west_rounded,
                  color: statusColour(context, transaction.status),
                ),
              ),
              const SizedBox(width: 13),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: <Widget>[
                    Row(
                      children: <Widget>[
                        Expanded(
                          child: Text(
                            formatMoney(transaction.amount),
                            style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w900),
                          ),
                        ),
                        StatusPill(transaction.status),
                      ],
                    ),
                    const SizedBox(height: 6),
                    Text(
                      transaction.reference,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(fontWeight: FontWeight.w700),
                    ),
                    const SizedBox(height: 3),
                    Text(
                      '${transaction.type.replaceAll('_', ' ')} · ${formatDateTime(transaction.createdAt)}',
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                            color: Theme.of(context).colorScheme.onSurfaceVariant,
                          ),
                    ),
                    if (transaction.description?.isNotEmpty == true) ...<Widget>[
                      const SizedBox(height: 5),
                      Text(
                        transaction.description!,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: Theme.of(context).textTheme.bodySmall,
                      ),
                    ],
                  ],
                ),
              ),
              const SizedBox(width: 4),
              const Icon(Icons.chevron_right_rounded),
            ],
          ),
        ),
      ),
    );
  }
}

class _CollectionSheet extends StatefulWidget {
  const _CollectionSheet({required this.config, required this.controller});

  final AppConfig config;
  final CitoController controller;

  @override
  State<_CollectionSheet> createState() => _CollectionSheetState();
}

class _CollectionSheetState extends State<_CollectionSheet> {
  final _formKey = GlobalKey<FormState>();
  final _accountController = TextEditingController();
  final _amountController = TextEditingController();
  final _descriptionController = TextEditingController();
  bool _submitting = false;
  String? _error;

  @override
  void dispose() {
    _accountController.dispose();
    _amountController.dispose();
    _descriptionController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    FocusManager.instance.primaryFocus?.unfocus();
    if (!_formKey.currentState!.validate() || _submitting) return;
    final amount = double.parse(_amountController.text.replaceAll(',', '').trim());
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        icon: Icon(
          widget.config.isProduction ? Icons.warning_amber_rounded : Icons.science_outlined,
        ),
        title: Text(widget.config.isProduction ? 'Confirm live collection' : 'Confirm test collection'),
        content: Text(
          'Request ${formatMoney(amount)} from ${_accountController.text.trim()} in '
          '${widget.config.isProduction ? 'PRODUCTION' : 'SANDBOX'}. Cito will submit this to the configured payment channel.',
        ),
        actions: <Widget>[
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Confirm')),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;

    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      final message = await widget.controller.initiateCollection(
        account: _accountController.text,
        description: _descriptionController.text,
        amount: amount,
      );
      if (!mounted) return;
      Navigator.pop(context);
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
    } on Object catch (error) {
      if (!mounted) return;
      setState(() => _error = error.toString().replaceFirst('Exception: ', ''));
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.fromLTRB(
        20,
        0,
        20,
        MediaQuery.viewInsetsOf(context).bottom + 24,
      ),
      child: SingleChildScrollView(
        child: Form(
          key: _formKey,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: <Widget>[
              Text(
                'Receive payment',
                style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w900),
              ),
              const SizedBox(height: 7),
              Text(
                'Initiate an incoming mobile-money collection. The backend still enforces channel, role, limit and idempotency controls.',
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: Theme.of(context).colorScheme.onSurfaceVariant,
                    ),
              ),
              const SizedBox(height: 16),
              EnvironmentBanner(environment: widget.config.environment),
              if (_error != null) ...<Widget>[
                const SizedBox(height: 12),
                ErrorNotice(message: _error!),
              ],
              const SizedBox(height: 18),
              TextFormField(
                controller: _accountController,
                keyboardType: TextInputType.phone,
                textInputAction: TextInputAction.next,
                decoration: const InputDecoration(
                  labelText: 'Customer mobile number',
                  hintText: '256772123456',
                  prefixIcon: Icon(Icons.phone_outlined),
                ),
                validator: (value) {
                  final normalized = value?.replaceAll(RegExp(r'[^0-9+]'), '') ?? '';
                  if (normalized.isEmpty) return 'Enter the customer mobile number.';
                  if (normalized.length < 9) return 'Enter a valid mobile number.';
                  return null;
                },
              ),
              const SizedBox(height: 14),
              TextFormField(
                controller: _amountController,
                keyboardType: const TextInputType.numberWithOptions(decimal: false),
                textInputAction: TextInputAction.next,
                decoration: const InputDecoration(
                  labelText: 'Amount (UGX)',
                  prefixIcon: Icon(Icons.payments_outlined),
                ),
                validator: (value) {
                  final amount = double.tryParse(value?.replaceAll(',', '').trim() ?? '');
                  if (amount == null || amount <= 0) return 'Enter an amount greater than zero.';
                  return null;
                },
              ),
              const SizedBox(height: 14),
              TextFormField(
                controller: _descriptionController,
                minLines: 2,
                maxLines: 4,
                textInputAction: TextInputAction.done,
                decoration: const InputDecoration(
                  labelText: 'Description',
                  hintText: 'Invoice, order or purpose',
                  prefixIcon: Icon(Icons.notes_rounded),
                ),
                validator: (value) => value == null || value.trim().length < 3
                    ? 'Add a clear payment description.'
                    : null,
              ),
              const SizedBox(height: 20),
              FilledButton.icon(
                onPressed: _submitting ? null : _submit,
                icon: _submitting
                    ? const SizedBox.square(
                        dimension: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.send_rounded),
                label: Text(_submitting ? 'Submitting…' : 'Review collection'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
