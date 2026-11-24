import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../services/admin_api_service.dart';
import '../services/auth_api_service.dart';
import '../services/payment_api_service.dart';
import '../theme.dart';
import '../utils/money_format.dart';
import '../utils/date_only.dart';
import '../validation/input_filters.dart';
import '../widgets/surfaces.dart';
import '../widgets/management_layout.dart';
import '../widgets/responsive.dart';
import 'register_page.dart';

class AdminSearchPage extends StatefulWidget {
  const AdminSearchPage({
    super.key,
    required this.section,
    required this.api,
    required this.authApi,
  });
  final String section;
  final AdminApiService api;
  final AuthApiService authApi;
  @override
  State<AdminSearchPage> createState() => _AdminSearchPageState();
}

class _AdminSearchPageState extends State<AdminSearchPage> {
  final _form = GlobalKey<FormState>();
  final _fields = <String, TextEditingController>{};
  Map<String, dynamic> _filters = {};
  AdminPageResult? _result;
  bool? _active;
  bool _busy = false;
  bool _moreFilters = false;
  String? _error;
  bool get _clients => widget.section == 'clientes';

  @override
  void initState() {
    super.initState();
    _load(0);
  }

  @override
  void dispose() {
    for (final c in _fields.values) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _load(int page) async {
    if (_busy) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final result = await widget.api.search(widget.section, _filters, page);
      if (mounted) setState(() => _result = result);
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = e is PaymentFailure
              ? e.message
              : 'No se pudo cargar la información.';
          _result = null;
        });
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  /// Filtros avanzados con valor: se cuentan en "Más filtros".
  int get _advancedInUse => const [
    'correo',
    'numeroCuenta',
    'id',
    'desde',
    'hasta',
  ].where((key) => (_fields[key]?.text.trim() ?? '').isNotEmpty).length;

  void _clear() {
    for (final controller in _fields.values) {
      controller.clear();
    }
    setState(() => _active = null);
    _filters = {};
    _load(0);
  }

  void _search() {
    if (!_form.currentState!.validate()) return;
    _filters = {
      for (final entry in _fields.entries)
        if (entry.value.text.trim().isNotEmpty)
          entry.key: entry.key == 'id'
              ? int.parse(entry.value.text.trim())
              : entry.value.text.trim(),
      if (_active != null) 'activo': _active,
    };
    _load(0);
  }

  Widget _field(String key, String label, {int max = 100}) => Padding(
    padding: const EdgeInsets.only(bottom: 14),
    child: TextFormField(
      key: ValueKey('filter-$key'),
      controller: _fields.putIfAbsent(key, TextEditingController.new),
      enabled: !_busy,
      decoration: InputDecoration(labelText: label),
      inputFormatters: [
        LengthLimitingTextInputFormatter(max),
        ...switch (key) {
          'id' || 'telefono' => [FilteringTextInputFormatter.digitsOnly],
          'desde' ||
          'hasta' => [FilteringTextInputFormatter.allow(RegExp(r'[0-9-]'))],
          _ => InputFilters.forField(key),
        },
      ],
      keyboardType: key == 'id' || key == 'telefono'
          ? TextInputType.number
          : TextInputType.text,
      validator: (value) {
        final text = value?.trim() ?? '';
        if (text.isEmpty) return null;
        if (key == 'desde' || key == 'hasta') {
          final date = DateOnly.tryParse(text);
          if (date == null) return 'Usa una fecha válida: AAAA-MM-DD';
          final from = DateOnly.tryParse(_fields['desde']?.text.trim() ?? '');
          if (key == 'hasta' && from != null && date.isBefore(from)) {
            return 'La fecha final no puede ser anterior a la inicial';
          }
        }
        if (key == 'id' &&
            (int.tryParse(text) == null || int.parse(text) <= 0)) {
          return 'Escribe un identificador positivo';
        }
        if (key == 'correo' &&
            !RegExp(r'^[^\s@]+@[^\s@]+\.[^\s@]+$').hasMatch(text)) {
          return 'Escribe un correo válido';
        }
        if (key == 'telefono' && !RegExp(r'^\d{10}$').hasMatch(text)) {
          return 'Escribe diez dígitos';
        }
        return null;
      },
    ),
  );

