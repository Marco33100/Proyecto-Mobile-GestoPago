import 'package:flutter/material.dart';

import '../models/login_challenge.dart';
import '../models/login_session.dart';
import '../widgets/second_factor_step.dart';
import '../services/auth_api_service.dart';
import '../storage/session_store_factory.dart';
import '../storage/session_store.dart';
import '../theme.dart';
import '../validation/login_validators.dart';
import '../widgets/brand_wordmark.dart';
import '../widgets/paper_background.dart';
import '../widgets/status_message.dart';
import '../widgets/surfaces.dart';

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
  LoginChallenge? _challenge;
  VerifiedLogin? _verified;

  /// El usuario aceptó cerrar la sesión abierta en otro dispositivo.
  bool _closePrevious = false;

  @override
  void dispose() {
    _emailController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  /// Paso 1: contraseña. Si es correcta, pasa al segundo factor.
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
      final challenge = await widget.authApi.login(
        email: _emailController.text,
        password: _passwordController.text,
      );
      if (!mounted) return;
      _passwordController.clear();
      setState(() => _challenge = challenge);
    } on ApiException catch (error) {
      if (mounted) setState(() => _errorMessage = error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  /// Paso 2: código del autenticador o de recuperación.
  Future<void> _verify({String? code, String? recoveryCode}) async {
    final challenge = _challenge;
    if (_loading || challenge == null) return;
    setState(() {
      _loading = true;
      _errorMessage = null;
    });
    ActiveSessionException? activeSession;
    try {
      final verified = await widget.authApi.verifyLogin(
        challenge: challenge,
        code: code,
        recoveryCode: recoveryCode,
        closePrevious: _closePrevious,
      );
      await (widget.sessionStore ?? createSessionStore(widget.authApi)).save(
        verified.session,
      );
      if (!mounted) return;
      if (verified.recoveryCodes.isEmpty) {
        widget.onAuthenticated(verified.session);
      } else {
        // Recién vinculado: mostrar los códigos antes de entrar.
        setState(() => _verified = verified);
      }
    } on ActiveSessionException catch (error) {
      activeSession = error;
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
    // El código no se gastó: si confirma, se reenvía el mismo.
    if (activeSession != null && mounted) {
      if (await _confirmClosePrevious(activeSession)) {
        _closePrevious = true;
        await _verify(code: code, recoveryCode: recoveryCode);
      } else if (mounted) {
        setState(
          () => _errorMessage =
              'No se inició sesión. Tu otra sesión sigue abierta.',
        );
      }
    }
  }

  Future<bool> _confirmClosePrevious(ActiveSessionException session) async {
    final where = sessionPlatformLabel(session.platform);
    final since = _sinceText(session.startedAt);
    return await showDialog<bool>(
          context: context,
          builder: (context) => AlertDialog(
            icon: const Icon(Icons.devices_outlined),
            title: const Text('Ya tienes una sesión abierta'),
            content: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 420),
              child: Text(
                'Tu cuenta está abierta en $where$since. Solo puedes tener '
                'una sesión a la vez: si continúas, esa sesión se cerrará.',
              ),
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context, false),
                child: const Text('Cancelar'),
              ),
              FilledButton(
                style: FilledButton.styleFrom(minimumSize: const Size(120, 48)),
                onPressed: () => Navigator.pop(context, true),
                child: const Text('Cerrar la otra sesión y entrar'),
              ),
            ],
          ),
        ) ??
        false;
  }

  static String _sinceText(DateTime? startedAt) {
    if (startedAt == null) return '';
    String two(int value) => value.toString().padLeft(2, '0');
    final time = '${two(startedAt.hour)}:${two(startedAt.minute)}';
    final now = DateTime.now();
    final today =
        startedAt.year == now.year &&
        startedAt.month == now.month &&
        startedAt.day == now.day;
    return today
        ? ' desde las $time'
        : ' desde el ${two(startedAt.day)}/${two(startedAt.month)} a las $time';
  }

  void _restart() => setState(() {
    _challenge = null;
    _verified = null;
    _errorMessage = null;
    _closePrevious = false;
  });

  Widget _secondFactor(BuildContext context) {
    final verified = _verified;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const BrandWordmark(centered: true, compact: true),
        const SizedBox(height: AppSpace.xxl),
        if (verified != null)
          RecoveryCodesStep(
            codes: verified.recoveryCodes,
            onDone: () => widget.onAuthenticated(verified.session),
          )
        else
          SecondFactorStep(
            challenge: _challenge!,
            busy: _loading,
            error: _errorMessage,
            onVerify: _verify,
            onCancel: _restart,
          ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    body: LayoutBuilder(
      builder: (context, constraints) {
        final wide = constraints.maxWidth >= Breakpoints.expanded;
        // La marca y los adornos también van en web, como en el teléfono.
        final form = _buildForm(context);
        if (!wide) return form;
        return Row(
          children: [
            const Expanded(
              flex: 5,
              child: Padding(
                padding: EdgeInsets.all(AppSpace.lg),
                child: _BrandPanel(),
              ),
            ),
            Expanded(flex: 4, child: form),
          ],
        );
      },
    ),
  );

  Widget _buildForm(BuildContext context) => PaperBackground(
    child: Stack(
      children: [
        const Positioned(right: -70, bottom: -50, child: CornerDiamonds()),
        _formBody(context),
      ],
    ),
  );

  Widget _formBody(BuildContext context) => SafeArea(
    child: Center(
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(24, 36, 24, 32),
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: ContentWidth.auth),
          child: _challenge != null
              ? _secondFactor(context)
              : AutofillGroup(
                  child: Form(
                    key: _formKey,
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        const BrandWordmark(centered: true),
                        const SizedBox(height: 44),
                        Text(
                          'Hola de nuevo',
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
                            prefixIcon: _FieldIcon(Icons.mail_outline_rounded),
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
                            prefixIcon: const _FieldIcon(
                              Icons.lock_outline_rounded,
                            ),
                            suffixIcon: IconButton(
                              tooltip: _obscurePassword
                                  ? 'Mostrar contraseña'
                                  : 'Ocultar contraseña',
                              onPressed: _loading
                                  ? null
                                  : () => setState(
                                      () =>
                                          _obscurePassword = !_obscurePassword,
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
                          StatusMessage(
                            message: _errorMessage!,
                            icon: Icons.error_outline_rounded,
                            tone: StatusTone.error,
                          ),
                        ],
                        const SizedBox(height: 28),
                        FilledButton(
                          style: FilledButton.styleFrom(
                            shape: const StadiumBorder(),
                            minimumSize: const Size.fromHeight(56),
                          ),
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
  );
}

/// Ícono de campo seguido de una línea divisoria, como en el diseño móvil.
class _FieldIcon extends StatelessWidget {
  const _FieldIcon(this.icon);

  final IconData icon;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(left: AppSpace.lg, right: AppSpace.md),
    child: Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, color: mutedInk),
        const SizedBox(width: AppSpace.md),
        Container(width: 1, height: 26, color: outlineColor),
      ],
    ),
  );
}

/// Panel de marca del login en pantallas anchas: qué es Gestopago y por qué
/// confiar en él, para que la pantalla no se sienta vacía.
class _BrandPanel extends StatelessWidget {
  const _BrandPanel();

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    // En ventanas bajas se omiten los beneficios para no desbordar el panel.
    final roomy = MediaQuery.sizeOf(context).height >= 720;
    return InkPanel(
      radius: AppRadius.lg + 8,
      padding: const EdgeInsets.fromLTRB(56, 48, 56, 48),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Spacer(),
          ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 480),
            child: Text(
              'Paga tus servicios con claridad y seguridad.',
              style: textTheme.displaySmall?.copyWith(color: warmSurface),
            ),
          ),
          const SizedBox(height: AppSpace.lg),
          ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 440),
            child: Text(
              'Consulta tus saldos, elige un servicio y autoriza cada pago '
              'con un código que solo tú tienes.',
              style: textTheme.bodyLarge?.copyWith(color: onInkMuted),
            ),
          ),
          if (roomy) ...const [
            SizedBox(height: AppSpace.xxl),
            _Feature(
              icon: Icons.verified_user_outlined,
              title: 'Doble factor al entrar y al pagar',
              text: 'Nadie entra ni cobra sin tu código del autenticador.',
            ),
            _Feature(
              icon: Icons.account_balance_wallet_outlined,
              title: 'Tus cuentas en un solo lugar',
              text: 'Saldo disponible siempre a la vista.',
            ),
            _Feature(
              icon: Icons.support_agent_outlined,
              title: 'Respaldo de un ejecutivo',
              text: 'Tu alta y cualquier aclaración, con una persona.',
            ),
          ],
          const Spacer(),
          Text(
            'Sesión protegida con cookie segura y verificación CSRF.',
            style: textTheme.bodySmall?.copyWith(color: onInkMuted),
          ),
        ],
      ),
    );
  }
}

class _Feature extends StatelessWidget {
  const _Feature({required this.icon, required this.title, required this.text});

  final IconData icon;
  final String title;
  final String text;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return Padding(
      padding: const EdgeInsets.only(bottom: AppSpace.lg),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          IconBadge(
            icon: icon,
            size: 40,
            background: inkRaised,
            foreground: terracottaOnInk,
          ),
          const SizedBox(width: AppSpace.lg),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: textTheme.titleMedium?.copyWith(color: warmSurface),
                ),
                Text(
                  text,
                  style: textTheme.bodyMedium?.copyWith(color: onInkMuted),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
