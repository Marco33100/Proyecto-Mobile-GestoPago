import 'package:flutter/material.dart';

import '../models/login_session.dart';
import '../services/auth_api_service.dart';
import '../utils/date_only.dart';
import '../validation/cliente_validators.dart';

/// Un solo alta: cliente, domicilio, cuenta y acceso se guardan en el backend.
class RegisterPage extends StatefulWidget {
  const RegisterPage(
      {super.key,
      required this.authApi,
      required this.session,
      this.onSessionInvalid});

  final AuthApiService authApi;
  final LoginSession session;
  final Future<void> Function()? onSessionInvalid;

  @override
  State<RegisterPage> createState() => _RegisterPageState();
}

class _RegisterPageState extends State<RegisterPage> {
  final _formKey = GlobalKey<FormState>();
  final Map<String, TextEditingController> _controllers = {};
  bool _loading = false;
  bool _obscurePassword = true;
  bool _datePickerOpen = false;
  String _sexo = 'FEMENINO';
  String _estadoCivil = 'SOLTERO';

  TextEditingController _controller(String key) =>
      _controllers.putIfAbsent(key, TextEditingController.new);
  String _text(String key) => _controller(key).text;
  String? _optional(String key) => _text(key).isEmpty ? null : _text(key);

  @override
  void dispose() {
    for (final controller in _controllers.values) {
      controller.dispose();
    }
    super.dispose();
  }

  void _normalizeInputs() {
    for (final entry in _controllers.entries) {
      if (entry.key == 'contrasena' || entry.key == 'confirmarContrasena') {
        continue;
      }
      var text = ClienteValidators.normalizeText(entry.value.text);
      if (entry.key == 'correo') text = text.toLowerCase();
      if (entry.key == 'curp' || entry.key == 'rfc') text = text.toUpperCase();
      entry.value.value = TextEditingValue(
          text: text, selection: TextSelection.collapsed(offset: text.length));
    }
  }

  Map<String, dynamic> _payload() => {
        for (final key in [
          'nombre',
          'apellidoPaterno',
          'apellidoMaterno',
          'fechaNacimiento',
          'curp',
          'rfc',
          'nacionalidad',
          'correo',
          'telefonoMovil',
          'ocupacion',
          'empresa',
          'ingresoMensual',
          'contrasena'
        ])
          key: _text(key),
        'segundoNombre': _optional('segundoNombre'),
        'telefonoAlternativo': _optional('telefonoAlternativo'),
        'sexo': _sexo,
        'estadoCivil': _estadoCivil,
        'domicilio': {
          for (final key in [
            'calle',
            'numeroExterior',
            'colonia',
            'municipio',
            'estado',
            'codigoPostal',
            'pais'
          ])
            key: _text(key),
          'numeroInterior': _optional('numeroInterior'),
        },
      };

