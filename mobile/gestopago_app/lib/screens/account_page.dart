import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import '../models/login_session.dart';
import '../services/payment_api_service.dart';
import '../services/profile_api_service.dart';
import '../validation/cliente_validators.dart';
import '../widgets/management_layout.dart';

class AccountPage extends StatefulWidget {
  const AccountPage({
    super.key,
    required this.session,
    required this.onLogout,
    this.isLoggingOut = false,
    this.api,
  });
  final LoginSession session;
  final Future<void> Function() onLogout;
  final bool isLoggingOut;
  final ProfileApiService? api;
  @override
  State<AccountPage> createState() => _AccountPageState();
}

class _AccountPageState extends State<AccountPage> {
  final _client = http.Client();
  late final _api =
      widget.api ??
      ProfileApiService(PaymentApiService(_client, widget.session));
  final _form = GlobalKey<FormState>();
  final _current = TextEditingController();
  final _replacement = TextEditingController();
  final _confirmation = TextEditingController();
  Map<String, dynamic>? _profile;
  bool _loading = false;
  bool _saving = false;
  bool _closing = false;
  bool _editing = false;
  String? _error;
  String? _notice;
  bool get _busy => _loading || _saving || _closing || widget.isLoggingOut;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _client.close();
    _current.dispose();
    _replacement.dispose();
    _confirmation.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    if (_busy) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final data = await _api.profile();
      if (mounted) setState(() => _profile = data);
    } catch (e) {
      if (mounted) {
        setState(
          () => _error = e is PaymentFailure
              ? e.message
              : 'No se pudo consultar tu perfil.',
        );
      }
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _logout() async {
    if (_saving || _closing || widget.isLoggingOut) return;
    setState(() => _closing = true);
    await widget.onLogout();
    if (mounted) setState(() => _closing = false);
  }

  Future<void> _savePassword() async {
    if (_busy || !_form.currentState!.validate()) return;
    final confirmed = await confirmManagementAction(
      context,
      'Cambiar contraseña',
      'Se cerrará tu sesión. Después tendrás que iniciar sesión con la nueva contraseña.',
    );
    if (!confirmed || !mounted || _busy) return;
    setState(() {
      _saving = true;
      _error = null;
      _notice = null;
    });
    try {
      await _api.changePassword(_current.text, _replacement.text);
      if (!mounted) return;
      setState(
        () => _notice =
            'Contraseña actualizada. Inicia sesión con la nueva contraseña.',
      );
      await widget.onLogout();
    } catch (e) {
      if (mounted) {
        setState(
          () => _error = e is PaymentFailure
              ? e.status == null || e.status! >= 500
                    ? 'No se pudo confirmar el cambio. Intenta iniciar sesión con la nueva contraseña antes de repetirlo.'
                    : e.message
              : 'No se pudo confirmar el cambio de contraseña.',
        );
      }
    } finally {
      if (mounted) {
        _current.clear();
        _replacement.clear();
        _confirmation.clear();
        setState(() => _saving = false);
      }
    }
  }

  Widget _password(
    TextEditingController controller,
    String key,
    String label,
    String? Function(String?) validator,
  ) => Padding(
    padding: const EdgeInsets.only(bottom: 14),
    child: TextFormField(
      key: ValueKey(key),
      controller: controller,
      obscureText: true,
      enableSuggestions: false,
      autocorrect: false,
      enabled: !_busy,
      decoration: InputDecoration(labelText: label),
      validator: validator,
    ),
  );

  @override
  Widget build(BuildContext context) => ManagementLayout(
    title: 'Tu cuenta',
    busy: _loading || _saving,
    error: _error,
    children: [
      if (_notice != null) Text(_notice!),
      Text(
        _profile == null
            ? 'Datos de la sesión. Actualiza para consultar el perfil del servidor.'
            : 'Perfil actualizado desde el servidor.',
      ),
      const SizedBox(height: 16),
      ManagementCard(
        children: [
          _value('Nombre', _profile?['nombre'] ?? widget.session.fullName),
          _value('Correo', _profile?['correo'] ?? widget.session.email),
          _value(
            'Tipo de acceso',
            widget.session.isExecutive ? 'Ejecutivo' : 'Cliente',
          ),
          if (_profile != null) ...[
            _value(
              'Teléfono',
              _profile!['telefono'] ?? 'Sin teléfono asociado',
            ),
            _value(
              'Estado',
              _profile!['activo'] == true ? 'Activo' : 'Inactivo',
            ),
            _value('Registro', _profile!['fechaCreacion'] ?? 'Sin dato'),
          ],
        ],
      ),
      TextButton.icon(
        onPressed: _busy ? null : _load,
        icon: const Icon(Icons.refresh),
        label: const Text('Actualizar perfil'),
      ),
      OutlinedButton.icon(
        onPressed: _busy ? null : () => setState(() => _editing = !_editing),
        icon: const Icon(Icons.lock_outline),
        label: const Text('Cambiar contraseña'),
      ),
      if (_editing) ...[
        const SizedBox(height: 16),
        const Text(
          'El cambio requiere tu contraseña actual y cerrará la sesión.',
        ),
        const SizedBox(height: 16),
        Form(
          key: _form,
          child: Column(
            children: [
              _password(
                _current,
                'password-current',
                'Contraseña actual',
                (v) => v == null || v.isEmpty
                    ? 'Escribe tu contraseña actual'
                    : v.length > 72
                    ? 'Máximo 72 caracteres'
                    : null,
              ),
              _password(
                _replacement,
                'password-new',
                'Nueva contraseña',
                ClienteValidators.password,
              ),
              _password(
                _confirmation,
                'password-confirm',
                'Confirmar nueva contraseña',
                (v) => v != _replacement.text
                    ? 'Las contraseñas no coinciden'
                    : null,
              ),
              FilledButton(
                onPressed: _busy ? null : _savePassword,
                child: const Text('Guardar contraseña'),
              ),
            ],
          ),
        ),
      ],
      const SizedBox(height: 24),
      OutlinedButton.icon(
        onPressed: _saving || _closing || widget.isLoggingOut ? null : _logout,
        icon: const Icon(Icons.logout_rounded),
        label: Text(
          _closing || widget.isLoggingOut ? 'Cerrando sesión' : 'Cerrar sesión',
        ),
      ),
    ],
  );

  Widget _value(String label, Object value) => Padding(
    padding: const EdgeInsets.only(bottom: 14),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: Theme.of(context).textTheme.bodySmall),
        const SizedBox(height: 4),
        SelectableText(
          '$value',
          style: Theme.of(context).textTheme.titleMedium,
        ),
      ],
    ),
  );
}
