import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../services/payment_api_service.dart';
import '../services/profile_api_service.dart';
import '../theme.dart';
import '../validation/cliente_validators.dart';
import '../validation/input_filters.dart';
import '../widgets/management_layout.dart';
import '../widgets/status_message.dart';

/// El cliente edita su contacto, domicilio, estado civil y empleo. Nombre,
/// fecha de nacimiento, CURP, RFC, sexo, nacionalidad y correo no se editan
/// aquí: el servidor los conserva aunque se enviaran.
class EditProfilePage extends StatefulWidget {
  const EditProfilePage({super.key, required this.api});

  final ProfileApiService api;

  @override
  State<EditProfilePage> createState() => _EditProfilePageState();
}

class _EditProfilePageState extends State<EditProfilePage> {
  static const _maritalStatuses = [
    'SOLTERO',
    'CASADO',
    'DIVORCIADO',
    'VIUDO',
    'UNION_LIBRE',
  ];

  final _form = GlobalKey<FormState>();
  final _controllers = <String, TextEditingController>{};
  String? _maritalStatus;
  ProfileCatalogs? _catalogs;
  Map<String, dynamic>? _data;
  bool _loading = true;
  bool _saving = false;
  String? _error;

  TextEditingController _c(String key) =>
      _controllers.putIfAbsent(key, TextEditingController.new);
  String _text(String key) => ClienteValidators.normalizeText(_c(key).text);

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    for (final controller in _controllers.values) {
      controller.dispose();
    }
    super.dispose();
  }

  Future<void> _load() async {
    try {
      final results = await Future.wait([
        widget.api.myData(),
        widget.api.catalogs(),
      ]);
      final data = results[0] as Map<String, dynamic>;
      if (!mounted) return;
      final address = data['domicilio'] is Map ? data['domicilio'] as Map : {};
      for (final key in ['telefonoMovil', 'telefonoAlternativo', 'ocupacion']) {
        _c(key).text = data[key]?.toString() ?? '';
      }
      _c('empresa').text = data['empresa']?.toString() ?? '';
      final income = data['ingresoMensual'];
      _c('ingresoMensual').text = income == null ? '' : income.toString();
      for (final key in [
        'calle',
        'numeroExterior',
        'numeroInterior',
        'colonia',
        'municipio',
        'estado',
        'codigoPostal',
        'pais',
      ]) {
        _c(key).text = address[key]?.toString() ?? '';
      }
      setState(() {
        _data = data;
        _catalogs = results[1] as ProfileCatalogs?;
        _maritalStatus = data['estadoCivil']?.toString();
      });
    } on PaymentFailure catch (error) {
      if (mounted) setState(() => _error = error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  String? _optional(String key) => _text(key).isEmpty ? null : _text(key);

  Future<void> _save() async {
    if (_saving || !_form.currentState!.validate()) return;
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      await widget.api.updateMyData({
        'telefonoMovil': _text('telefonoMovil'),
        'telefonoAlternativo': _optional('telefonoAlternativo'),
        'estadoCivil': _maritalStatus,
        'ocupacion': _text('ocupacion'),
        'empresa': _optional('empresa'),
        'ingresoMensual': _optional('ingresoMensual'),
        'domicilio': {
          for (final key in [
            'calle',
            'numeroExterior',
            'colonia',
            'municipio',
            'estado',
            'codigoPostal',
            'pais',
          ])
            key: _text(key),
          'numeroInterior': _optional('numeroInterior'),
        },
      });
      if (mounted) Navigator.of(context).pop(true);
    } on PaymentFailure catch (error) {
      if (mounted) setState(() => _error = error.message);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Widget _field(
    String key,
    String label, {
    int max = 100,
    bool optional = false,
    String? Function(String?)? validator,
    TextInputType? keyboard,
  }) => Padding(
    padding: const EdgeInsets.only(bottom: AppSpace.lg),
    child: TextFormField(
      key: ValueKey(key),
      controller: _c(key),
      enabled: !_saving,
      keyboardType: keyboard,
      textInputAction: TextInputAction.next,
      inputFormatters: [
        LengthLimitingTextInputFormatter(max),
        ...InputFilters.forField(key),
      ],
      decoration: InputDecoration(labelText: label),
      validator:
          validator ??
          (value) => optional && (value ?? '').trim().isEmpty
              ? null
              : ClienteValidators.requiredText(value, max),
    ),
  );

  /// Lista del catálogo; sin catálogo (sin red) queda como texto.
  Widget _catalog(String key, String label, List<String>? options) {
    if (options == null || options.isEmpty) return _field(key, label, max: 60);
    final current = _c(key).text;
    return Padding(
      padding: const EdgeInsets.only(bottom: AppSpace.lg),
      child: DropdownButtonFormField<String>(
        key: ValueKey(key),
        initialValue: options.contains(current) ? current : null,
        isExpanded: true,
        menuMaxHeight: 360,
        decoration: InputDecoration(labelText: label),
        items: [
          for (final option in options)
            DropdownMenuItem(
              value: option,
              child: Text(option, overflow: TextOverflow.ellipsis),
            ),
        ],
        validator: (value) => value == null ? 'Elige una opción' : null,
        onChanged: _saving ? null : (value) => _c(key).text = value ?? '',
      ),
    );
  }

  Widget _maritalField() => Padding(
    padding: const EdgeInsets.only(bottom: AppSpace.lg),
    child: DropdownButtonFormField<String>(
      key: const ValueKey('estadoCivil'),
      initialValue: _maritalStatuses.contains(_maritalStatus)
          ? _maritalStatus
          : null,
      decoration: const InputDecoration(labelText: 'Estado civil'),
      items: [
        for (final status in _maritalStatuses)
          DropdownMenuItem(
            value: status,
            child: Text(status.replaceAll('_', ' ')),
          ),
      ],
      validator: (value) => value == null ? 'Elige una opción' : null,
      onChanged: _saving
          ? null
          : (value) => setState(() => _maritalStatus = value),
    ),
  );

  /// Dos columnas si hay espacio; una en teléfono.
  Widget _section(String title, List<Widget> fields) => ManagementCard(
    children: [
      Text(title, style: Theme.of(context).textTheme.titleMedium),
      const SizedBox(height: AppSpace.lg),
      LayoutBuilder(
        builder: (context, constraints) {
          if (constraints.maxWidth < 560) return Column(children: fields);
          final width = (constraints.maxWidth - AppSpace.lg) / 2;
          return Wrap(
            spacing: AppSpace.lg,
            children: [
              for (final field in fields) SizedBox(width: width, child: field),
            ],
          );
        },
      ),
    ],
  );

  @override
  Widget build(BuildContext context) => ManagementLayout(
    title: 'Editar mis datos',
    busy: _loading || _saving,
    error: _error,
    children: [
      if (_data != null)
        Form(
          key: _form,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const StatusMessage(
                icon: Icons.info_outline_rounded,
                tone: StatusTone.neutral,
                message:
                    'Tu nombre, fecha de nacimiento, CURP, RFC, sexo, '
                    'nacionalidad y correo solo los puede cambiar un ejecutivo.',
              ),
              const SizedBox(height: AppSpace.lg),
              _section('Contacto', [
                _field(
                  'telefonoMovil',
                  'Teléfono móvil',
                  keyboard: TextInputType.phone,
                  validator: ClienteValidators.phone,
                ),
                _field(
                  'telefonoAlternativo',
                  'Teléfono alternativo (opcional)',
                  keyboard: TextInputType.phone,
                  validator: (value) =>
                      ClienteValidators.phone(value, optional: true),
                ),
                _maritalField(),
              ]),
              _section('Domicilio', [
                _field('calle', 'Calle'),
                _field('colonia', 'Colonia'),
                _field('numeroExterior', 'Número exterior', max: 10),
                _field(
                  'numeroInterior',
                  'Número interior (opcional)',
                  max: 10,
                  optional: true,
                ),
                _field('municipio', 'Municipio', max: 60),
                _field('estado', 'Estado', max: 40),
                _field(
                  'codigoPostal',
                  'Código postal',
                  keyboard: TextInputType.number,
                  validator: ClienteValidators.postalCode,
                ),
                _catalog('pais', 'País', _catalogs?.countries),
              ]),
              _section('Trabajo', [
                _catalog('ocupacion', 'Ocupación', _catalogs?.occupations),
                _field(
                  'empresa',
                  'Empresa (opcional)',
                  max: 150,
                  optional: true,
                ),
                _field(
                  'ingresoMensual',
                  'Ingreso mensual',
                  keyboard: const TextInputType.numberWithOptions(
                    decimal: true,
                  ),
                  validator: (value) => ClienteValidators.incomeFor(
                    value,
                    occupation: _text('ocupacion'),
                    company: _text('empresa'),
                  ),
                ),
              ]),
              const SizedBox(height: AppSpace.sm),
              Align(
                alignment: Alignment.centerRight,
                child: FilledButton.icon(
                  // Ancho propio: no ocupa toda la fila en computadora.
                  style: FilledButton.styleFrom(
                    minimumSize: const Size(220, 52),
                  ),
                  onPressed: _saving ? null : _save,
                  icon: const Icon(Icons.save_outlined),
                  label: Text(_saving ? 'Guardando' : 'Guardar cambios'),
                ),
              ),
            ],
          ),
        ),
    ],
  );
}
