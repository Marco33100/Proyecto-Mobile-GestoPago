import 'package:flutter/material.dart';
import '../config/http_client_factory.dart';
import '../models/login_session.dart';
import '../services/payment_api_service.dart';
import '../services/profile_api_service.dart';
import '../utils/display_format.dart';
import 'edit_profile_page.dart';
import '../theme.dart';
import '../validation/cliente_validators.dart';
import '../widgets/management_layout.dart';
import '../widgets/status_message.dart';
import '../widgets/surfaces.dart';

class AccountPage extends StatefulWidget {
  const AccountPage({
    super.key,
    required this.session,
    required this.onLogout,
    this.isLoggingOut = false,
    this.api,
    this.onSecurity,
  });
  final LoginSession session;
  final Future<void> Function() onLogout;
  final bool isLoggingOut;
  final ProfileApiService? api;

  /// Abre "Seguridad de pagos"; sin él no se muestra el botón.
  final VoidCallback? onSecurity;
  @override
  State<AccountPage> createState() => _AccountPageState();
}

class _AccountPageState extends State<AccountPage> {
  final _client = createHttpClient();
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

  Future<void> _editMyData() async {
    final saved = await Navigator.of(
      context,
    ).push<bool>(MaterialPageRoute(builder: (_) => EditProfilePage(api: _api)));
    if (saved != true || !mounted) return;
    setState(() => _notice = 'Tus datos se actualizaron.');
    await _load();
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
  Widget build(BuildContext context) {
    final name = '${_profile?['nombre'] ?? widget.session.fullName}';
    final email = '${_profile?['correo'] ?? widget.session.email}';
    final client = !widget.session.isExecutive;
    final details = ManagementCard(
      children: [
        Text(
          'Datos de contacto',
          style: Theme.of(context).textTheme.titleMedium,
        ),
        const SizedBox(height: AppSpace.lg),
        if (_profile != null) ...[
          _value('Teléfono', _profile!['telefono'] ?? 'Sin teléfono asociado'),
          _value(
            'Fecha de registro',
            formatDateTime(_profile!['fechaCreacion']) ?? 'Sin dato',
          ),
        ] else
          Text(
            _loading
                ? 'Consultando tus datos…'
                : 'Usa Actualizar perfil para ver tus datos.',
          ),
      ],
    );
    final actions = Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (client) ...[
          ActionCard(
            icon: Icons.edit_outlined,
            title: 'Editar mis datos',
            description: 'Teléfonos, domicilio y empleo.',
            accent: true,
            onTap: () => _busy ? null : _editMyData(),
          ),
          const SizedBox(height: AppSpace.md),
        ],
        ActionCard(
          icon: Icons.lock_outline_rounded,
          title: 'Cambiar contraseña',
          description: 'Pide tu contraseña actual y cierra tu sesión.',
          onTap: () => _busy ? null : setState(() => _editing = !_editing),
        ),
        // El autenticador solo se administra desde /api/pagos (rol cliente).
        if (client && widget.onSecurity != null) ...[
          const SizedBox(height: AppSpace.md),
          ActionCard(
            icon: Icons.verified_user_outlined,
            title: 'Seguridad de pagos',
            description: 'Tu autenticador para autorizar pagos.',
            onTap: () => _busy ? null : widget.onSecurity!(),
          ),
        ],
      ],
    );
    return ManagementLayout(
      title: 'Tu cuenta',
      // En computadora: datos y acciones lado a lado.
      maxWidth: 960,
      busy: _loading || _saving,
      error: _error,
      children: [
        if (_notice != null) ...[
          StatusMessage(
            message: _notice!,
            icon: Icons.check_circle_outline_rounded,
          ),
          const SizedBox(height: AppSpace.lg),
        ],
        _ProfileHeader(
          name: name,
          email: email,
          role: widget.session.isExecutive ? 'Ejecutivo' : 'Cliente',
          active: _profile == null ? null : _profile!['activo'] == true,
          onRefresh: _busy ? null : _load,
        ),
        const SizedBox(height: AppSpace.xl),
        LayoutBuilder(
          builder: (context, constraints) {
            if (constraints.maxWidth < 720) {
              return Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  details,
                  const SizedBox(height: AppSpace.lg),
                  actions,
                ],
              );
            }
            return Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(child: details),
                const SizedBox(width: AppSpace.xl),
                Expanded(child: actions),
              ],
            );
          },
        ),
        if (_editing) ...[
          const SizedBox(height: AppSpace.xl),
          ManagementCard(
            children: [
              Text(
                'Cambiar contraseña',
                style: Theme.of(context).textTheme.titleMedium,
              ),
              const SizedBox(height: AppSpace.sm),
              const Text(
                'El cambio requiere tu contraseña actual y cerrará la sesión.',
              ),
              const SizedBox(height: AppSpace.lg),
              Form(
                key: _form,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
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
                    Align(
                      alignment: Alignment.centerRight,
                      child: FilledButton(
                        style: FilledButton.styleFrom(
                          minimumSize: const Size(220, 52),
                        ),
                        onPressed: _busy ? null : _savePassword,
                        child: const Text('Guardar contraseña'),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ],
        const SizedBox(height: AppSpace.xxl),
        Center(
          child: TextButton.icon(
            style: TextButton.styleFrom(foregroundColor: errorOnSurface),
            onPressed: _saving || _closing || widget.isLoggingOut
                ? null
                : _logout,
            icon: const Icon(Icons.logout_rounded),
            label: Text(
              _closing || widget.isLoggingOut
                  ? 'Cerrando sesión'
                  : 'Cerrar sesión',
            ),
          ),
        ),
      ],
    );
  }

  Widget _value(String label, Object value) => Padding(
    padding: const EdgeInsets.only(bottom: AppSpace.lg),
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

/// Encabezado: quién es, su rol y si la cuenta está activa.
class _ProfileHeader extends StatelessWidget {
  const _ProfileHeader({
    required this.name,
    required this.email,
    required this.role,
    required this.active,
    required this.onRefresh,
  });

  final String name;
  final String email;
  final String role;
  final bool? active;
  final VoidCallback? onRefresh;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return InkPanel(
      padding: const EdgeInsets.all(AppSpace.xl),
      child: Row(
        children: [
          CircleAvatar(
            radius: 32,
            backgroundColor: warmSurface,
            foregroundColor: ink,
            child: Text(
              initialsOf(name),
              style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 20),
            ),
          ),
          const SizedBox(width: AppSpace.lg),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  name,
                  style: textTheme.titleLarge?.copyWith(color: warmSurface),
                ),
                const SizedBox(height: 2),
                Text(
                  email,
                  overflow: TextOverflow.ellipsis,
                  style: textTheme.bodyMedium?.copyWith(color: onInkMuted),
                ),
                const SizedBox(height: AppSpace.md),
                Wrap(
                  spacing: AppSpace.sm,
                  runSpacing: AppSpace.sm,
                  children: [
                    _Tag(text: role),
                    if (active != null)
                      _Tag(
                        text: active! ? 'Activo' : 'Inactivo',
                        icon: active!
                            ? Icons.check_circle_rounded
                            : Icons.block_rounded,
                      ),
                  ],
                ),
              ],
            ),
          ),
          IconButton(
            tooltip: 'Actualizar perfil',
            color: warmSurface,
            onPressed: onRefresh,
            icon: const Icon(Icons.refresh_rounded),
          ),
        ],
      ),
    );
  }
}

class _Tag extends StatelessWidget {
  const _Tag({required this.text, this.icon});

  final String text;
  final IconData? icon;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
    decoration: BoxDecoration(
      color: warmSurface.withValues(alpha: 0.14),
      borderRadius: BorderRadius.circular(999),
    ),
    child: Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        if (icon != null) ...[
          Icon(icon, size: 14, color: warmSurface),
          const SizedBox(width: 4),
        ],
        Text(
          text,
          style: Theme.of(
            context,
          ).textTheme.labelMedium?.copyWith(color: warmSurface),
        ),
      ],
    ),
  );
}
