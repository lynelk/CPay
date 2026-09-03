import 'package:flutter/material.dart';

import '../models/cito_models.dart';
import '../state/cito_controller.dart';
import '../widgets/cito_widgets.dart';

class SupportScreen extends StatefulWidget {
  const SupportScreen({
    required this.controller,
    super.key,
    this.initialSubject = '',
    this.initialDescription = '',
    this.initialCategory = 'GENERAL_SUPPORT',
    this.initialTransactionReference,
  });

  final CitoController controller;
  final String initialSubject;
  final String initialDescription;
  final String initialCategory;
  final String? initialTransactionReference;

  @override
  State<SupportScreen> createState() => _SupportScreenState();
}

class _SupportScreenState extends State<SupportScreen> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _subjectController;
  late final TextEditingController _descriptionController;
  late final TextEditingController _transactionController;
  late String _category;
  bool _submitting = false;
  String? _error;

  static const _categories = <String, String>{
    'GENERAL_SUPPORT': 'General support',
    'PAYMENT_PENDING': 'Payment pending',
    'PAYMENT_FAILED': 'Payment failed',
    'PAYOUT_ISSUE': 'Payout issue',
    'SETTLEMENT': 'Settlement',
    'ACCOUNT_ACCESS': 'Account access',
    'VERIFICATION_KYB': 'Verification / KYB',
    'API_INTEGRATION': 'API integration',
    'WEBHOOK': 'Webhook',
    'SERVICE_ACCESS': 'Service access',
    'ACCOUNT_DELETION': 'Account deletion',
  };

  @override
  void initState() {
    super.initState();
    _subjectController = TextEditingController(text: widget.initialSubject);
    _descriptionController = TextEditingController(text: widget.initialDescription);
    _transactionController = TextEditingController(text: widget.initialTransactionReference ?? '');
    _category = widget.initialCategory;
  }

  @override
  void dispose() {
    _subjectController.dispose();
    _descriptionController.dispose();
    _transactionController.dispose();
    super.dispose();
  }

  Future<void> _createCase() async {
    if (!_formKey.currentState!.validate() || _submitting) return;
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      final reference = await widget.controller.createSupportCase(
        subject: _subjectController.text,
        description: _descriptionController.text,
        category: _category,
        transactionReference: _transactionController.text.trim().isEmpty
            ? null
            : _transactionController.text,
      );
      if (!mounted) return;
      _subjectController.clear();
      _descriptionController.clear();
      _transactionController.clear();
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Support case $reference created.')),
      );
    } on Object catch (error) {
      if (!mounted) return;
      setState(() => _error = error.toString().replaceFirst('Exception: ', ''));
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Help & support')),
      body: RefreshIndicator(
        onRefresh: widget.controller.refreshAll,
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 30),
          children: <Widget>[
            const SectionHeader(
              title: 'Tell Cito what happened',
              subtitle: 'Cases are linked to your authenticated merchant account. Add a transaction reference when the issue concerns money movement.',
            ),
            const SizedBox(height: 12),
            Card(
              child: Padding(
                padding: const EdgeInsets.all(18),
                child: Form(
                  key: _formKey,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: <Widget>[
                      if (_error != null) ...<Widget>[
                        ErrorNotice(message: _error!),
                        const SizedBox(height: 14),
                      ],
                      DropdownButtonFormField<String>(
                        initialValue: _category,
                        decoration: const InputDecoration(
                          labelText: 'Issue category',
                          prefixIcon: Icon(Icons.category_outlined),
                        ),
                        items: _categories.entries
                            .map(
                              (entry) => DropdownMenuItem<String>(
                                value: entry.key,
                                child: Text(entry.value),
                              ),
                            )
                            .toList(growable: false),
                        onChanged: (value) => setState(() => _category = value ?? _category),
                      ),
                      const SizedBox(height: 14),
                      TextFormField(
                        controller: _subjectController,
                        decoration: const InputDecoration(
                          labelText: 'Subject',
                          prefixIcon: Icon(Icons.subject_rounded),
                        ),
                        validator: (value) => value == null || value.trim().length < 4
                            ? 'Enter a clear subject.'
                            : null,
                      ),
                      const SizedBox(height: 14),
                      TextFormField(
                        controller: _transactionController,
                        decoration: const InputDecoration(
                          labelText: 'Transaction reference (optional)',
                          prefixIcon: Icon(Icons.receipt_long_outlined),
                        ),
                      ),
                      const SizedBox(height: 14),
                      TextFormField(
                        controller: _descriptionController,
                        minLines: 4,
                        maxLines: 8,
                        decoration: const InputDecoration(
                          labelText: 'What happened?',
                          alignLabelWithHint: true,
                          prefixIcon: Icon(Icons.notes_rounded),
                        ),
                        validator: (value) => value == null || value.trim().length < 10
                            ? 'Provide enough detail for the support team to investigate.'
                            : null,
                      ),
                      const SizedBox(height: 18),
                      FilledButton.icon(
                        onPressed: _submitting ? null : _createCase,
                        icon: _submitting
                            ? const SizedBox.square(
                                dimension: 18,
                                child: CircularProgressIndicator(strokeWidth: 2),
                              )
                            : const Icon(Icons.send_rounded),
                        label: Text(_submitting ? 'Creating case…' : 'Create support case'),
                      ),
                    ],
                  ),
                ),
              ),
            ),
            const SizedBox(height: 22),
            const SectionHeader(
              title: 'Your cases',
              subtitle: 'Open and recent support requests in your merchant scope.',
            ),
            const SizedBox(height: 10),
            if (widget.controller.supportCases.isEmpty)
              const Card(
                child: Padding(
                  padding: EdgeInsets.all(18),
                  child: EmptyView(
                    title: 'No support cases',
                    message: 'Cases you create will be listed here.',
                    icon: Icons.support_agent_rounded,
                  ),
                ),
              )
            else
              ...widget.controller.supportCases.map(
                (item) => Padding(
                  padding: const EdgeInsets.only(bottom: 9),
                  child: _SupportCaseCard(item: item),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _SupportCaseCard extends StatelessWidget {
  const _SupportCaseCard({required this.item});

  final SupportCase item;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Row(
              children: <Widget>[
                Expanded(
                  child: Text(item.reference, style: const TextStyle(fontWeight: FontWeight.w900)),
                ),
                StatusPill(item.status),
              ],
            ),
            const SizedBox(height: 8),
            Text(item.subject),
            const SizedBox(height: 8),
            Row(
              children: <Widget>[
                StatusPill(item.severity),
                const Spacer(),
                Text(
                  formatDateTime(item.updatedAt),
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: Theme.of(context).colorScheme.onSurfaceVariant,
                      ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
