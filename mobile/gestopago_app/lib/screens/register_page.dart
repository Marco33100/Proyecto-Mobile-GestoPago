import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../models/login_session.dart';
import '../services/auth_api_service.dart';
import '../services/admin_api_service.dart';
import '../services/payment_api_service.dart';
import '../theme.dart';
import '../utils/date_only.dart';
import '../validation/cliente_validators.dart';
import '../widgets/brand_wordmark.dart';
import '../widgets/paper_background.dart';

/// The backend creates the client, address, account, and login in one request.
class RegisterPage extends StatefulWidget {
  const RegisterPage({
    super.key,
    required this.authApi,
    required this.session,
    this.onSessionInvalid,
    this.initialClient,
    this.adminApi,
  });

  final AuthApiService authApi;
  final LoginSession session;
  final Future<void> Function()? onSessionInvalid;
  final Map<String, dynamic>? initialClient;
  final AdminApiService? adminApi;

  @override
  State<RegisterPage> createState() => _RegisterPageState();
}

class _RegisterPageState extends State<RegisterPage> {
  static const _stepNames = [
    'Datos personales',
    'Contacto y domicilio',
    'Trabajo y acceso',
  ];

  final _stepFormKeys = List.generate(3, (_) => GlobalKey<FormState>());
  final _controllers = <String, TextEditingController>{};
  final _scrollController = ScrollController();
  int _currentStep = 0;
  bool _loading = false;
  bool _obscurePassword = true;
  bool _datePickerOpen = false;
  String _sexo = 'FEMENINO';
  String _estadoCivil = 'SOLTERO';
  bool get _editing => widget.initialClient != null;

  @override
  void initState() {
    super.initState();
    final client = widget.initialClient;
    if (client == null) return;
    for (final entry in client.entries) {
      if (entry.value != null && entry.value is! Map && entry.value is! List) {
        _controller(entry.key).text = entry.value.toString();
      }
    }
    final address = client['domicilio'];
    if (address is Map) {
      for (final entry in address.entries) {
        _controller(entry.key.toString()).text = entry.value?.toString() ?? '';
      }
    }
    _sexo = client['sexo'] as String? ?? 'FEMENINO';
    _estadoCivil = client['estadoCivil'] as String? ?? 'SOLTERO';
  }

  TextEditingController _controller(String key) =>
      _controllers.putIfAbsent(key, TextEditingController.new);
  String _text(String key) => _controller(key).text;
  String? _optional(String key) => _text(key).isEmpty ? null : _text(key);

  @override
  void dispose() {
    for (final controller in _controllers.values) {
      controller.dispose();
    }
    _scrollController.dispose();
    super.dispose();
  }