  Future<void> _submit() async {
    if (_loading || !widget.session.isExecutive) return;
    _normalizeInputs();
    if (!_formKey.currentState!.validate()) return;
    setState(() => _loading = true);
    try {
      final message = await widget.authApi
          .registerClient(session: widget.session, payload: _payload());
      if (mounted) Navigator.of(context).pop(message);
    } on ApiException catch (error) {
      if (!mounted) return;
      _showError(error.message);
      if (error.statusCode == 401) {
        // Cerrar el formulario antes de borrar la sesion del ejecutivo.
        Navigator.of(context).pop();
        await widget.onSessionInvalid?.call();
      }
    } catch (_) {
      if (mounted) _showError('No fue posible completar el registro.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  void _showError(String message) => ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(SnackBar(content: Text(message)));

  Future<void> _selectBirthDate() async {
    if (_loading || _datePickerOpen) return;
    _datePickerOpen = true;
    try {
      final firstDate = DateTime(1, 1, 1);
      final lastDate = ClienteValidators.latestBirthDate();
      final current = DateOnly.tryParse(_text('fechaNacimiento'));
      final initialDate = current != null &&
              !current.isBefore(firstDate) &&
              !current.isAfter(lastDate)
          ? current
          : lastDate;
      final selected = await showDatePicker(
        context: context,
        initialDate: initialDate,
        firstDate: firstDate,
        lastDate: lastDate,
        initialDatePickerMode: DatePickerMode.year,
        initialEntryMode: DatePickerEntryMode.calendarOnly,
        helpText: 'Selecciona la fecha de nacimiento',
        cancelText: 'Cancelar',
        confirmText: 'Seleccionar',
      );
      if (!mounted || selected == null) return;
      final text = DateOnly.format(selected);
      _controller('fechaNacimiento').value = TextEditingValue(
        text: text,
        selection: TextSelection.collapsed(offset: text.length),
      );
    } finally {
      _datePickerOpen = false;
    }
  }

  Widget _birthDateField() => Padding(
        padding: const EdgeInsets.only(bottom: 16),
        child: TextFormField(
          key: const ValueKey('fechaNacimiento'),
          controller: _controller('fechaNacimiento'),
          enabled: !_loading,
          readOnly: true,
          enableInteractiveSelection: false,
          onTap: _selectBirthDate,
          decoration: InputDecoration(
            labelText: 'Fecha de nacimiento',
            hintText: 'AAAA-MM-DD',
            helperText: 'Elige en el calendario; se guarda sin hora.',
            border: const OutlineInputBorder(),
            suffixIcon: IconButton(
              tooltip: 'Elegir fecha de nacimiento',
              onPressed: _loading ? null : _selectBirthDate,
              icon: const Icon(Icons.calendar_today),
            ),
          ),
          validator: ClienteValidators.birthDate,
          autovalidateMode: AutovalidateMode.onUserInteraction,
        ),
      );

  Widget _field(String key, String label,
      {int max = 100,
      bool optional = false,
      String? Function(String?)? validator,
      TextInputType? keyboard,
      bool secret = false}) {
    return Padding(
        padding: const EdgeInsets.only(bottom: 16),
        child: TextFormField(
          key: ValueKey(key),
          controller: _controller(key),
          enabled: !_loading,
          obscureText: secret && _obscurePassword,
          keyboardType: keyboard,
          textInputAction: TextInputAction.next,
          autocorrect: !secret,
          enableSuggestions: !secret,
          decoration: InputDecoration(
              labelText: label,
              border: const OutlineInputBorder(),
              suffixIcon: key == 'contrasena'
                  ? IconButton(
                      onPressed: () =>
                          setState(() => _obscurePassword = !_obscurePassword),
                      icon: Icon(_obscurePassword
                          ? Icons.visibility
                          : Icons.visibility_off))
                  : null),
          validator: validator ??
              (value) => optional && (value ?? '').trim().isEmpty
                  ? null
                  : ClienteValidators.requiredText(value, max),
        ));
  }

  Widget _section(String title) => Padding(
      padding: const EdgeInsets.symmetric(vertical: 16),
      child: Text(title, style: Theme.of(context).textTheme.titleLarge));

  Widget _choice(String label, String value, List<String> values,
          ValueChanged<String> onChange) =>
      Padding(
          padding: const EdgeInsets.only(bottom: 16),
          child: DropdownButtonFormField<String>(
            initialValue: value,
            decoration: InputDecoration(
                labelText: label, border: const OutlineInputBorder()),
            items: values
                .map((v) => DropdownMenuItem(
                    value: v, child: Text(v.replaceAll('_', ' '))))
                .toList(),
            onChanged: _loading
                ? null
                : (v) {
                    if (v != null) setState(() => onChange(v));
                  },
          ));

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: const Text('Registro completo de cliente')),
        body: !widget.session.isExecutive
            ? const Center(
                child: Text('Solo un ejecutivo puede registrar clientes.'))
            : SafeArea(
                child: SingleChildScrollView(
                padding: const EdgeInsets.all(24),
                child: Center(
                    child: ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 560),
                  child: Form(
                      key: _formKey,
                      child: Column(
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: [
                            _section('Datos personales'),
                            _field('nombre', 'Nombre',
                                validator: ClienteValidators.name),
                            _field('segundoNombre', 'Segundo nombre (opcional)',
                                validator: (v) =>
                                    ClienteValidators.name(v, optional: true)),
                            _field('apellidoPaterno', 'Apellido paterno',
                                validator: ClienteValidators.name),
                            _field('apellidoMaterno', 'Apellido materno',
                                validator: ClienteValidators.name),
                            _birthDateField(),
                            _field('curp', 'CURP',
                                validator: ClienteValidators.curp),
                            _field('rfc', 'RFC',
                                validator: ClienteValidators.rfc),
                            _choice(
                                'Sexo',
                                _sexo,
                                ['FEMENINO', 'MASCULINO', 'NO_BINARIO'],
                                (v) => _sexo = v),
                            _field('nacionalidad', 'Nacionalidad', max: 60),
                            _choice(
                                'Estado civil',
                                _estadoCivil,
                                [
                                  'SOLTERO',
                                  'CASADO',
                                  'DIVORCIADO',
                                  'VIUDO',
                                  'UNION_LIBRE'
                                ],
                                (v) => _estadoCivil = v),
                            _section('Contacto'),
                            _field('correo', 'Correo / usuario de acceso',
                                keyboard: TextInputType.emailAddress,
                                validator: ClienteValidators.email),
                            _field('telefonoMovil', 'Telefono movil',
                                keyboard: TextInputType.phone,
                                validator: ClienteValidators.phone),
                            _field('telefonoAlternativo',
                                'Telefono alternativo (opcional)',
                                keyboard: TextInputType.phone,
                                validator: (v) =>
                                    ClienteValidators.phone(v, optional: true)),
                            _section('Domicilio'),
                            _field('calle', 'Calle'),
                            _field('numeroExterior', 'Numero exterior',
                                max: 20),
                            _field(
                                'numeroInterior', 'Numero interior (opcional)',
                                max: 20, optional: true),
                            _field('colonia', 'Colonia'),
                            _field('municipio', 'Municipio'),
                            _field('estado', 'Estado'),
                            _field('codigoPostal', 'Codigo postal',
                                keyboard: TextInputType.number,
                                validator: ClienteValidators.postalCode),
                            _field('pais', 'Pais', max: 60),
                            _section('Informacion laboral'),
                            _field('ocupacion', 'Ocupacion'),
                            _field('empresa', 'Empresa', max: 150),
                            _field('ingresoMensual',
                                'Ingreso mensual (ej. 15000.50)',
                                keyboard: const TextInputType.numberWithOptions(
                                    decimal: true),
                                validator: ClienteValidators.income),
                            _section('Acceso del cliente'),
                            _field('contrasena', 'Contrasena',
                                secret: true,
                                validator: ClienteValidators.password),
                            _field(
                                'confirmarContrasena', 'Confirmar contrasena',
                                secret: true,
                                validator: (v) => v != _text('contrasena')
                                    ? 'Las contrasenas no coinciden'
                                    : null),
                            const Text(
                                'El sistema asigna la cuenta y el saldo inicial. El correo sera el usuario de acceso.'),
                            const SizedBox(height: 24),
                            FilledButton(
                                onPressed: _loading ? null : _submit,
                                child: _loading
                                    ? const SizedBox.square(
                                        dimension: 22,
                                        child: CircularProgressIndicator(
                                            strokeWidth: 2))
                                    : const Text('Registrar cliente')),
                          ])),
                )),
              )),
      );
}
