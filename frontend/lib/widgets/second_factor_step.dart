import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:qr_flutter/qr_flutter.dart';

import '../models/login_challenge.dart';
import '../theme.dart';
import 'status_message.dart';
import 'surfaces.dart';

final _codePattern = RegExp(r'^\d{6}$');
final _recoveryPattern = RegExp(r'^[A-F0-9]{8}(?:-[A-F0-9]{8}){3}$');

ButtonStyle _pill() => FilledButton.styleFrom(
  shape: const StadiumBorder(),
  minimumSize: const Size.fromHeight(56),
);

/// Segundo paso del inicio de sesión.
///
/// Si el usuario ya tiene autenticador, pide su código (o uno de recuperación).
/// Si no, lo guía para vincularlo: QR, clave y primer código.
class SecondFactorStep extends StatefulWidget {
  const SecondFactorStep({
    super.key,
    required this.challenge,
    required this.busy,
    required this.onVerify,
    required this.onCancel,
    this.error,
  });

  final LoginChallenge challenge;
  final bool busy;
  final String? error;
  final Future<void> Function({String? code, String? recoveryCode}) onVerify;
  final VoidCallback onCancel;

  @override
  State<SecondFactorStep> createState() => _SecondFactorStepState();
}

class _SecondFactorStepState extends State<SecondFactorStep> {
  final _code = TextEditingController();
  final _recovery = TextEditingController();
  bool _useRecovery = false;
  String? _localError;

  bool get _enrolling => !widget.challenge.configured;

  @override
  void dispose() {
    _code.dispose();
    _recovery.dispose();
    super.dispose();
  }

