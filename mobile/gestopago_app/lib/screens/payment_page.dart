import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:http/http.dart' as http;
import 'package:qr_flutter/qr_flutter.dart';
import '../models/login_session.dart';
import '../services/payment_api_service.dart';

class PaymentPage extends StatefulWidget {
  const PaymentPage({
    super.key,
    required this.session,
    this.api,
    this.pendingStore,
  });
  final LoginSession session;
  final PaymentApiService? api;
  final PendingPaymentStore? pendingStore;
  @override
  State<PaymentPage> createState() => _PaymentPageState();
}

class _PaymentPageState extends State<PaymentPage> {
  final client = http.Client();
  late final api = widget.api ?? PaymentApiService(client, widget.session);
  late final pending = widget.pendingStore ?? const PendingPaymentStore();
  final password = TextEditingController();
  final otp = TextEditingController();
  final currentOtp = TextEditingController();
  final recovery = TextEditingController();
  Map<String, dynamic>? setup;
  List<String> recoveryCodes = [];
  bool changingFactor = false;
  final amount = TextEditingController();
  final reference = TextEditingController();
  final phone = TextEditingController();
  final form = GlobalKey<FormState>();
  final scroll = ScrollController();
  List<Map<String, dynamic>> accounts = [], products = [];
  Map<String, dynamic>? product, account, payment;
  String? pendingId, error;
  bool busy = false, enrolled = false, revealPin = false;
  bool get terminal =>
      ['APLICADO', 'RECHAZADO', 'EXPIRADO'].contains(payment?['estado']);
  bool get gift => [10, 11].contains(product?['idCatTipoServicio']);
  @override
  void initState() {
    super.initState();
    run(load);
  }

  @override
  void dispose() {
    client.close();
    password.dispose();
    otp.dispose();
    currentOtp.dispose();
    recovery.dispose();
    amount.dispose();
    reference.dispose();
    phone.dispose();
    scroll.dispose();
    super.dispose();
  }

  Future<void> run(Future<void> Function() action) async {
    if (busy) return;
    setState(() {
      busy = true;
      error = null;
    });
    try {
      await action();
    } on PaymentFailure catch (e) {
      error = e.message;
    } catch (_) {
      error =
          'No se pudo completar la operacion. Si hay un pago pendiente, consulta su estado.';
    } finally {
      if (mounted) {
        setState(() {
          busy = false;
        });
      }
    }
  }

  Future<void> load() async {
    pendingId = await pending.read(widget.session.userId);
    if (pendingId != null) {
      await consult();
      return;
    }
    final factor = await api.request('/api/pagos/segundo-factor/estado', {});
    enrolled = factor['activo'] == true;
    final a = await api.request('/api/cuentas/mias/consulta', {
      'pagina': 0,
      'tamanio': 100,
    });
    accounts = (a['contenido'] as List? ?? [])
        .whereType<Map<String, dynamic>>()
        .where((a) => a['activa'] == true)
        .toList();
    final p = await api.request('/api/products');
    products = ((p['productos'] as Map?)?['productos'] as List? ?? [])
        .whereType<Map<String, dynamic>>()
        .where((p) => [1, 2, 4].contains(p['tipoFront']))
        .toList();
  }

  Future<void> enroll() async {
    if (password.text.isEmpty) {
      throw const PaymentFailure(
        'Escribe tu contrasena actual para configurar el segundo factor.',
      );
    }
    if (enrolled &&
        currentOtp.text.trim().isEmpty &&
        recovery.text.trim().isEmpty) {
      throw const PaymentFailure(
        'Confirma tu codigo actual o utiliza un codigo de recuperacion.',
      );
    }
    if (currentOtp.text.trim().isNotEmpty && recovery.text.trim().isNotEmpty) {
      throw const PaymentFailure(
        'Utiliza solo un codigo actual o uno de recuperacion.',
      );
    }
    try {
      setup = await api.request('/api/pagos/segundo-factor/preparar', {
        'contrasena': password.text,
        if (currentOtp.text.trim().isNotEmpty)
          'codigoActual': currentOtp.text.trim(),
        if (recovery.text.trim().isNotEmpty)
          'codigoRecuperacion': recovery.text.trim().toUpperCase(),
      });
    } finally {
      password.clear();
      currentOtp.clear();
      recovery.clear();
    }
  }