  void _normalizeInputs() {
    for (final entry in _controllers.entries) {
      if (entry.key == 'contrasena' || entry.key == 'confirmarContrasena') {
        continue;
      }
      var value = ClienteValidators.normalizeText(entry.value.text);
      if (entry.key == 'correo') value = value.toLowerCase();
      if (entry.key == 'curp' || entry.key == 'rfc') {
        value = value.toUpperCase();
      }
      entry.value.value = TextEditingValue(
        text: value,
        selection: TextSelection.collapsed(offset: value.length),
      );
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
      'contrasena',
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
        'pais',
      ])
        key: _text(key),
      'numeroInterior': _optional('numeroInterior'),
    },
  };

  void _continue() {
    if (_loading) return;
    _normalizeInputs();
    if (!_stepFormKeys[_currentStep].currentState!.validate()) return;
    setState(() => _currentStep++);
    _scrollToTop();
  }

  void _goBack() {
    if (_loading || _currentStep == 0) return;
    setState(() => _currentStep--);
    _scrollToTop();
  }

  void _scrollToTop() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted && _scrollController.hasClients) {
        _scrollController.animateTo(
          0,
          duration: const Duration(milliseconds: 180),
          curve: Curves.easeOutCubic,
        );
      }
    });
  }

  Future<void> _submit() async {
    if (_loading || !widget.session.isExecutive) return;
    _normalizeInputs();
    if (!_stepFormKeys[_currentStep].currentState!.validate()) return;
    setState(() => _loading = true);
    try {
      final payload = _payload();
      String message;
      if (_editing) {
        payload.remove('curp');
        payload.remove('rfc');
        payload.remove('contrasena');
        final facial = widget.initialClient!['referenciaReconocimientoFacial'];
        if (facial != null) payload['referenciaReconocimientoFacial'] = facial;
        await widget.adminApi!.updateClient(
          widget.initialClient!['id'] as int,
          payload,
        );
        message = 'Cliente actualizado correctamente.';
      } else {
        message = await widget.authApi.registerClient(
          session: widget.session,
          payload: payload,
        );
      }
      if (mounted) Navigator.of(context).pop(message);
    } on PaymentFailure catch (error) {
      if (mounted) _showError(error.message);
    } on ApiException catch (error) {
      if (!mounted) return;
      _showError(error.message);
      if (error.statusCode == 401) {
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
      final initialDate =
          current != null &&
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
      final value = DateOnly.format(selected);
      _controller('fechaNacimiento').value = TextEditingValue(
        text: value,
        selection: TextSelection.collapsed(offset: value.length),
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
      decoration: const InputDecoration(
        labelText: 'Fecha de nacimiento',
        hintText: 'AAAA-MM-DD',
        helperText: 'Elige la fecha en el calendario.',
        prefixIcon: Icon(Icons.calendar_month_outlined),
      ),
      validator: ClienteValidators.birthDate,
      autovalidateMode: AutovalidateMode.onUserInteraction,
    ),
  );

  Widget _field(
    String key,
    String label, {
    int max = 100,
    bool optional = false,
    String? Function(String?)? validator,
    TextInputType? keyboard,
    bool secret = false,
    TextCapitalization capitalization = TextCapitalization.words,
  }) => Padding(
    padding: const EdgeInsets.only(bottom: 16),
    child: TextFormField(
      key: ValueKey(key),
      controller: _controller(key),
      enabled: !_loading && !(_editing && (key == 'curp' || key == 'rfc')),
      obscureText: secret && _obscurePassword,
      keyboardType: keyboard,
      textCapitalization: secret ? TextCapitalization.none : capitalization,
      textInputAction: TextInputAction.next,
      autocorrect: !secret,
      enableSuggestions: !secret,
      inputFormatters: secret ? null : [LengthLimitingTextInputFormatter(max)],
      decoration: InputDecoration(
        labelText: label,
        suffixIcon: key == 'contrasena' || key == 'confirmarContrasena'
            ? IconButton(
                tooltip: _obscurePassword
                    ? 'Mostrar contraseña'
                    : 'Ocultar contraseña',
                onPressed: _loading
                    ? null
                    : () =>
                          setState(() => _obscurePassword = !_obscurePassword),
                icon: Icon(
                  _obscurePassword
                      ? Icons.visibility_outlined
                      : Icons.visibility_off_outlined,
                ),
              )
            : null,
      ),
      validator:
          validator ??
          (value) => optional && (value ?? '').trim().isEmpty
              ? null
              : ClienteValidators.requiredText(value, max),
    ),
  );

  Widget _choice(
    String label,
    String value,
    List<String> values,
    ValueChanged<String> onChange,
  ) => Padding(
    padding: const EdgeInsets.only(bottom: 16),
    child: DropdownButtonFormField<String>(
      initialValue: value,
      decoration: InputDecoration(
        labelText: label,
        prefixIcon: const Icon(Icons.expand_more_rounded),
      ),
      items: values
          .map(
            (option) => DropdownMenuItem(
              value: option,
              child: Text(option.replaceAll('_', ' ')),
            ),
          )
          .toList(),
      onChanged: _loading
          ? null
          : (selected) {
              if (selected != null) setState(() => onChange(selected));
            },
    ),
  );

  Widget _currentFields() => switch (_currentStep) {
    0 => _personalFields(),
    1 => _contactAndAddressFields(),
    _ => _workAndAccessFields(),
  };

  Widget _personalFields() => Column(
    children: [
      _field('nombre', 'Nombre', validator: ClienteValidators.name),
      _field(
        'segundoNombre',
        'Segundo nombre (opcional)',
        validator: (value) => ClienteValidators.name(value, optional: true),
      ),
      _field(
        'apellidoPaterno',
        'Apellido paterno',
        validator: ClienteValidators.name,
      ),
      _field(
        'apellidoMaterno',
        'Apellido materno',
        validator: ClienteValidators.name,
      ),
      _birthDateField(),
      _field(
        'curp',
        'CURP',
        capitalization: TextCapitalization.characters,
        validator: ClienteValidators.curp,
      ),
      _field(
        'rfc',
        'RFC',
        capitalization: TextCapitalization.characters,
        validator: ClienteValidators.rfc,
      ),
      _choice('Sexo', _sexo, const [
        'FEMENINO',
        'MASCULINO',
        'NO_BINARIO',
      ], (value) => _sexo = value),
      _field('nacionalidad', 'Nacionalidad'),
      _choice('Estado civil', _estadoCivil, const [
        'SOLTERO',
        'CASADO',
        'DIVORCIADO',
        'VIUDO',
        'UNION_LIBRE',
      ], (value) => _estadoCivil = value),
    ],
  );

  Widget _contactAndAddressFields() => Column(
    children: [
      _field(
        'correo',
        'Correo / usuario de acceso',
        keyboard: TextInputType.emailAddress,
        capitalization: TextCapitalization.none,
        validator: ClienteValidators.email,
      ),
      _field(
        'telefonoMovil',
        'Teléfono móvil',
        keyboard: TextInputType.phone,
        capitalization: TextCapitalization.none,
        validator: ClienteValidators.phone,
      ),
      _field(
        'telefonoAlternativo',
        'Teléfono alternativo (opcional)',
        keyboard: TextInputType.phone,
        capitalization: TextCapitalization.none,
        validator: (value) => ClienteValidators.phone(value, optional: true),
      ),
      _field('calle', 'Calle'),
      _field('numeroExterior', 'Número exterior', max: 20),
      _field(
        'numeroInterior',
        'Número interior (opcional)',
        max: 20,
        optional: true,
      ),
      _field('colonia', 'Colonia'),
      _field('municipio', 'Municipio'),
      _field('estado', 'Estado'),
      _field(
        'codigoPostal',
        'Código postal',
        keyboard: TextInputType.number,
        capitalization: TextCapitalization.none,
        validator: ClienteValidators.postalCode,
      ),
      _field('pais', 'País', max: 60),
    ],
  );

  Widget _workAndAccessFields() => Column(
    children: [
      _field('ocupacion', 'Ocupación'),
      _field('empresa', 'Empresa', max: 150),
      _field(
        'ingresoMensual',
        'Ingreso mensual',
        keyboard: const TextInputType.numberWithOptions(decimal: true),
        capitalization: TextCapitalization.none,
        validator: ClienteValidators.income,
      ),
      if (!_editing)
        _field(
          'contrasena',
          'Contraseña para el cliente',
          secret: true,
          validator: ClienteValidators.password,
        ),
      if (!_editing)
        _field(
          'confirmarContrasena',
          'Confirmar contraseña',
          secret: true,
          validator: (value) => value != _text('contrasena')
              ? 'Las contraseñas no coinciden'
              : null,
        ),
      const SizedBox(height: 4),
      if (!_editing) const _AccountCreationNote(),
      if (_editing)
        const Text(
          'La CURP, el RFC, el titular y el número de cuenta se conservan. Esta operación no cambia saldos.',
        ),
    ],
  );

  @override
  Widget build(BuildContext context) {
    final canPop = _currentStep == 0 || _loading;
    return PopScope<Object?>(
      canPop: canPop,
      onPopInvokedWithResult: (didPop, result) {
        if (!didPop && !_loading) _goBack();
      },
      child: Scaffold(
        appBar: AppBar(
          title: Text(_editing ? 'Editar cliente' : 'Registrar cliente'),
        ),
        body: !widget.session.isExecutive
            ? const Center(
                child: Text('Solo un ejecutivo puede registrar clientes.'),
              )
            : PaperBackground(
                child: SafeArea(
                  child: Form(
                    key: _stepFormKeys[_currentStep],
                    child: CustomScrollView(
                      controller: _scrollController,
                      slivers: [
                        SliverPadding(
                          padding: const EdgeInsets.fromLTRB(24, 18, 24, 24),
                          sliver: SliverToBoxAdapter(
                            child: Center(
                              child: ConstrainedBox(
                                constraints: const BoxConstraints(
                                  maxWidth: 560,
                                ),
                                child: Column(
                                  crossAxisAlignment:
                                      CrossAxisAlignment.stretch,
                                  children: [
                                    const BrandWordmark(compact: true),
                                    const SizedBox(height: 26),
                                    Text(
                                      _editing
                                          ? 'Actualizar información'
                                          : 'Registro de cliente',
                                      style: Theme.of(
                                        context,
                                      ).textTheme.headlineMedium,
                                    ),
                                    const SizedBox(height: 8),
                                    Text(
                                      _editing
                                          ? 'Revisa los datos modificables del cliente.'
                                          : 'Completa los datos para crear el acceso del cliente.',
                                      style: Theme.of(
                                        context,
                                      ).textTheme.bodyMedium,
                                    ),
                                    const SizedBox(height: 22),
                                    Row(
                                      children: [
                                        Expanded(
                                          child: ClipRRect(
                                            borderRadius: BorderRadius.circular(
                                              4,
                                            ),
                                            child: LinearProgressIndicator(
                                              value:
                                                  (_currentStep + 1) /
                                                  _stepNames.length,
                                              minHeight: 5,
                                            ),
                                          ),
                                        ),
                                        const SizedBox(width: 12),
                                        Text(
                                          '${_currentStep + 1} de ${_stepNames.length}',
                                          style: Theme.of(
                                            context,
                                          ).textTheme.labelLarge,
                                        ),
                                      ],
                                    ),
                                    const SizedBox(height: 17),
                                    Text(
                                      _stepNames[_currentStep],
                                      style: Theme.of(
                                        context,
                                      ).textTheme.titleLarge,
                                    ),
                                    const SizedBox(height: 18),
                                    _currentFields(),
                                  ],
                                ),
                              ),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
        bottomNavigationBar: widget.session.isExecutive
            ? SafeArea(
                top: false,
                child: Container(
                  padding: const EdgeInsets.fromLTRB(20, 10, 20, 12),
                  decoration: const BoxDecoration(
                    color: warmSurface,
                    border: Border(top: BorderSide(color: outlineColor)),
                  ),
                  child: Center(
                    heightFactor: 1,
                    child: ConstrainedBox(
                      constraints: const BoxConstraints(maxWidth: 560),
                      child: Row(
                        children: [
                          if (_currentStep > 0) ...[
                            Expanded(
                              child: OutlinedButton.icon(
                                onPressed: _loading ? null : _goBack,
                                icon: const Icon(Icons.arrow_back_rounded),
                                label: const Text('Atrás'),
                              ),
                            ),
                            const SizedBox(width: 12),
                          ],
                          Expanded(
                            child: FilledButton.icon(
                              onPressed: _loading
                                  ? null
                                  : _currentStep == _stepNames.length - 1
                                  ? _submit
                                  : _continue,
                              icon: _loading
                                  ? const SizedBox.square(
                                      dimension: 18,
                                      child: CircularProgressIndicator(
                                        strokeWidth: 2,
                                        color: warmSurface,
                                      ),
                                    )
                                  : Icon(
                                      _currentStep == _stepNames.length - 1
                                          ? Icons.person_add_alt_1_rounded
                                          : Icons.arrow_forward_rounded,
                                    ),
                              label: Text(
                                _loading
                                    ? 'Guardando'
                                    : _currentStep == _stepNames.length - 1
                                    ? (_editing
                                          ? 'Guardar cambios'
                                          : 'Registrar cliente')
                                    : 'Continuar',
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
              )
            : null,
      ),
    );
  }
}

class _AccountCreationNote extends StatelessWidget {
  const _AccountCreationNote();

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(15),
    decoration: BoxDecoration(
      color: sage,
      borderRadius: BorderRadius.circular(16),
    ),
    child: const Text(
      'Al registrar al cliente, el sistema crea su cuenta y acceso. El correo será su usuario.',
      style: TextStyle(height: 1.4, color: ink),
    ),
  );
}