  Future<void> _detail(Map<String, dynamic> client) async {
    final changed = await Navigator.push<bool>(
      context,
      MaterialPageRoute(
        builder: (_) => ClientDetailPage(
          client: client,
          api: widget.api,
          authApi: widget.authApi,
        ),
      ),
    );
    if (changed == true && mounted) await _load(_result?.page ?? 0);
  }

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    final primary = _clients
        ? [
            _field('nombre', 'Nombre', max: 50),
            _field('rfc', 'RFC', max: 13),
            _field('curp', 'CURP', max: 18),
          ]
        : [
            _field('nombre', 'Nombre', max: 203),
            _field('correo', 'Correo exacto', max: 254),
            _field('telefono', 'Teléfono móvil', max: 10),
          ];
    final inUse = _advancedInUse;
    return ManagementLayout(
      title: _clients ? 'Clientes' : 'Usuarios',
      busy: _busy,
      error: _error,
      maxWidth: ContentWidth.wide,
      children: [
        Text(
          _clients
              ? 'Busca por nombre, RFC o CURP; basta con una parte. Para editar o dar de baja, abre el cliente.'
              : 'Accesos para iniciar sesión (clientes y ejecutivos). Para editar datos de una persona usa Clientes.',
        ),
        const SizedBox(height: AppSpace.lg),
        ManagementCard(
          children: [
            Form(
              key: _form,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  ResponsiveGrid(
                    minItemWidth: 240,
                    spacing: 16,
                    children: primary,
                  ),
                  if (_clients && _moreFilters) ...[
                    Text(
                      'Valores exactos',
                      style: textTheme.labelLarge?.copyWith(color: mutedInk),
                    ),
                    const SizedBox(height: AppSpace.sm),
                    ResponsiveGrid(
                      minItemWidth: 240,
                      spacing: 16,
                      children: [
                        _field('correo', 'Correo', max: 100),
                        _field('numeroCuenta', 'Número de cuenta', max: 24),
                        _field('id', 'ID de cliente', max: 18),
                        _field('desde', 'Registro desde (AAAA-MM-DD)', max: 10),
                        _field('hasta', 'Registro hasta (AAAA-MM-DD)', max: 10),
                      ],
                    ),
                  ],
                  Wrap(
                    spacing: AppSpace.lg,
                    runSpacing: AppSpace.md,
                    alignment: WrapAlignment.spaceBetween,
                    crossAxisAlignment: WrapCrossAlignment.center,
                    children: [
                      Wrap(
                        spacing: AppSpace.sm,
                        runSpacing: AppSpace.sm,
                        crossAxisAlignment: WrapCrossAlignment.center,
                        children: [
                          for (final (value, label) in const [
                            (null, 'Todos'),
                            (true, 'Activos'),
                            (false, 'Inactivos'),
                          ])
                            ChoiceChip(
                              label: Text(label),
                              selected: _active == value,
                              onSelected: _busy
                                  ? null
                                  : (_) => setState(() => _active = value),
                            ),
                          if (_clients)
                            TextButton.icon(
                              onPressed: () =>
                                  setState(() => _moreFilters = !_moreFilters),
                              icon: Icon(
                                _moreFilters
                                    ? Icons.expand_less_rounded
                                    : Icons.tune_rounded,
                              ),
                              label: Text(
                                _moreFilters
                                    ? 'Menos filtros'
                                    : inUse == 0
                                    ? 'Más filtros'
                                    : 'Más filtros ($inUse)',
                              ),
                            ),
                        ],
                      ),
                      Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          TextButton(
                            onPressed: _busy ? null : _clear,
                            child: const Text('Limpiar'),
                          ),
                          const SizedBox(width: AppSpace.sm),
                          FilledButton.icon(
                            style: FilledButton.styleFrom(
                              minimumSize: const Size(160, 48),
                            ),
                            onPressed: _busy ? null : _search,
                            icon: const Icon(Icons.search),
                            label: const Text('Buscar'),
                          ),
                        ],
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
        const SizedBox(height: AppSpace.xl),
        if (_result case final result?) ...[
          Text(
            result.total == 1 ? '1 resultado' : '${result.total} resultados',
            style: textTheme.titleMedium,
          ),
          const SizedBox(height: AppSpace.md),
          if (result.items.isEmpty) const Text('No se encontraron datos.'),
          ResponsiveGrid(
            children: [for (final item in result.items) _resultCard(item)],
          ),
          PaginationBar(
            page: result.page,
            isLast: result.last,
            busy: _busy,
            onPage: _load,
          ),
        ],
        if (_error != null)
          TextButton(
            onPressed: _busy ? null : () => _load(0),
            child: const Text('Reintentar consulta'),
          ),
      ],
    );
  }

  Widget _resultCard(Map<String, dynamic> item) {
    final textTheme = Theme.of(context).textTheme;
    final name = _clients ? clientName(item) : '${item['nombre'] ?? ''}';
    final active = item['activo'] == true;
    final muted = textTheme.bodySmall?.copyWith(color: mutedInk);
    return ManagementCard(
      children: [
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            CircleAvatar(
              radius: 22,
              backgroundColor: sage,
              foregroundColor: ink,
              child: Text(
                initialsOf(name),
                style: const TextStyle(fontWeight: FontWeight.w700),
              ),
            ),
            const SizedBox(width: AppSpace.md),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    name,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: textTheme.titleMedium,
                  ),
                  Text(
                    '${item['correo'] ?? ''}',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: textTheme.bodyMedium?.copyWith(color: mutedInk),
                  ),
                ],
              ),
            ),
            const SizedBox(width: AppSpace.sm),
            _StatusPill(active: active),
          ],
        ),
        const SizedBox(height: AppSpace.md),
        if (_clients) ...[
          Text('RFC  ${item['rfc'] ?? '—'}', style: muted),
          Text('CURP  ${item['curp'] ?? '—'}', style: muted),
        ] else ...[
          Text(
            'Teléfono  ${item['telefono'] ?? 'Sin teléfono asociado'}',
            style: muted,
          ),
          SelectableText('ID  ${item['id'] ?? ''}', style: muted),
        ],
        if (_clients)
          Align(
            alignment: Alignment.centerRight,
            child: TextButton.icon(
              onPressed: _busy ? null : () => _detail(item),
              iconAlignment: IconAlignment.end,
              icon: const Icon(Icons.arrow_forward_rounded),
              label: const Text('Ver cliente'),
            ),
          ),
      ],
    );
  }
}