  void _submit() {
    if (widget.busy) return;
    if (_useRecovery) {
      final value = _recovery.text.trim().toUpperCase();
      if (!_recoveryPattern.hasMatch(value)) {
        setState(
          () => _localError =
              'El código de recuperación tiene cuatro grupos de ocho caracteres.',
        );
        return;
      }
      setState(() => _localError = null);
      widget.onVerify(recoveryCode: value);
      return;
    }
    final value = _code.text.trim();
    if (!_codePattern.hasMatch(value)) {
      setState(() => _localError = 'Escribe los seis dígitos del código.');
      return;
    }
    setState(() => _localError = null);
    widget.onVerify(code: value);
    _code.clear();
  }

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    final error = _localError ?? widget.error;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Center(
          child: IconBadge(
            icon: _enrolling
                ? Icons.phonelink_lock_outlined
                : Icons.verified_user_outlined,
            size: 64,
            background: _enrolling ? terracottaSoft : sage,
            foreground: _enrolling ? terracotta : ink,
          ),
        ),
        const SizedBox(height: AppSpace.lg),
        Semantics(
          header: true,
          child: Text(
            _enrolling ? 'Protege tu cuenta' : 'Verificación en dos pasos',
            textAlign: TextAlign.center,
            style: textTheme.headlineMedium,
          ),
        ),
        const SizedBox(height: AppSpace.sm),
        Text(
          _enrolling
              ? 'Cada inicio de sesión pide un código de tu app autenticadora. '
                    'Vincúlala una sola vez.'
              : _useRecovery
              ? 'Escribe uno de los códigos de recuperación que guardaste. '
                    'Cada uno sirve una sola vez.'
              : 'Abre tu app autenticadora y escribe el código de 6 dígitos '
                    'de Gestopago.',
          textAlign: TextAlign.center,
          style: textTheme.bodyLarge,
        ),
        const SizedBox(height: AppSpace.xl),
        if (_enrolling) ...[
          _EnrollmentGuide(challenge: widget.challenge),
          const SizedBox(height: AppSpace.xl),
        ],
        if (_useRecovery)
          TextField(
            key: const ValueKey('recovery-code'),
            controller: _recovery,
            enabled: !widget.busy,
            autofocus: true,
            textCapitalization: TextCapitalization.characters,
            autocorrect: false,
            enableSuggestions: false,
            textInputAction: TextInputAction.done,
            onSubmitted: (_) => _submit(),
            decoration: const InputDecoration(
              labelText: 'Código de recuperación',
              hintText: 'XXXXXXXX-XXXXXXXX-XXXXXXXX-XXXXXXXX',
              prefixIcon: Icon(Icons.key_outlined),
            ),
          )
        else
          TextField(
            key: const ValueKey('totp-code'),
            controller: _code,
            enabled: !widget.busy,
            autofocus: !_enrolling,
            keyboardType: TextInputType.number,
            textAlign: TextAlign.center,
            autofillHints: const [AutofillHints.oneTimeCode],
            inputFormatters: [
              FilteringTextInputFormatter.digitsOnly,
              LengthLimitingTextInputFormatter(6),
            ],
            style: textTheme.headlineSmall?.copyWith(letterSpacing: 8),
            textInputAction: TextInputAction.done,
            onSubmitted: (_) => _submit(),
            decoration: const InputDecoration(
              labelText: 'Código de 6 dígitos',
              hintText: '000000',
            ),
          ),
        if (error != null) ...[
          const SizedBox(height: AppSpace.lg),
          StatusMessage(
            message: error,
            icon: Icons.error_outline_rounded,
            tone: StatusTone.error,
          ),
        ],
        const SizedBox(height: AppSpace.xl),
        FilledButton(
          style: _pill(),
          onPressed: widget.busy ? null : _submit,
          child: widget.busy
              ? const SizedBox.square(
                  dimension: 22,
                  child: CircularProgressIndicator(
                    strokeWidth: 2,
                    color: warmSurface,
                  ),
                )
              : Text(_enrolling ? 'Activar y entrar' : 'Verificar'),
        ),
        const SizedBox(height: AppSpace.sm),
        if (!_enrolling)
          TextButton(
            onPressed: widget.busy
                ? null
                : () => setState(() {
                    _useRecovery = !_useRecovery;
                    _localError = null;
                    _code.clear();
                    _recovery.clear();
                  }),
            child: Text(
              _useRecovery
                  ? 'Usar el código del autenticador'
                  : 'Perdí mi teléfono: usar un código de recuperación',
            ),
          ),
        TextButton(
          onPressed: widget.busy ? null : widget.onCancel,
          child: const Text('Volver a iniciar sesión'),
        ),
      ],
    );
  }
}

/// Instrucciones, QR y clave para vincular el autenticador.
class _EnrollmentGuide extends StatelessWidget {
  const _EnrollmentGuide({required this.challenge});

  final LoginChallenge challenge;

  static const _steps = [
    'Instala Google Authenticator o Microsoft Authenticator.',
    'Escanea el código QR o escribe la clave manualmente.',
    'Escribe abajo el código de 6 dígitos que aparece.',
  ];

