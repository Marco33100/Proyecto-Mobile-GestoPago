import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../models/login_session.dart';
import '../services/auth_api_service.dart';
import '../services/admin_api_service.dart';
import '../services/payment_api_service.dart';
import '../theme.dart';
import '../utils/date_only.dart';
import '../validation/cliente_validators.dart';
import '../validation/input_filters.dart';
import '../widgets/paper_background.dart';
import '../widgets/responsive.dart';
import '../widgets/status_message.dart';

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

  /// Null en el registro público desde el login.
  final LoginSession? session;
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
  bool get _selfSignup => widget.session == null;
  bool get _allowed => widget.session?.isExecutive ?? true;
  RegistrationCatalogs? _catalogs;

  @override
  void initState() {
    super.initState();
    _loadCatalogs();
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

  Future<void> _loadCatalogs() async {
    final catalogs = await widget.authApi.registrationCatalogs();
    if (!mounted || catalogs == null) return;
    // Valores por omision para un registro nuevo en México.
    if (_text('pais').isEmpty) _controller('pais').text = 'México';
    if (_text('nacionalidad').isEmpty) {
      _controller('nacionalidad').text = 'Mexicana';
    }
    setState(() => _catalogs = catalogs);
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
      'contrasena',
    ])
      key: _text(key),
    'segundoNombre': _optional('segundoNombre'),
    'empresa': _optional('empresa'),
    'ingresoMensual': _optional('ingresoMensual'),
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
    if (_loading || !_allowed) return;
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
    ..showSnackBar(
      SnackBar(
        behavior: SnackBarBehavior.floating,
        width: Breakpoints.isExpanded(context) ? 520 : null,
        backgroundColor: const Color(0xFF9C3F36),
        content: Text(message),
      ),
    );

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
    bool uppercase = false,
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
      inputFormatters: secret
          ? null
          : [
              LengthLimitingTextInputFormatter(max),
              ...InputFilters.forField(key),
              if (uppercase) const _UpperCaseFormatter(),
            ],
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
      decoration: InputDecoration(labelText: label),
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

  /// Lista desplegable si el catálogo cargó; si no, campo de texto.
  Widget _catalogField(
    String key,
    String label,
    List<String>? options, {
    int max = 100,
  }) {
    if (options == null || options.isEmpty) {
      return _field(key, label, max: max);
    }
    final current = _text(key);
    return Padding(
      padding: const EdgeInsets.only(bottom: 16),
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
        onChanged: _loading
            ? null
            : (value) => _controller(key).text = value ?? '',
      ),
    );
  }

  /// Grupos de campos relacionados separados por aire.
  Widget _groups(List<List<Widget>> groups) => Column(
    children: [
      for (var i = 0; i < groups.length; i++) ...[
        if (i > 0) const SizedBox(height: 12),
        _grid(groups[i]),
      ],
    ],
  );

  /// Dos columnas en pantallas anchas; una en celular.
  Widget _grid(List<Widget> fields) {
    if (!Breakpoints.isExpanded(context)) return Column(children: fields);
    return LayoutBuilder(
      builder: (context, constraints) {
        // Un teléfono en horizontal es "ancho" pero no cabe en dos columnas.
        if (constraints.maxWidth < 560) return Column(children: fields);
        final width = (constraints.maxWidth - 16) / 2;
        return Wrap(
          spacing: 16,
          children: [
            for (final field in fields) SizedBox(width: width, child: field),
          ],
        );
      },
    );
  }

  /// En computadora: campos más compactos y bordes suaves.
  ThemeData _formTheme(BuildContext context, bool wide) {
    final theme = Theme.of(context);
    if (!wide) return theme;
    const error = Color(0xFF9C3F36);
    final border = OutlineInputBorder(
      borderRadius: BorderRadius.circular(AppRadius.sm),
      borderSide: const BorderSide(color: outlineColor),
    );
    return theme.copyWith(
      inputDecorationTheme: theme.inputDecorationTheme.copyWith(
        contentPadding: const EdgeInsets.symmetric(
          horizontal: 14,
          vertical: 14,
        ),
        border: border,
        enabledBorder: border,
        focusedBorder: border.copyWith(
          borderSide: const BorderSide(color: ink, width: 1.6),
        ),
        errorBorder: border.copyWith(
          borderSide: const BorderSide(color: error),
        ),
        focusedErrorBorder: border.copyWith(
          borderSide: const BorderSide(color: error, width: 1.6),
        ),
      ),
    );
  }

  Widget _currentFields() => switch (_currentStep) {
    0 => _personalFields(),
    1 => _contactAndAddressFields(),
    _ => _workAndAccessFields(),
  };

  Widget _personalFields() => _groups([
    [
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
    ],
    [
      _field(
        'curp',
        'CURP',
        max: 18,
        uppercase: true,
        capitalization: TextCapitalization.characters,
        validator: ClienteValidators.curp,
      ),
      _field(
        'rfc',
        'RFC',
        max: 13,
        uppercase: true,
        capitalization: TextCapitalization.characters,
        validator: ClienteValidators.rfc,
      ),
    ],
    [
      _birthDateField(),
      _choice('Sexo', _sexo, const [
        'FEMENINO',
        'MASCULINO',
        'NO_BINARIO',
      ], (value) => _sexo = value),
      _catalogField(
        'nacionalidad',
        'Nacionalidad',
        _catalogs?.nationalities,
        max: 60,
      ),
      _choice('Estado civil', _estadoCivil, const [
        'SOLTERO',
        'CASADO',
        'DIVORCIADO',
        'VIUDO',
        'UNION_LIBRE',
      ], (value) => _estadoCivil = value),
    ],
  ]);

  Widget _contactAndAddressFields() => _groups([
    [
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
    ],
    [
      _field('calle', 'Calle'),
      _field('numeroExterior', 'Número exterior', max: 10),
      _field(
        'numeroInterior',
        'Número interior (opcional)',
        max: 10,
        optional: true,
      ),
      _field('colonia', 'Colonia'),
      _field('municipio', 'Municipio', max: 60),
      _field('estado', 'Estado', max: 40),
      _field(
        'codigoPostal',
        'Código postal',
        keyboard: TextInputType.number,
        capitalization: TextCapitalization.none,
        validator: ClienteValidators.postalCode,
      ),
      _catalogField('pais', 'País', _catalogs?.countries, max: 60),
    ],
  ]);

  Widget _workAndAccessFields() => Column(
    children: [
      _groups([
        [
          _catalogField('ocupacion', 'Ocupación', _catalogs?.occupations),
          _field('empresa', 'Empresa (opcional)', max: 150, optional: true),
          _field(
            'ingresoMensual',
            'Ingreso mensual',
            keyboard: const TextInputType.numberWithOptions(decimal: true),
            capitalization: TextCapitalization.none,
            validator: (value) => ClienteValidators.incomeFor(
              value,
              occupation: _text('ocupacion'),
              company: _text('empresa'),
            ),
          ),
        ],
        [
          if (!_editing)
            _field(
              'contrasena',
              _selfSignup ? 'Contraseña' : 'Contraseña para el cliente',
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
        ],
      ]),
      const SizedBox(height: 4),
      if (!_editing) _AccountCreationNote(selfSignup: _selfSignup),
      if (_editing)
        const Text(
          'La CURP, el RFC, el titular y el número de cuenta se conservan. Esta operación no cambia saldos.',
        ),
    ],
  );

  @override
  Widget build(BuildContext context) {
    final canPop = _currentStep == 0 || _loading;
    final wide = Breakpoints.isExpanded(context);
    final maxWidth = wide ? ContentWidth.form + 260 : ContentWidth.form;
    return PopScope<Object?>(
      canPop: canPop,
      onPopInvokedWithResult: (didPop, result) {
        if (!didPop && !_loading) _goBack();
      },
      child: Scaffold(
        appBar: AppBar(
          title: Text(
            _editing
                ? 'Editar cliente'
                : _selfSignup
                ? 'Crear cuenta'
                : 'Registrar cliente',
          ),
        ),
        body: !_allowed
            ? const Center(
                child: Text('Solo un ejecutivo puede registrar clientes.'),
              )
            : Theme(
                data: _formTheme(context, wide),
                child: PaperBackground(
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
                                  constraints: BoxConstraints(
                                    maxWidth: maxWidth,
                                  ),
                                  child: Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.stretch,
                                    children: [
                                      const SizedBox(height: 8),
                                      Text(
                                        _editing
                                            ? 'Actualizar información'
                                            : _selfSignup
                                            ? 'Crea tu cuenta'
                                            : 'Registro de cliente',
                                        style: Theme.of(
                                          context,
                                        ).textTheme.headlineMedium,
                                      ),
                                      const SizedBox(height: 8),
                                      Text(
                                        _editing
                                            ? 'Revisa los datos modificables del cliente.'
                                            : _selfSignup
                                            ? 'Completa tus datos para crear tu cuenta y tu acceso.'
                                            : 'Completa los datos para crear el acceso del cliente.',
                                        style: Theme.of(
                                          context,
                                        ).textTheme.bodyMedium,
                                      ),
                                      const SizedBox(height: 22),
                                      if (!wide)
                                        Row(
                                          children: [
                                            Expanded(
                                              child: ClipRRect(
                                                borderRadius:
                                                    BorderRadius.circular(4),
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
                                      if (wide)
                                        TwoPane(
                                          secondaryFirst: true,
                                          secondaryWidth: 220,
                                          primary: _currentFields(),
                                          secondary: _StepList(
                                            names: _stepNames,
                                            current: _currentStep,
                                          ),
                                        )
                                      else
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
              ),
        bottomNavigationBar: _allowed
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
                      constraints: BoxConstraints(maxWidth: maxWidth),
                      child: Row(
                        children: [
                          if (wide) const Spacer(),
                          if (_currentStep > 0) ...[
                            _slot(
                              wide,
                              160,
                              OutlinedButton.icon(
                                onPressed: _loading ? null : _goBack,
                                icon: const Icon(Icons.arrow_back_rounded),
                                label: const Text('Atrás'),
                              ),
                            ),
                            const SizedBox(width: 12),
                          ],
                          _slot(
                            wide,
                            220,
                            FilledButton.icon(
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
                                          : _selfSignup
                                          ? 'Crear cuenta'
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

/// Botón que llena el ancho en celular y tiene ancho fijo en computadora.
Widget _slot(bool wide, double width, Widget child) =>
    wide ? SizedBox(width: width, child: child) : Expanded(child: child);

class _UpperCaseFormatter extends TextInputFormatter {
  const _UpperCaseFormatter();

  @override
  TextEditingValue formatEditUpdate(
    TextEditingValue oldValue,
    TextEditingValue newValue,
  ) => newValue.copyWith(text: newValue.text.toUpperCase());
}

class _AccountCreationNote extends StatelessWidget {
  const _AccountCreationNote({required this.selfSignup});

  final bool selfSignup;

  @override
  Widget build(BuildContext context) => StatusMessage(
    icon: Icons.info_outline_rounded,
    message: selfSignup
        ? 'Al crear tu cuenta, el sistema crea también tu acceso. Tu correo será tu usuario.'
        : 'Al registrar al cliente, el sistema crea su cuenta y acceso. El correo será su usuario.',
  );
}

/// Lista de pasos del registro, visible al costado en pantallas anchas.
class _StepList extends StatelessWidget {
  const _StepList({required this.names, required this.current});

  final List<String> names;
  final int current;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 6),
      decoration: BoxDecoration(
        color: warmSurface,
        borderRadius: BorderRadius.circular(AppRadius.md),
        border: Border.all(color: outlineColor),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          for (var index = 0; index < names.length; index++)
            Semantics(
              selected: index == current,
              label:
                  'Paso ${index + 1} de ${names.length}: ${names[index]}'
                  '${index < current ? ', completado' : ''}',
              child: ExcludeSemantics(
                child: Container(
                  margin: const EdgeInsets.symmetric(vertical: 2),
                  padding: const EdgeInsets.symmetric(
                    horizontal: 12,
                    vertical: 12,
                  ),
                  decoration: BoxDecoration(
                    color: index == current ? sage : null,
                    borderRadius: BorderRadius.circular(AppRadius.sm),
                  ),
                  child: Row(
                    children: [
                      CircleAvatar(
                        radius: 13,
                        backgroundColor: index <= current ? ink : sage,
                        foregroundColor: index <= current
                            ? warmSurface
                            : mutedInk,
                        child: index < current
                            ? const Icon(Icons.check_rounded, size: 16)
                            : Text(
                                '${index + 1}',
                                style: const TextStyle(
                                  fontSize: 13,
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Text(
                          names[index],
                          style: index == current
                              ? textTheme.titleMedium
                              : textTheme.bodyMedium,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}