/// Estado con color y texto (el color nunca va solo).
class _StatusPill extends StatelessWidget {
  const _StatusPill({required this.active});

  final bool active;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
    decoration: BoxDecoration(
      color: active ? successSurface : errorSurface,
      borderRadius: BorderRadius.circular(999),
    ),
    child: Text(
      active ? 'Activo' : 'Inactivo',
      style: Theme.of(
        context,
      ).textTheme.labelMedium?.copyWith(color: active ? ink : errorOnSurface),
    ),
  );
}

String clientName(Map<String, dynamic> client) =>
    ['nombre', 'segundoNombre', 'apellidoPaterno', 'apellidoMaterno']
        .map((key) => client[key]?.toString() ?? '')
        .where((part) => part.isNotEmpty)
        .join(' ');

class ClientDetailPage extends StatefulWidget {
  const ClientDetailPage({
    super.key,
    required this.client,
    required this.api,
    required this.authApi,
  });
  final Map<String, dynamic> client;
  final AdminApiService api;
  final AuthApiService authApi;
  @override
  State<ClientDetailPage> createState() => _ClientDetailPageState();
}

class _ClientDetailPageState extends State<ClientDetailPage> {
  bool _busy = false;
  String? _error;
  Future<void> _deactivate({String? account}) async {
    final confirmed = await confirmManagementAction(
      context,
      account == null ? 'Desactivar cliente' : 'Desactivar cuenta',
      account == null
          ? 'Se desactivará el cliente y sus cuentas. ¿Deseas continuar?'
          : 'La cuenta dejará de permitir operaciones. No se cambia su saldo.',
    );
    if (!confirmed || !mounted || _busy) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      if (account == null) {
        await widget.api.deactivateClient(widget.client['id'] as int);
      } else {
        await widget.api.deactivateAccount(account);
      }
      if (mounted) Navigator.pop(context, true);
    } catch (e) {
      if (mounted) {
        setState(
          () => _error = e is PaymentFailure
              ? e.message
              : 'No fue posible completar la operación.',
        );
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _edit() async {
    final result = await Navigator.push<String>(
      context,
      MaterialPageRoute(
        builder: (_) => RegisterPage(
          authApi: widget.authApi,
          session: widget.api.session,
          initialClient: widget.client,
          adminApi: widget.api,
        ),
      ),
    );
    if (result != null && mounted) Navigator.pop(context, true);
  }

  @override
  Widget build(BuildContext context) {
    final client = widget.client;
    final accounts = (client['cuentas'] as List? ?? [])
        .whereType<Map<String, dynamic>>();
    return ManagementLayout(
      title: 'Detalle del cliente',
      busy: _busy,
      error: _error,
      maxWidth: ContentWidth.wide,
      children: [
        Text(
          clientName(client),
          style: Theme.of(context).textTheme.headlineSmall,
        ),
        const SizedBox(height: 12),
        ResponsiveGrid(
          minItemWidth: 340,
          maxColumns: 2,
          children: [
            ManagementCard(
              children: [
                for (final field in [
                  ('correo', 'Correo'),
                  ('telefonoMovil', 'Teléfono móvil'),
                  ('telefonoAlternativo', 'Teléfono alternativo'),
                  ('curp', 'CURP'),
                  ('rfc', 'RFC'),
                  ('fechaNacimiento', 'Fecha de nacimiento'),
                  ('sexo', 'Sexo'),
                  ('estadoCivil', 'Estado civil'),
                  ('nacionalidad', 'Nacionalidad'),
                  ('ocupacion', 'Ocupación'),
                  ('empresa', 'Empresa'),
                ])
                  Padding(
                    padding: const EdgeInsets.only(bottom: 8),
                    child: SelectableText(
                      '${field.$2}: ${client[field.$1] ?? 'Sin dato'}',
                    ),
                  ),
                Text(
                  'Ingreso mensual: ${MoneyFormat.mxn(client['ingresoMensual'])}',
                ),
              ],
            ),
            if (client['domicilio'] case final Map address)
              ManagementCard(
                children: [
                  Text(
                    'Domicilio',
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  Text(
                    [
                          'calle',
                          'numeroExterior',
                          'numeroInterior',
                          'colonia',
                          'municipio',
                          'estado',
                          'codigoPostal',
                          'pais',
                        ]
                        .map((key) => address[key]?.toString() ?? '')
                        .where((v) => v.isNotEmpty)
                        .join(', '),
                  ),
                ],
              ),
            for (final account in accounts)
              ManagementCard(
                children: [
                  SelectableText('${account['numeroCuenta']}'),
                  Text(
                    'Disponible: ${MoneyFormat.mxn(account['saldoDisponible'])}',
                  ),
                  Text(
                    'Retenido: ${MoneyFormat.mxn(account['saldoRetenido'])}',
                  ),
                  Text(
                    account['activa'] == true
                        ? 'Cuenta activa'
                        : 'Cuenta inactiva',
                  ),
                  if (account['activa'] == true)
                    TextButton(
                      onPressed: _busy
                          ? null
                          : () => _deactivate(
                              account: account['numeroCuenta'] as String,
                            ),
                      child: const Text('Desactivar cuenta'),
                    ),
                ],
              ),
          ],
        ),
        Align(
          alignment: Alignment.centerLeft,
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 360),
            child: FilledButton.icon(
              onPressed: _busy ? null : _edit,
              icon: const Icon(Icons.edit_outlined),
              label: const Text('Editar información'),
            ),
          ),
        ),
        if (client['activo'] == true)
          TextButton(
            onPressed: _busy ? null : () => _deactivate(),
            child: const Text('Desactivar cliente y cuentas'),
          ),
      ],
    );
  }
}
