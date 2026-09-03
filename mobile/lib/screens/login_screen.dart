import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../core/app_config.dart';
import '../core/cito_theme.dart';
import '../state/cito_controller.dart';
import '../widgets/cito_widgets.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({
    required this.config,
    required this.controller,
    super.key,
  });

  final AppConfig config;
  final CitoController controller;

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _formKey = GlobalKey<FormState>();
  final _accountController = TextEditingController();
  final _usernameController = TextEditingController();
  final _passwordController = TextEditingController();
  bool _submitting = false;
  bool _obscurePassword = true;
  String? _error;

  @override
  void dispose() {
    _accountController.dispose();
    _usernameController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    FocusManager.instance.primaryFocus?.unfocus();
    if (!_formKey.currentState!.validate() || _submitting) return;
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      await widget.controller.login(
        accountNumber: _accountController.text,
        username: _usernameController.text,
        password: _passwordController.text,
      );
      TextInput.finishAutofillContext();
    } on Object catch (error) {
      if (!mounted) return;
      setState(() => _error = error.toString().replaceFirst('Exception: ', ''));
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  Future<void> _open(String path) async {
    final uri = widget.config.resolve(path);
    final launched = await launchUrl(uri, mode: LaunchMode.externalApplication);
    if (!launched && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('The Cito page could not be opened.')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Scaffold(
      body: SafeArea(
        child: LayoutBuilder(
          builder: (context, constraints) {
            return SingleChildScrollView(
              padding: const EdgeInsets.fromLTRB(22, 28, 22, 24),
              child: ConstrainedBox(
                constraints: BoxConstraints(minHeight: constraints.maxHeight - 52),
                child: Center(
                  child: ConstrainedBox(
                    constraints: const BoxConstraints(maxWidth: 480),
                    child: AutofillGroup(
                      child: Form(
                        key: _formKey,
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: <Widget>[
                            const Align(
                              alignment: Alignment.centerLeft,
                              child: CitoLogo(size: 62),
                            ),
                            const SizedBox(height: 28),
                            Text(
                              'Run your business from Cito',
                              style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                                    fontWeight: FontWeight.w900,
                                    color: scheme.brightness == Brightness.light
                                        ? CitoColours.navy
                                        : scheme.onSurface,
                                    letterSpacing: -.7,
                                  ),
                            ),
                            const SizedBox(height: 10),
                            Text(
                              'Secure access to payments, balances, services, notifications and support.',
                              style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                                    color: scheme.onSurfaceVariant,
                                    height: 1.45,
                                  ),
                            ),
                            const SizedBox(height: 20),
                            EnvironmentBanner(environment: widget.config.environment),
                            if (_error != null) ...<Widget>[
                              const SizedBox(height: 14),
                              ErrorNotice(message: _error!),
                            ],
                            const SizedBox(height: 24),
                            TextFormField(
                              controller: _accountController,
                              autofillHints: const <String>[AutofillHints.organizationName],
                              textInputAction: TextInputAction.next,
                              autocorrect: false,
                              decoration: const InputDecoration(
                                labelText: 'Merchant account number',
                                prefixIcon: Icon(Icons.storefront_outlined),
                              ),
                              validator: (value) => value == null || value.trim().isEmpty
                                  ? 'Enter your merchant account number.'
                                  : null,
                            ),
                            const SizedBox(height: 14),
                            TextFormField(
                              controller: _usernameController,
                              autofillHints: const <String>[AutofillHints.username, AutofillHints.email],
                              keyboardType: TextInputType.emailAddress,
                              textInputAction: TextInputAction.next,
                              autocorrect: false,
                              decoration: const InputDecoration(
                                labelText: 'Username or email',
                                prefixIcon: Icon(Icons.person_outline),
                              ),
                              validator: (value) => value == null || value.trim().isEmpty
                                  ? 'Enter your username or email.'
                                  : null,
                            ),
                            const SizedBox(height: 14),
                            TextFormField(
                              controller: _passwordController,
                              autofillHints: const <String>[AutofillHints.password],
                              obscureText: _obscurePassword,
                              textInputAction: TextInputAction.done,
                              onFieldSubmitted: (_) => _submit(),
                              decoration: InputDecoration(
                                labelText: 'Password',
                                prefixIcon: const Icon(Icons.lock_outline),
                                suffixIcon: IconButton(
                                  tooltip: _obscurePassword ? 'Show password' : 'Hide password',
                                  onPressed: () => setState(() => _obscurePassword = !_obscurePassword),
                                  icon: Icon(_obscurePassword ? Icons.visibility_outlined : Icons.visibility_off_outlined),
                                ),
                              ),
                              validator: (value) => value == null || value.isEmpty
                                  ? 'Enter your password.'
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
                                  : const Icon(Icons.login_rounded),
                              label: Text(_submitting ? 'Signing in…' : 'Sign in securely'),
                            ),
                            const SizedBox(height: 16),
                            Card(
                              child: Padding(
                                padding: const EdgeInsets.all(16),
                                child: Row(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: <Widget>[
                                    Icon(Icons.shield_outlined, color: scheme.primary),
                                    const SizedBox(width: 12),
                                    Expanded(
                                      child: Text(
                                        'Your password is never stored. Session cookies are protected in the device secure store and mutating requests use Cito CSRF protection.',
                                        style: Theme.of(context).textTheme.bodySmall?.copyWith(
                                              color: scheme.onSurfaceVariant,
                                              height: 1.45,
                                            ),
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                            ),
                            const SizedBox(height: 20),
                            Wrap(
                              alignment: WrapAlignment.center,
                              spacing: 4,
                              children: <Widget>[
                                TextButton(onPressed: () => _open('/privacy'), child: const Text('Privacy')),
                                TextButton(onPressed: () => _open('/terms'), child: const Text('Terms')),
                                TextButton(
                                  onPressed: () => _open('/account-deletion'),
                                  child: const Text('Account deletion'),
                                ),
                              ],
                            ),
                            Text(
                              'Cito Business · Core-Synergies',
                              textAlign: TextAlign.center,
                              style: Theme.of(context).textTheme.labelSmall?.copyWith(
                                    color: scheme.onSurfaceVariant,
                                  ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            );
          },
        ),
      ),
    );
  }
}