  Future<void> activateFactor() async {
    if (!RegExp(r'^[0-9]{6}$').hasMatch(otp.text.trim())) {
      throw const PaymentFailure('Escribe los seis digitos del autenticador.');
    }
    try {
      final result = await api.request('/api/pagos/segundo-factor/activar', {
        'codigo': otp.text.trim(),
      });
      recoveryCodes = (result['codigosRecuperacion'] as List? ?? [])
          .cast<String>();
      enrolled = result['activo'] == true;
      changingFactor = false;
      setup = null;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted && scroll.hasClients) scroll.jumpTo(0);
      });
    } finally {
      otp.clear();
    }
  }

  Future<void> prepare() async {
    if (!form.currentState!.validate()) return;
    pendingId = PendingPaymentStore.newId();
    await pending.write(widget.session.userId, pendingId!);
    payment = await api.request('/api/pagos/preparar', {
      'solicitudId': pendingId,
      'numeroCuenta': account!['numeroCuenta'],
      'idProducto': product!['idProducto'],
      'idServicio': product!['idServicio'],
      'tipoFront': product!['tipoFront'],
      'monto': amount.text.trim(),
      if (product!['tipoFront'] == 1 && !gift) 'telefono': phone.text.trim(),
      if (product!['tipoFront'] != 1) 'referencia': reference.text.trim(),
    });
  }

  Future<void> consult() async {
    if (pendingId == null) return;
    try {
      payment = await api.request('/api/pagos/consulta', {'pagoId': pendingId});
    } on PaymentFailure catch (e) {
      if (e.status != 404) rethrow;
      // A missing prepared order cannot charge: authorization requires an existing owned order.
      await pending.clear(widget.session.userId);
      pendingId = null;
      throw const PaymentFailure(
        'La solicitud no llego a prepararse. Puedes volver a cargar el formulario.',
      );
    }
  }

  Future<void> authorize() async {
    if (!RegExp(r'^[0-9]{6}$').hasMatch(otp.text.trim())) {
      throw const PaymentFailure('Escribe los seis digitos del autenticador.');
    }
    try {
      payment = await api.request('/api/pagos/autorizar', {
        'pagoId': pendingId,
        'codigo': otp.text.trim(),
      });
    } finally {
      otp.clear();
    }
  }

  Future<void> cancel() async {
    payment = await api.request('/api/pagos/cancelar', {'pagoId': pendingId});
  }

  Future<void> finish() async {
    if (!terminal) return;
    await pending.clear(widget.session.userId);
    pendingId = null;
    payment = null;
    product = null;
    account = null;
    revealPin = false;
    await load();
  }

  Widget codeField() => TextField(
    controller: otp,
    enabled: !busy,
    keyboardType: TextInputType.number,
    inputFormatters: [
      FilteringTextInputFormatter.digitsOnly,
      LengthLimitingTextInputFormatter(6),
    ],
    enableSuggestions: false,
    autocorrect: false,
    autofillHints: const [AutofillHints.oneTimeCode],
    decoration: decoration('Codigo del autenticador (6 digitos)'),
  );

  InputDecoration decoration(String label) =>
      InputDecoration(labelText: label, border: const OutlineInputBorder());
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Pagar un servicio')),
    body: SafeArea(
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 600),
          child: ListView(
            controller: scroll,
            padding: const EdgeInsets.all(24),
            children: [
              Text(
                'Servicios, recargas y tarjetas',
                style: Theme.of(context).textTheme.headlineSmall,
              ),
              const SizedBox(height: 12),
              const Text(
                'Sin comision adicional. Revisa el concepto y el importe antes de autorizar.',
              ),
              const SizedBox(height: 24),
              if (busy)
                const LinearProgressIndicator(
                  semanticsLabel: 'Procesando solicitud',
                ),
              if (error != null) ...[
                const SizedBox(height: 16),
                Text(
                  error!,
                  style: TextStyle(color: Theme.of(context).colorScheme.error),
                ),
                TextButton(
                  onPressed: busy
                      ? null
                      : () => run(pendingId == null ? load : consult),
                  child: Text(
                    pendingId == null ? 'Volver a cargar' : 'Consultar estado',
                  ),
                ),
              ],
              if (pendingId != null) ...[
                const SizedBox(height: 24),
                Text(
                  payment?['estado']?.toString() ?? 'Respuesta pendiente',
                  style: Theme.of(context).textTheme.titleLarge,
                ),
                if (payment != null) ...[
                  const SizedBox(height: 16),
                  Text(
                    '${payment!['concepto']}\nImporte: ${payment!['monto']} MXN\nCuenta: ${payment!['numeroCuenta']}\nUPC: ${payment!['upc']}',
                  ),
                  if (payment!['telefono'] != null)
                    SelectableText('Telefono: ${payment!['telefono']}'),
                  if (payment!['referencia'] != null)
                    SelectableText('Referencia: ${payment!['referencia']}'),
                  if (payment!['saldoReferencia'] != null) ...[
                    Text(
                      'Saldo informado del servicio: ${payment!['saldoReferencia']} MXN',
                    ),
                    const Text(
                      'Un saldo positivo indica deuda; cero indica sin deuda y negativo indica saldo a favor.',
                    ),
                    if (payment!['consultaReferenciaEn'] != null)
                      Text('Consulta: ${payment!['consultaReferenciaEn']}'),
                    Text(
                      'Regla de importe: ${payment!['reglaImporteReferencia'] ?? 'SIN_DEFINIR'}',
                    ),
                  ],
                  if (payment!['autorizacion'] != null)
                    SelectableText('Autorizacion: ${payment!['autorizacion']}'),
                  if (payment!['aviso'] != null)
                    Text(payment!['aviso'] as String),
                  if (payment!['pin'] != null)
                    TextButton(
                      onPressed: () => setState(() => revealPin = !revealPin),
                      child: Text(
                        revealPin
                            ? 'PIN: ${payment!['pin']}'
                            : 'Mostrar PIN de la tarjeta',
                      ),
                    ),
                  if (payment!['leyenda'] != null)
                    SelectableText(payment!['leyenda'] as String),
                ],
                const SizedBox(height: 24),
                if (payment?['estado'] == 'PREPARADO') ...[
                  codeField(),
                  const SizedBox(height: 12),
                  FilledButton.icon(
                    onPressed:
                        busy || payment?['autorizacionDisponible'] == false
                        ? null
                        : () => run(authorize),
                    icon: const Icon(Icons.password),
                    label: const Text('Autorizar este pago'),
                  ),
                  TextButton(
                    onPressed: busy ? null : () => run(cancel),
                    child: const Text('Cancelar antes de enviar'),
                  ),
                ],
                if (!terminal) ...[
                  const Text(
                    'No generes otro pago mientras no se confirme el resultado. Puedes salir y consultar al regresar.',
                  ),
                  OutlinedButton(
                    onPressed: busy ? null : () => run(consult),
                    child: const Text('Consultar estado'),
                  ),
                ] else
                  FilledButton(
                    onPressed: busy ? null : () => run(finish),
                    child: const Text('Volver a mis servicios'),
                  ),
              ] else ...[
                if (recoveryCodes.isNotEmpty) ...[
                  const SizedBox(height: 20),
                  Text(
                    'Guarda tus codigos de recuperacion',
                    style: Theme.of(context).textTheme.titleLarge,
                  ),
                  const Text(
                    'Se muestran una sola vez. Cada codigo permite volver a configurar el autenticador; no autoriza pagos.',
                  ),
                  SelectableText(recoveryCodes.join('\n')),
                  TextButton(
                    onPressed: () async {
                      await Clipboard.setData(
                        ClipboardData(text: recoveryCodes.join('\n')),
                      );
                    },
                    child: const Text('Copiar codigos de recuperacion'),
                  ),
                  FilledButton(
                    onPressed: () => setState(() => recoveryCodes = []),
                    child: const Text('Ya guarde los codigos'),
                  ),
                  const Text(
                    'Para pagar, espera al siguiente codigo del autenticador. El usado para activar ya se consumio.',
                  ),
                ],
                if (enrolled &&
                    !changingFactor &&
                    setup == null &&
                    recoveryCodes.isEmpty)
                  TextButton(
                    onPressed: busy
                        ? null
                        : () => setState(() => changingFactor = true),
                    child: const Text(
                      'Cambiar autenticador o recuperar acceso',
                    ),
                  ),
                if ((!enrolled || changingFactor) && setup == null) ...[
                  const SizedBox(height: 24),
                  Text(
                    'Configura el segundo factor',
                    style: Theme.of(context).textTheme.titleLarge,
                  ),
                  const Text(
                    'Usa un autenticador TOTP. Puedes registrarlo en un autenticador local del equipo para las pruebas.',
                  ),
                  const SizedBox(height: 16),
                  TextField(
                    controller: password,
                    obscureText: true,
                    enableSuggestions: false,
                    autocorrect: false,
                    decoration: decoration('Contrasena actual'),
                  ),
                  if (enrolled) ...[
                    const SizedBox(height: 12),
                    TextField(
                      controller: currentOtp,
                      keyboardType: TextInputType.number,
                      inputFormatters: [
                        FilteringTextInputFormatter.digitsOnly,
                        LengthLimitingTextInputFormatter(6),
                      ],
                      enableSuggestions: false,
                      autocorrect: false,
                      decoration: decoration('Codigo actual (6 digitos)'),
                    ),
                    const SizedBox(height: 12),
                    TextField(
                      controller: recovery,
                      obscureText: true,
                      enableSuggestions: false,
                      autocorrect: false,
                      decoration: decoration('O un codigo de recuperacion'),
                    ),
                  ],
                  const SizedBox(height: 12),
                  OutlinedButton.icon(
                    onPressed: busy ? null : () => run(enroll),
                    icon: const Icon(Icons.security),
                    label: const Text('Preparar autenticador'),
                  ),
                  if (changingFactor)
                    TextButton(
                      onPressed: busy
                          ? null
                          : () => setState(() => changingFactor = false),
                      child: const Text('Conservar mi autenticador'),
                    ),
                ],
                if (setup != null) ...[
                  const SizedBox(height: 20),
                  Text(
                    'Agrega esta cuenta a tu autenticador',
                    style: Theme.of(context).textTheme.titleLarge,
                  ),
                  const Text(
                    'Escanea el QR o introduce la clave manualmente. No compartas esta clave. La configuracion vence en cinco minutos.',
                  ),
                  Center(
                    child: Container(
                      color: Colors.white,
                      padding: const EdgeInsets.all(12),
                      child: QrImageView(
                        data: setup!['uri'] as String,
                        size: 220,
                      ),
                    ),
                  ),
                  SelectableText(setup!['secreto'] as String),
                  TextButton(
                    onPressed: () async {
                      await Clipboard.setData(
                        ClipboardData(text: setup!['secreto'] as String),
                      );
                    },
                    child: const Text('Copiar clave para registro manual'),
                  ),
                  codeField(),
                  const SizedBox(height: 12),
                  FilledButton(
                    onPressed: busy ? null : () => run(activateFactor),
                    child: const Text('Activar segundo factor'),
                  ),
                  TextButton(
                    onPressed: busy
                        ? null
                        : () => setState(() {
                            setup = null;
                            otp.clear();
                          }),
                    child: const Text('Cancelar configuracion'),
                  ),
                ],
                const SizedBox(height: 24),
                if (accounts.isEmpty && !busy)
                  const Text(
                    'No tienes cuentas activas disponibles para pagar.',
                  ),
                Form(
                  key: form,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      DropdownButtonFormField<Map<String, dynamic>>(
                        isExpanded: true,
                        initialValue: account,
                        decoration: decoration('Cuenta de cargo'),
                        items: accounts
                            .map(
                              (a) => DropdownMenuItem(
                                value: a,
                                child: Text(
                                  '${a['numeroCuenta']} · Disponible ${a['saldoDisponible']} MXN',
                                  maxLines: 2,
                                ),
                              ),
                            )
                            .toList(),
                        onChanged: busy
                            ? null
                            : (a) => setState(() => account = a),
                        validator: (a) =>
                            a == null ? 'Selecciona tu cuenta' : null,
                      ),
                      const SizedBox(height: 16),
                      Autocomplete<Map<String, dynamic>>(
                        displayStringForOption: (p) =>
                            '${p['servicio']} · ${p['producto']}',
                        optionsBuilder: (v) => products
                            .where(
                              (p) => '${p['servicio']} ${p['producto']}'
                                  .toLowerCase()
                                  .contains(v.text.toLowerCase()),
                            )
                            .take(30),
                        fieldViewBuilder:
                            (
                              context,
                              controller,
                              focus,
                              submit,
                            ) => TextFormField(
                              controller: controller,
                              focusNode: focus,
                              enabled: !busy,
                              decoration: decoration(
                                'Buscar servicio o producto',
                              ),
                              onChanged: (_) => setState(() => product = null),
                              validator: (_) => product == null
                                  ? 'Selecciona una coincidencia del catalogo'
                                  : null,
                            ),
                        onSelected: (p) => setState(() {
                          product = p;
                          amount.text = p['tipoFront'] == 1
                              ? p['precio'].toString()
                              : '';
                        }),
                      ),
                      const SizedBox(height: 16),
                      TextFormField(
                        controller: amount,
                        readOnly: product?['tipoFront'] == 1,
                        enabled: !busy,
                        keyboardType: const TextInputType.numberWithOptions(
                          decimal: true,
                        ),
                        decoration: decoration('Importe en MXN'),
                        validator: (v) =>
                            v == null ||
                                !RegExp(r'^\d+(\.\d{1,2})?$').hasMatch(v) ||
                                (double.tryParse(v) ?? 0) <= 0
                            ? 'Escribe un importe positivo con hasta 2 decimales'
                            : null,
                      ),
                      const SizedBox(height: 16),
                      if (product?['tipoFront'] == 1 && !gift)
                        TextFormField(
                          controller: phone,
                          enabled: !busy,
                          keyboardType: TextInputType.phone,
                          decoration: decoration('Telefono de la recarga'),
                          validator: (v) =>
                              v != null && RegExp(r'^\d{10,20}$').hasMatch(v)
                              ? null
                              : 'Escribe de 10 a 20 digitos',
                        ),
                      if (product != null && product!['tipoFront'] != 1)
                        TextFormField(
                          controller: reference,
                          enabled: !busy,
                          maxLength: 100,
                          decoration: decoration('Referencia del recibo'),
                          validator: (v) => v == null || v.trim().isEmpty
                              ? 'Escribe la referencia del recibo'
                              : null,
                        ),
                      const SizedBox(height: 24),
                      FilledButton(
                        onPressed:
                            busy ||
                                !enrolled ||
                                setup != null ||
                                changingFactor ||
                                recoveryCodes.isNotEmpty ||
                                accounts.isEmpty
                            ? null
                            : () => run(prepare),
                        child: const Text('Revisar pago'),
                      ),
                    ],
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    ),
  );
}
