import 'package:flutter/material.dart';

import '../models/login_session.dart';
import '../services/auth_api_service.dart';
import '../storage/session_database.dart';
import '../storage/session_store.dart';
import '../theme.dart';
import '../validation/login_validators.dart';
import '../widgets/brand_wordmark.dart';
import '../widgets/paper_background.dart';

class LoginPage extends StatefulWidget {
  const LoginPage({
    super.key,
    required this.authApi,
    required this.onAuthenticated,
    this.sessionStore,
  });

  final AuthApiService authApi;
  final ValueChanged<LoginSession> onAuthenticated;
  final SessionStore? sessionStore;

  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  final _formKey = GlobalKey<FormState>();
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  bool _loading = false;
  bool _obscurePassword = true;
  String? _errorMessage;

  @override
  void dispose() {
    _emailController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_loading) return;
    final normalizedEmail = _emailController.text.trim();
    _emailController.value = TextEditingValue(
      text: normalizedEmail,
      selection: TextSelection.collapsed(offset: normalizedEmail.length),
    );
    if (!_formKey.currentState!.validate()) return;
    setState(() {
      _loading = true;
      _errorMessage = null;
    });

    try {
      final session = await widget.authApi.login(
        email: _emailController.text,
        password: _passwordController.text,
      );
      await (widget.sessionStore ?? SessionDatabase.instance).save(session);
      if (!mounted) return;
      widget.onAuthenticated(session);
    } on ApiException catch (error) {
      if (mounted) setState(() => _errorMessage = error.message);
    } catch (_) {
      if (mounted) {
        setState(
          () => _errorMessage =
              'No fue posible guardar la sesión en este dispositivo.',
        );
      }
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    body: PaperBackground(
      child: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.fromLTRB(24, 36, 24, 32),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 440),
              child: AutofillGroup(
                child: Form(
                  key: _formKey,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      const BrandWordmark(centered: true),
                      const SizedBox(height: 54),
                      Text(
                        'Bienvenido de nuevo',
                        textAlign: TextAlign.center,
                        style: Theme.of(context).textTheme.headlineMedium,
                      ),
                      const SizedBox(height: 10),
                      Text(
                        'Inicia sesión para gestionar tus servicios y pagos.',
                        textAlign: TextAlign.center,
                        style: Theme.of(context).textTheme.bodyLarge,
                      ),
                      const SizedBox(height: 34),
                      TextFormField(
                        controller: _emailController,
                        enabled: !_loading,
                        keyboardType: TextInputType.emailAddress,
                        textInputAction: TextInputAction.next,
                        autofillHints: const [AutofillHints.email],
                        autocorrect: false,
                        decoration: const InputDecoration(
                          labelText: 'Correo electrónico',
                          prefixIcon: Icon(Icons.mail_outline_rounded),
                        ),
                        validator: LoginValidators.email,
                      ),
                      const SizedBox(height: 16),
                      TextFormField(
                        controller: _passwordController,
                        enabled: !_loading,
                        obscureText: _obscurePassword,
                        textInputAction: TextInputAction.done,
                        autofillHints: const [AutofillHints.password],
                        onFieldSubmitted: (_) => _submit(),
                        decoration: InputDecoration(
                          labelText: 'Contraseña',
                          prefixIcon: const Icon(Icons.lock_outline_rounded),
                          suffixIcon: IconButton(
                            tooltip: _obscurePassword
                                ? 'Mostrar contraseña'
                                : 'Ocultar contraseña',
                            onPressed: _loading
                                ? null
                                : () => setState(
                                    () => _obscurePassword = !_obscurePassword,
                                  ),
                            icon: Icon(
                              _obscurePassword
                                  ? Icons.visibility_outlined
                                  : Icons.visibility_off_outlined,
                            ),
                          ),
                        ),
                        validator: LoginValidators.password,
                      ),
                      if (_errorMessage != null) ...[
                        const SizedBox(height: 18),
                        _InlineMessage(
                          message: _errorMessage!,
                          icon: Icons.error_outline_rounded,
                        ),
                      ],
                      const SizedBox(height: 24),
                      FilledButton(
                        onPressed: _loading ? null : _submit,
                        child: _loading
                            ? const SizedBox.square(
                                dimension: 22,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                  color: warmSurface,
                                ),
                              )
                            : const Text('Iniciar sesión'),
                      ),
                      const SizedBox(height: 20),
                      Text(
                        '¿Aún no tienes acceso? Solicita el registro a un ejecutivo.',
                        textAlign: TextAlign.center,
                        style: Theme.of(context).textTheme.bodyMedium,
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    ),
  );
}

class _InlineMessage extends StatelessWidget {
  const _InlineMessage({required this.message, required this.icon});

  final String message;
  final IconData icon;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(14),
    decoration: BoxDecoration(
      color: const Color(0xFFF4E3DE),
      borderRadius: BorderRadius.circular(16),
      border: Border.all(color: const Color(0xFFD9B4A9)),
    ),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(icon, color: const Color(0xFF8A382E), size: 21),
        const SizedBox(width: 10),
        Expanded(
          child: Text(
            message,
            style: const TextStyle(color: Color(0xFF6F3028), height: 1.4),
          ),
        ),
      ],
    ),
  );
}
