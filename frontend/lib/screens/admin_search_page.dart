import 'package:flutter/material.dart';
import '../services/admin_api_service.dart';
import '../services/auth_api_service.dart';
import '../services/payment_api_service.dart';
import '../utils/money_format.dart';
import '../utils/date_only.dart';
import '../widgets/management_layout.dart';
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
      maxLength: max,
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
  Widget build(BuildContext context) => ManagementLayout(
    title: _clients ? 'Clientes' : 'Usuarios',
    busy: _busy,
    error: _error,
    children: [
      Form(
        key: _form,
        child: Column(
          children: [
            _field('nombre', 'Nombre', max: _clients ? 50 : 203),
            _field('correo', 'Correo exacto', max: _clients ? 100 : 254),
            if (_clients) ...[
              _field('rfc', 'RFC', max: 13),
              _field('curp', 'CURP', max: 18),
              _field('numeroCuenta', 'Número de cuenta', max: 24),
              _field('id', 'ID de cliente', max: 18),
              _field('desde', 'Registro desde (AAAA-MM-DD)', max: 10),
              _field('hasta', 'Registro hasta (AAAA-MM-DD)', max: 10),
            ] else
              _field('telefono', 'Teléfono móvil', max: 10),
            DropdownButtonFormField<String>(
              initialValue: 'todos',
              decoration: const InputDecoration(labelText: 'Estado'),
              items: const [
                DropdownMenuItem(value: 'todos', child: Text('Todos')),
                DropdownMenuItem(value: 'activo', child: Text('Activo')),
                DropdownMenuItem(value: 'inactivo', child: Text('Inactivo')),
              ],
              onChanged: _busy
                  ? null
                  : (value) => _active = switch (value) {
                      'activo' => true,
                      'inactivo' => false,
                      _ => null,
                    },
            ),
            const SizedBox(height: 12),
            Text(
              _clients
                  ? 'Busca coincidencias en nombre, RFC o CURP. Si no hay coincidencias, se muestra el listado. Correo, cuenta e ID requieren el valor completo.'
                  : 'Los filtros se combinan: se muestran usuarios que cumplen todos los criterios enviados.',
            ),
            const SizedBox(height: 18),
            FilledButton.icon(
              onPressed: _busy ? null : _search,
              icon: const Icon(Icons.search),
              label: const Text('Buscar'),
            ),
          ],
        ),
      ),
      const SizedBox(height: 24),
      if (_result case final result?) ...[
        Text(
          '${result.total} resultados · Página ${result.page + 1}',
          style: Theme.of(context).textTheme.titleMedium,
        ),
        const SizedBox(height: 12),
        if (result.items.isEmpty) const Text('No se encontraron datos.'),
        for (final item in result.items)
          ManagementCard(
            children: [
              Text(
                _clients ? clientName(item) : '${item['nombre'] ?? ''}',
                style: Theme.of(context).textTheme.titleLarge,
              ),
              const SizedBox(height: 6),
              Text('${item['correo'] ?? ''}'),
              Text(item['activo'] == true ? 'Activo' : 'Inactivo'),
              if (_clients)
                Text('RFC: ${item['rfc'] ?? ''} · CURP: ${item['curp'] ?? ''}'),
              if (!_clients) ...[
                SelectableText('ID: ${item['id'] ?? ''}'),
                Text(
                  'Teléfono: ${item['telefono'] ?? 'Sin teléfono asociado'}',
                ),
              ],
              if (_clients)
                TextButton.icon(
                  onPressed: _busy ? null : () => _detail(item),
                  icon: const Icon(Icons.arrow_forward),
                  label: const Text('Ver cliente'),
                ),
            ],
          ),
        Row(
          children: [
            Expanded(
              child: OutlinedButton(
                onPressed: _busy || result.page == 0
                    ? null
                    : () => _load(result.page - 1),
                child: const Text('Anterior'),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: OutlinedButton(
                onPressed: _busy || result.last
                    ? null
                    : () => _load(result.page + 1),
                child: const Text('Siguiente'),
              ),
            ),
          ],
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
      children: [
        Text(
          clientName(client),
          style: Theme.of(context).textTheme.headlineSmall,
        ),
        const SizedBox(height: 12),
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
              Text('Domicilio', style: Theme.of(context).textTheme.titleMedium),
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
              Text('Retenido: ${MoneyFormat.mxn(account['saldoRetenido'])}'),
              Text(
                account['activa'] == true ? 'Cuenta activa' : 'Cuenta inactiva',
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
        FilledButton.icon(
          onPressed: _busy ? null : _edit,
          icon: const Icon(Icons.edit_outlined),
          label: const Text('Editar información'),
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