  String get _groupedSecret {
    final secret = challenge.secret ?? '';
    final groups = <String>[];
    for (var i = 0; i < secret.length; i += 4) {
      groups.add(
        secret.substring(i, i + 4 > secret.length ? secret.length : i + 4),
      );
    }
    return groups.join(' ');
  }

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return AppCard(
      padding: const EdgeInsets.all(AppSpace.xl),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          for (var index = 0; index < _steps.length; index++)
            Padding(
              padding: const EdgeInsets.only(bottom: AppSpace.md),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  CircleAvatar(
                    radius: 12,
                    backgroundColor: terracottaSoft,
                    foregroundColor: terracotta,
                    child: Text(
                      '${index + 1}',
                      style: const TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ),
                  const SizedBox(width: AppSpace.md),
                  Expanded(
                    child: Text(_steps[index], style: textTheme.bodyMedium),
                  ),
                ],
              ),
            ),
          const SizedBox(height: AppSpace.sm),
          Center(
            child: Container(
              padding: const EdgeInsets.all(AppSpace.md),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(AppRadius.md),
                border: Border.all(color: outlineColor),
              ),
              child: QrImageView(
                data: challenge.otpauthUri ?? '',
                size: 184,
                semanticsLabel: 'Código QR para vincular tu autenticador',
              ),
            ),
          ),
          const SizedBox(height: AppSpace.lg),
          Text(
            'Clave de configuración',
            textAlign: TextAlign.center,
            style: textTheme.labelMedium,
          ),
          const SizedBox(height: AppSpace.xs),
          SelectableText(
            _groupedSecret,
            textAlign: TextAlign.center,
            style: textTheme.titleMedium?.copyWith(
              letterSpacing: 1.2,
              fontFeatures: const [FontFeature.tabularFigures()],
            ),
          ),
          Center(
            child: TextButton.icon(
              onPressed: () async {
                await Clipboard.setData(
                  ClipboardData(text: challenge.secret ?? ''),
                );
                if (!context.mounted) return;
                ScaffoldMessenger.of(context)
                  ..hideCurrentSnackBar()
                  ..showSnackBar(
                    const SnackBar(content: Text('Clave copiada')),
                  );
              },
              icon: const Icon(Icons.copy_rounded),
              label: const Text('Copiar clave'),
            ),
          ),
          Text(
            'No compartas esta clave. La configuración vence en 5 minutos.',
            textAlign: TextAlign.center,
            style: textTheme.bodySmall,
          ),
        ],
      ),
    );
  }
}

/// Códigos de recuperación recién generados. Se muestran una sola vez.
class RecoveryCodesStep extends StatelessWidget {
  const RecoveryCodesStep({
    super.key,
    required this.codes,
    required this.onDone,
  });

  final List<String> codes;
  final VoidCallback onDone;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const Center(
          child: IconBadge(
            icon: Icons.shield_outlined,
            size: 64,
            background: successSurface,
          ),
        ),
        const SizedBox(height: AppSpace.lg),
        Semantics(
          header: true,
          child: Text(
            'Guarda tus códigos de recuperación',
            textAlign: TextAlign.center,
            style: textTheme.headlineMedium,
          ),
        ),
        const SizedBox(height: AppSpace.sm),
        Text(
          'Te permiten entrar si pierdes tu teléfono. Cada uno sirve una sola '
          'vez y no se volverán a mostrar.',
          textAlign: TextAlign.center,
          style: textTheme.bodyLarge,
        ),
        const SizedBox(height: AppSpace.xl),
        AppCard(
          padding: const EdgeInsets.symmetric(
            horizontal: AppSpace.lg,
            vertical: AppSpace.xl,
          ),
          child: SelectionArea(
            child: Column(
              children: [
                for (final code in codes)
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 5),
                    // Un código por renglón: se reduce en vez de partirse.
                    child: FittedBox(
                      fit: BoxFit.scaleDown,
                      child: Text(
                        code,
                        maxLines: 1,
                        style: textTheme.titleMedium?.copyWith(
                          letterSpacing: 0.4,
                          fontFeatures: const [FontFeature.tabularFigures()],
                        ),
                      ),
                    ),
                  ),
              ],
            ),
          ),
        ),
        const SizedBox(height: AppSpace.md),
        OutlinedButton.icon(
          style: OutlinedButton.styleFrom(backgroundColor: warmSurface),
          onPressed: () async {
            await Clipboard.setData(ClipboardData(text: codes.join('\n')));
            if (!context.mounted) return;
            ScaffoldMessenger.of(context)
              ..hideCurrentSnackBar()
              ..showSnackBar(const SnackBar(content: Text('Códigos copiados')));
          },
          icon: const Icon(Icons.copy_rounded),
          label: const Text('Copiar códigos'),
        ),
        const SizedBox(height: AppSpace.md),
        FilledButton(
          style: _pill(),
          onPressed: onDone,
          child: const Text('Ya los guardé, entrar'),
        ),
      ],
    );
  }
}
