import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:http/http.dart' as http;
import 'package:qr_flutter/qr_flutter.dart';

import '../models/login_session.dart';
import '../services/payment_api_service.dart';
import '../theme.dart';
import '../utils/money_format.dart';
import '../widgets/brand_wordmark.dart';
import '../widgets/paper_background.dart';
import '../widgets/catalog_product_picker.dart';

class PaymentPage extends StatefulWidget {
  const PaymentPage({
    super.key,
    required this.session,
    this.api,
    this.pendingStore,
    this.initialService,
  });

  final LoginSession session;
  final PaymentApiService? api;
  final PendingPaymentStore? pendingStore;
  final String? initialService;

  @override
  State<PaymentPage> createState() => _PaymentPageState();
}

class _PaymentPageState extends State<PaymentPage> {
  static const _terminalStates = {'APLICADO', 'RECHAZADO', 'EXPIRADO'};

  final _client = http.Client();
  final _formKey = GlobalKey<FormState>();
  final _scrollController = ScrollController();
  final _amountController = TextEditingController();
  final _referenceController = TextEditingController();
  final _phoneController = TextEditingController();

  final _passwordController = TextEditingController();
  final _otpController = TextEditingController();
  final _currentOtpController = TextEditingController();
  final _recoveryController = TextEditingController();

  late final PaymentApiService _api =
      widget.api ?? PaymentApiService(_client, widget.session);
  late final PendingPaymentStore _pending =
      widget.pendingStore ?? const PendingPaymentStore();

  List<Map<String, dynamic>> _accounts = [];
  List<Map<String, dynamic>> _products = [];
  Map<String, dynamic>? _account;
  Map<String, dynamic>? _product;
  Map<String, dynamic>? _payment;
  Map<String, dynamic>? _factorSetup;
  List<String> _recoveryCodes = [];
  String? _pendingId;
  String? _error;
  bool _busy = false;
  bool _enrolled = false;
  bool _changingFactor = false;
  bool _revealPin = false;
  bool _paymentsUnavailable = false;
  bool _obscurePassword = true;
  bool _showRecoveryInput = false;

  bool get _hasUnresolvedPayment => _pendingId != null;
  bool get _terminal => _terminalStates.contains(_payment?['estado']);
  bool get _isGiftCard =>
      const [10, 11].contains(_product?['idCatTipoServicio']);
  bool get _requiresPhone => _product?['tipoFront'] == 1 && !_isGiftCard;
  bool get _requiresReference =>
      _product != null && _product!['tipoFront'] != 1;

  @override
  void initState() {
    super.initState();

    _run(_load);
  }

  @override
  void dispose() {
    _client.close();
    _scrollController.dispose();
    _amountController.dispose();
    _referenceController.dispose();
    _phoneController.dispose();

    _passwordController.dispose();
    _otpController.dispose();
    _currentOtpController.dispose();
    _recoveryController.dispose();
    super.dispose();
  }

  Future<void> _run(Future<void> Function() action) async {
    if (_busy) return;
    setState(() {
      _busy = true;
      _error = null;
      _paymentsUnavailable = false;
    });
    try {
      await action();
    } on PaymentFailure catch (failure) {
      _error = failure.message;
      _paymentsUnavailable = failure.status == 503;
    } catch (_) {
      _error = _hasUnresolvedPayment
          ? 'No se pudo consultar el pago. No lo repitas; vuelve a consultar su estado.'
          : 'No se pudo completar la solicitud. Revisa tu conexión e inténtalo de nuevo.';
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _load() async {
    _pendingId = await _pending.read(widget.session.userId);
    if (_pendingId != null) {
      await _consult();
      return;
    }

    final factor = await _api.request('/api/pagos/segundo-factor/estado', {});
    _enrolled = factor['activo'] == true;

    final accountResponse = await _api.request('/api/cuentas/mias/consulta', {
      'pagina': 0,
      'tamanio': 100,
    });
    final selectedNumber = _account?['numeroCuenta'];
    _accounts = (accountResponse['contenido'] as List? ?? [])
        .whereType<Map<String, dynamic>>()
        .where((account) => account['activa'] == true)
        .toList(growable: false);
    // Las respuestas JSON crean mapas nuevos. Mantener la selección por su
    // número y usar los datos recién consultados, no el mapa anterior.
    final selectedAccounts = _accounts.where(
      (account) => account['numeroCuenta'] == selectedNumber,
    );
    _account = selectedAccounts.isEmpty ? null : selectedAccounts.first;

    final productResponse = await _api.request('/api/products');
    final productContainer = productResponse['productos'];
    final productItems = productContainer is Map
        ? productContainer['productos']
        : null;
    _products = (productItems is List ? productItems : const [])
        .whereType<Map<String, dynamic>>()
        .where((product) => const [1, 2, 4].contains(product['tipoFront']))
        .toList(growable: false);
  }

  Future<void> _prepareFactor() async {
    if (_passwordController.text.isEmpty) {
      throw const PaymentFailure('Escribe tu contraseña actual.');
    }
    final currentCode = _currentOtpController.text.trim();
    final recoveryCode = _recoveryController.text.trim();
    if (_enrolled && currentCode.isEmpty && recoveryCode.isEmpty) {
      throw const PaymentFailure(
        'Para reemplazar el autenticador, confirma el código actual o usa uno de recuperación.',
      );
    }
    if (currentCode.isNotEmpty && recoveryCode.isNotEmpty) {
      throw const PaymentFailure('Usa un solo método de verificación.');
    }
    if (recoveryCode.isNotEmpty &&
        !RegExp(
          r'^[A-F0-9]{8}(?:-[A-F0-9]{8}){3}$',
        ).hasMatch(recoveryCode.toUpperCase())) {
      throw const PaymentFailure(
        'El código de recuperación debe tener cuatro grupos de ocho caracteres.',
      );
    }
    try {
      _factorSetup = await _api.request('/api/pagos/segundo-factor/preparar', {
        'contrasena': _passwordController.text,
        if (currentCode.isNotEmpty) 'codigoActual': currentCode,
        if (recoveryCode.isNotEmpty)
          'codigoRecuperacion': recoveryCode.toUpperCase(),
      });
    } finally {
      _passwordController.clear();
      _currentOtpController.clear();
      _recoveryController.clear();
    }
  }

  Future<void> _activateFactor() async {
    final code = _otpController.text.trim();
    if (!RegExp(r'^\d{6}$').hasMatch(code)) {
      throw const PaymentFailure('Escribe los seis dígitos del autenticador.');
    }
    try {
      final response = await _api.request('/api/pagos/segundo-factor/activar', {
        'codigo': code,
      });
      _recoveryCodes = (response['codigosRecuperacion'] as List? ?? [])
          .whereType<String>()
          .toList(growable: false);
      _enrolled = response['activo'] == true;
      _changingFactor = false;
      _factorSetup = null;
      _scrollToTop();
    } finally {
      _otpController.clear();
    }
  }

  Future<void> _preparePayment() async {
    if (!_formKey.currentState!.validate()) return;
    if (_product == null) {
      throw const PaymentFailure('Selecciona un producto del catálogo.');
    }
    if (_validateAmount(_amountController.text) != null) {
      throw const PaymentFailure(
        'El catálogo no devolvió un importe válido para este producto.',
      );
    }
    final requestId = PendingPaymentStore.newId();
    await _pending.write(widget.session.userId, requestId);
    _pendingId = requestId;
    try {
      _payment = await _api.request('/api/pagos/preparar', {
        'solicitudId': requestId,
        'numeroCuenta': _account!['numeroCuenta'],
        'idProducto': _product!['idProducto'],
        'idServicio': _product!['idServicio'],
        'tipoFront': _product!['tipoFront'],
        'monto': _amountController.text.trim(),
        if (_requiresPhone) 'telefono': _phoneController.text.trim(),
        if (_requiresReference) 'referencia': _referenceController.text.trim(),
      });
    } on PaymentFailure catch (failure) {
      // Rechazo definitivo de la preparación: nunca se llamó a autorizar.
      if (const [400, 401, 403, 404, 409, 422].contains(failure.status)) {
        await _pending.clear(widget.session.userId);
        _pendingId = null;
      }
      rethrow;
    }
  }

  Future<void> _consult() async {
    final requestId = _pendingId;
    if (requestId == null) return;
    try {
      _payment = await _api.request('/api/pagos/consulta', {
        'pagoId': requestId,
      });
    } on PaymentFailure catch (failure) {
      if (failure.status != 404) rethrow;
      // There is no order to authorize, so it is safe to release the local hold.
      await _pending.clear(widget.session.userId);
      _pendingId = null;
      _payment = null;
      await _load();
      throw const PaymentFailure(
        'No encontramos una solicitud preparada. Puedes volver a completar el formulario.',
      );
    }
  }

  Future<void> _authorize() async {
    final code = _otpController.text.trim();
    if (!RegExp(r'^\d{6}$').hasMatch(code)) {
      throw const PaymentFailure('Escribe los seis dígitos del autenticador.');
    }
    try {
      _payment = await _api.request('/api/pagos/autorizar', {
        'pagoId': _pendingId,
        'codigo': code,
      });
    } finally {
      _otpController.clear();
    }
  }

  Future<void> _cancelPreparedPayment() async {
    if (_payment?['estado'] != 'PREPARADO') return;
    _payment = await _api.request('/api/pagos/cancelar', {
      'pagoId': _pendingId,
    });
  }

  Future<void> _finishPayment() async {
    if (!_terminal) return;
    await _pending.clear(widget.session.userId);
    _pendingId = null;
    _payment = null;
    _product = null;
    _revealPin = false;
    await _load();
  }

  void _scrollToTop() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted && _scrollController.hasClients) {
        _scrollController.animateTo(
          0,
          duration: const Duration(milliseconds: 220),
          curve: Curves.easeOutCubic,
        );
      }
    });
  }

  void _selectProduct(Map<String, dynamic> product) {
    setState(() {
      _product = product;
      _amountController.text = product['tipoFront'] == 1
          ? (product['precio']?.toString() ?? '')
          : '';
      _referenceController.clear();
      _phoneController.clear();
    });
  }

  Future<void> _copy(String value, String message) async {
    await Clipboard.setData(ClipboardData(text: value));
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Pagar un servicio')),
      body: PaperBackground(
        child: SafeArea(
          child: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 640),
              child: CustomScrollView(
                controller: _scrollController,
                keyboardDismissBehavior:
                    ScrollViewKeyboardDismissBehavior.onDrag,
                slivers: [
                  SliverPadding(
                    padding: const EdgeInsets.fromLTRB(22, 8, 22, 34),
                    sliver: SliverToBoxAdapter(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          const BrandWordmark(compact: true),
                          const SizedBox(height: 28),
                          Text(
                            _hasUnresolvedPayment
                                ? 'Estado de tu pago'
                                : 'Paga tus servicios',
                            style: Theme.of(context).textTheme.headlineMedium,
                          ),
                          const SizedBox(height: 8),
                          Text(
                            _hasUnresolvedPayment
                                ? 'Conservamos esta solicitud hasta conocer el resultado.'
                                : 'Selecciona una cuenta y un producto del catálogo disponible.',
                            style: Theme.of(context).textTheme.bodyLarge,
                          ),
                          const SizedBox(height: 22),
                          if (_busy)
                            const Padding(
                              padding: EdgeInsets.only(bottom: 18),
                              child: LinearProgressIndicator(
                                semanticsLabel: 'Procesando solicitud',
                              ),
                            ),
                          if (_error != null) ...[
                            _MessageCard(
                              icon: _paymentsUnavailable
                                  ? Icons.pause_circle_outline_rounded
                                  : Icons.info_outline_rounded,
                              message: _error!,
                              isError: !_paymentsUnavailable,
                            ),
                            const SizedBox(height: 14),
                            if (_hasUnresolvedPayment)
                              OutlinedButton.icon(
                                onPressed: _busy ? null : () => _run(_consult),
                                icon: const Icon(Icons.refresh_rounded),
                                label: const Text('Consultar estado'),
                              )
                            else if (!_paymentsUnavailable)
                              OutlinedButton.icon(
                                onPressed: _busy ? null : () => _run(_load),
                                icon: const Icon(Icons.refresh_rounded),
                                label: const Text('Volver a cargar'),
                              ),
                            const SizedBox(height: 18),
                          ],
                          if (_hasUnresolvedPayment)
                            _buildPaymentStatus()
                          else if (_paymentsUnavailable)
                            const _EmptyStateCard(
                              icon: Icons.pause_circle_outline_rounded,
                              title: 'Pagos no disponibles',
                              message: 'Vuelve a intentarlo más tarde.',
                            )
                          else if (_factorSetup != null)
                            _buildFactorActivation()
                          else if (_recoveryCodes.isNotEmpty)
                            _buildRecoveryCodes()
                          else if (!_enrolled || _changingFactor)
                            _buildFactorPreparation()
                          else
                            _buildPaymentForm(),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildPaymentForm() {
    if (_accounts.isEmpty && !_busy) {
      return _EmptyStateCard(
        icon: Icons.account_balance_wallet_outlined,
        title: 'No hay cuentas activas',
        message: 'Necesitas una cuenta activa para preparar un pago.',
        action: OutlinedButton.icon(
          onPressed: _busy ? null : () => _run(_load),
          icon: const Icon(Icons.refresh_rounded),
          label: const Text('Actualizar'),
        ),
      );
    }
    if (_products.isEmpty && !_busy) {
      return _EmptyStateCard(
        icon: Icons.receipt_long_outlined,
        title: 'Catálogo sin productos disponibles',
        message: 'En este momento no hay productos que se puedan pagar.',
        action: OutlinedButton.icon(
          onPressed: _busy ? null : () => _run(_load),
          icon: const Icon(Icons.refresh_rounded),
          label: const Text('Actualizar catálogo'),
        ),
      );
    }

    return Form(
      key: _formKey,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const _SectionLabel(
            eyebrow: '01',
            title: 'Elige tu cuenta',
            description: 'Solo aparecen cuentas activas.',
          ),
          const SizedBox(height: 12),
          DropdownButtonFormField<String>(
            key: ValueKey(_account?['numeroCuenta']),
            isExpanded: true,
            initialValue: _account?['numeroCuenta'] as String?,
            decoration: const InputDecoration(
              labelText: 'Cuenta de cargo',
              prefixIcon: Icon(Icons.account_balance_wallet_outlined),
            ),
            items: _accounts
                .map(
                  (account) => DropdownMenuItem(
                    value: account['numeroCuenta'] as String,
                    child: Text(
                      '${_maskedAccount(account['numeroCuenta'])} · ${MoneyFormat.mxn(account['saldoDisponible'])}',
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                )
                .toList(growable: false),
            onChanged: _busy
                ? null
                : (value) => setState(
                    () => _account = value == null
                        ? null
                        : _accounts.firstWhere(
                            (account) => account['numeroCuenta'] == value,
                          ),
                  ),
            validator: (value) =>
                value == null ? 'Selecciona una cuenta' : null,
          ),
          const SizedBox(height: 26),
          const _SectionLabel(
            eyebrow: '02',
            title: 'Selecciona un servicio',
            description: 'Elige un producto tal como aparece en el catálogo.',
          ),
          const SizedBox(height: 12),
          CatalogProductPicker(
            products: _products,
            selected: _product,
            enabled: !_busy,
            initialSearch: widget.initialService ?? '',
            onChanged: _selectProduct,
          ),
          if (_product != null) ...[
            const SizedBox(height: 24),
            const _SectionLabel(
              eyebrow: '03',
              title: 'Datos del pago',
              description: 'Captura la información que solicita este producto.',
            ),
            const SizedBox(height: 12),
            if (_requiresReference)
              TextFormField(
                controller: _referenceController,
                enabled: !_busy,
                maxLength: 100,
                textInputAction: TextInputAction.next,
                decoration:
                    const InputDecoration(
                      labelText: 'Referencia del servicio',
                      prefixIcon: Icon(Icons.tag_rounded),
                    ).copyWith(
                      helperText:
                          _product?['showAyuda'] == true &&
                              _text(_product?['legend']).isNotEmpty
                          ? _text(_product?['legend'])
                          : null,
                    ),
                validator: (value) {
                  if (value == null || value.trim().isEmpty) {
                    return 'Escribe la referencia que aparece en tu recibo';
                  }
                  if (value.runes.any((rune) => rune < 32 || rune == 127)) {
                    return 'La referencia contiene caracteres no permitidos';
                  }
                  return null;
                },
              ),
            if (_requiresPhone)
              TextFormField(
                controller: _phoneController,
                enabled: !_busy,
                keyboardType: TextInputType.phone,
                inputFormatters: [
                  FilteringTextInputFormatter.digitsOnly,
                  LengthLimitingTextInputFormatter(20),
                ],
                textInputAction: TextInputAction.next,
                decoration: const InputDecoration(
                  labelText: 'Teléfono de la recarga',
                  helperText: 'Escribe entre 10 y 20 dígitos.',
                  prefixIcon: Icon(Icons.phone_iphone_rounded),
                ),
                validator: (value) =>
                    value != null &&
                        RegExp(r'^\d{10,20}$').hasMatch(value.trim())
                    ? null
                    : 'Escribe entre 10 y 20 dígitos',
              ),
            if (_product!['tipoFront'] != 1)
              TextFormField(
                controller: _amountController,
                enabled: !_busy,
                keyboardType: const TextInputType.numberWithOptions(
                  decimal: true,
                ),
                inputFormatters: [
                  FilteringTextInputFormatter.allow(RegExp(r'[0-9.]')),
                  LengthLimitingTextInputFormatter(12),
                ],
                textInputAction: TextInputAction.done,
                decoration: const InputDecoration(
                  labelText: 'Importe en MXN',
                  prefixIcon: Icon(Icons.payments_outlined),
                ),
                validator: _validateAmount,
              )
            else
              _FixedPriceCard(price: _product!['precio']),
          ],
          const SizedBox(height: 24),
          if (!_enrolled)
            const _MessageCard(
              icon: Icons.lock_outline_rounded,
              message:
                  'Configura el código de autenticación antes de continuar.',
            ),
          const SizedBox(height: 8),
          FilledButton.icon(
            onPressed: _busy || !_enrolled || _recoveryCodes.isNotEmpty
                ? null
                : () => _run(_preparePayment),
            icon: const Icon(Icons.arrow_forward_rounded),
            label: const Text('Revisar pago'),
          ),
          if (_enrolled && !_changingFactor)
            Center(
              child: TextButton.icon(
                onPressed: _busy
                    ? null
                    : () => setState(() => _changingFactor = true),
                icon: const Icon(Icons.security_rounded),
                label: const Text('Cambiar autenticador'),
              ),
            ),
        ],
      ),
    );
  }

  String? _validateAmount(String? value) {
    final text = value?.trim() ?? '';
    if (!RegExp(r'^\d+(\.\d{1,2})?$').hasMatch(text)) {
      return 'Escribe un importe con hasta 2 decimales';
    }
    if ((double.tryParse(text) ?? 0) <= 0) {
      return 'El importe debe ser mayor que cero';
    }
    return null;
  }

  Widget _buildPaymentStatus() {
    final payment = _payment;
    if (payment == null) {
      return _EmptyStateCard(
        icon: Icons.hourglass_top_rounded,
        title: 'Solicitud por confirmar',
        message:
            'Estamos verificando si se preparó. Consulta el estado antes de iniciar otro pago.',
        action: OutlinedButton.icon(
          onPressed: _busy ? null : () => _run(_consult),
          icon: const Icon(Icons.refresh_rounded),
          label: const Text('Consultar estado'),
        ),
      );
    }

    final state = _text(payment['estado']);
    final prepared = state == 'PREPARADO';
    final success = state == 'APLICADO';
    final statusIcon = success
        ? Icons.check_circle_outline_rounded
        : prepared
        ? Icons.fact_check_outlined
        : state == 'RECHAZADO' || state == 'EXPIRADO'
        ? Icons.error_outline_rounded
        : Icons.hourglass_top_rounded;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Container(
          padding: const EdgeInsets.all(20),
          decoration: BoxDecoration(
            color: success ? const Color(0xFFE6EBDD) : warmSurface,
            border: Border.all(
              color: success ? ink.withValues(alpha: .24) : outlineColor,
            ),
            borderRadius: BorderRadius.circular(24),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Icon(statusIcon, color: success ? ink : terracotta, size: 36),
              const SizedBox(height: 12),
              Text(
                _statusTitle(state),
                style: Theme.of(context).textTheme.headlineSmall,
              ),
              const SizedBox(height: 6),
              Text(
                _statusDescription(state, payment),
                style: Theme.of(context).textTheme.bodyMedium,
              ),
              if (_text(payment['aviso']).isNotEmpty) ...[
                const SizedBox(height: 14),
                Text(
                  _text(payment['aviso']),
                  style: Theme.of(context).textTheme.bodyMedium,
                ),
              ],
            ],
          ),
        ),
        const SizedBox(height: 16),
        _paymentDetails(payment),
        if (state == 'PREPARADO') ...[
          const SizedBox(height: 24),
          const _SectionLabel(
            eyebrow: '04',
            title: 'Autoriza el pago',
            description:
                'Abre tu autenticador e ingresa el código de seis dígitos.',
          ),
          const SizedBox(height: 12),
          _otpField(label: 'Código del autenticador'),
          const SizedBox(height: 14),
          FilledButton.icon(
            onPressed:
                _busy ||
                    _paymentsUnavailable ||
                    payment['autorizacionDisponible'] != true
                ? null
                : () => _run(_authorize),
            icon: const Icon(Icons.lock_open_rounded),
            label: const Text('Autorizar pago'),
          ),
          if (payment['autorizacionDisponible'] == false &&
              _text(payment['reglaImporteReferencia']).isNotEmpty) ...[
            const SizedBox(height: 10),
            _MessageCard(
              icon: Icons.info_outline_rounded,
              message: _referenceRuleMessage(payment['reglaImporteReferencia']),
            ),
          ],
          const SizedBox(height: 8),
          TextButton.icon(
            onPressed: _busy ? null : () => _run(_cancelPreparedPayment),
            icon: const Icon(Icons.close_rounded),
            label: const Text('Cancelar solicitud preparada'),
          ),
        ] else if (_terminal) ...[
          const SizedBox(height: 24),
          FilledButton.icon(
            onPressed: _busy ? null : () => _run(_finishPayment),
            icon: const Icon(Icons.home_outlined),
            label: const Text('Volver a mis servicios'),
          ),
        ] else ...[
          const SizedBox(height: 18),
          const _MessageCard(
            icon: Icons.schedule_rounded,
            message:
                'No inicies otro pago todavía. Puedes consultar nuevamente cuando el proveedor confirme el resultado.',
          ),
          const SizedBox(height: 14),
          OutlinedButton.icon(
            onPressed: _busy ? null : () => _run(_consult),
            icon: const Icon(Icons.refresh_rounded),
            label: const Text('Consultar estado'),
          ),
        ],
      ],
    );
  }

  Widget _paymentDetails(Map<String, dynamic> payment) {
    final lines = <Widget>[];
    void add(String label, Object? value, {bool money = false}) {
      if (value == null || value.toString().trim().isEmpty) return;
      lines.add(
        _DetailLine(
          label: label,
          value: money ? MoneyFormat.mxn(value) : value.toString(),
        ),
      );
    }

    add('Servicio', payment['concepto']);
    add('Cuenta', _maskedAccount(payment['numeroCuenta']));
    add('Importe', payment['monto'], money: true);
    add('Comisión', payment['comisionCliente'], money: true);
    if (payment['monto'] != null && payment['comisionCliente'] != null) {
      final total = MoneyFormat.totalMxn(
        payment['monto'],
        payment['comisionCliente'],
      );
      if (total != null) {
        lines.add(_DetailLine(label: 'Total', value: total, emphasis: true));
      }
    }
    add('Teléfono', payment['telefono']);
    add('Referencia', payment['referencia']);
    add(
      'Saldo informado del servicio',
      payment['saldoReferencia'],
      money: true,
    );
    add('Autorización', payment['autorizacion']);
    add('Folio de pago', payment['upc']);
    add('Vigencia', _formatDate(payment['venceEn']));

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 8),
      decoration: BoxDecoration(
        color: warmSurface,
        borderRadius: BorderRadius.circular(22),
        border: Border.all(color: outlineColor),
      ),
      child: Column(
        children: [
          for (var index = 0; index < lines.length; index++) ...[
            if (index > 0) const Divider(height: 1),
            lines[index],
          ],
          if (_text(payment['pin']).isNotEmpty) ...[
            const Divider(height: 1),
            ListTile(
              contentPadding: EdgeInsets.zero,
              title: const Text('PIN de la tarjeta'),
              subtitle: Text(
                _revealPin ? _text(payment['pin']) : 'Oculto por seguridad',
              ),
              trailing: IconButton(
                tooltip: _revealPin ? 'Ocultar PIN' : 'Mostrar PIN',
                onPressed: () => setState(() => _revealPin = !_revealPin),
                icon: Icon(
                  _revealPin
                      ? Icons.visibility_off_outlined
                      : Icons.visibility_outlined,
                ),
              ),
            ),
          ],
          if (_text(payment['leyenda']).isNotEmpty) ...[
            const Divider(height: 1),
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 14),
              child: Align(
                alignment: Alignment.centerLeft,
                child: SelectableText(_text(payment['leyenda'])),
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildFactorPreparation() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _SectionLabel(
          eyebrow: 'SEGURIDAD',
          title: _changingFactor
              ? 'Configura un nuevo autenticador'
              : 'Protege tus pagos',
          description:
              'Vincula una aplicación de autenticación para autorizar tus pagos.',
        ),
        const SizedBox(height: 14),
        _MessageCard(
          icon: Icons.verified_user_outlined,
          message: _changingFactor
              ? 'Confirma tu identidad para reemplazar la clave del autenticador.'
              : 'La configuración usa una clave TOTP de seis dígitos. Los códigos de recuperación sirven para volver a configurarla.',
        ),
        const SizedBox(height: 18),
        TextField(
          controller: _passwordController,
          enabled: !_busy,
          obscureText: _obscurePassword,
          maxLength: 72,
          autocorrect: false,
          enableSuggestions: false,
          decoration: InputDecoration(
            labelText: 'Contraseña actual',
            prefixIcon: const Icon(Icons.lock_outline_rounded),
            suffixIcon: IconButton(
              tooltip: _obscurePassword
                  ? 'Mostrar contraseña'
                  : 'Ocultar contraseña',
              onPressed: () =>
                  setState(() => _obscurePassword = !_obscurePassword),
              icon: Icon(
                _obscurePassword
                    ? Icons.visibility_outlined
                    : Icons.visibility_off_outlined,
              ),
            ),
          ),
        ),
        if (_enrolled && _changingFactor) ...[
          const SizedBox(height: 12),
          if (!_showRecoveryInput)
            _otpField(
              controller: _currentOtpController,
              label: 'Código actual del autenticador',
            )
          else
            TextField(
              controller: _recoveryController,
              enabled: !_busy,
              textCapitalization: TextCapitalization.characters,
              autocorrect: false,
              enableSuggestions: false,
              decoration: const InputDecoration(
                labelText: 'Código de recuperación',
                prefixIcon: Icon(Icons.key_outlined),
              ),
            ),
          TextButton.icon(
            onPressed: _busy
                ? null
                : () => setState(() {
                    _showRecoveryInput = !_showRecoveryInput;
                    if (_showRecoveryInput) {
                      _currentOtpController.clear();
                    } else {
                      _recoveryController.clear();
                    }
                  }),
            icon: Icon(
              _showRecoveryInput ? Icons.password_rounded : Icons.key_outlined,
            ),
            label: Text(
              _showRecoveryInput
                  ? 'Usar código actual'
                  : 'Usar código de recuperación',
            ),
          ),
        ],
        const SizedBox(height: 16),
        FilledButton.icon(
          onPressed: _busy ? null : () => _run(_prepareFactor),
          icon: const Icon(Icons.qr_code_2_rounded),
          label: const Text('Preparar autenticador'),
        ),
        if (_changingFactor)
          Center(
            child: TextButton(
              onPressed: _busy
                  ? null
                  : () => setState(() {
                      _changingFactor = false;
                      _showRecoveryInput = false;
                    }),
              child: const Text('Conservar mi autenticador'),
            ),
          ),
      ],
    );
  }

  Widget _buildFactorActivation() {
    final setup = _factorSetup!;
    final uri = _text(setup['uri']);
    final secret = _text(setup['secreto']);
    final expiration = _formatDate(setup['venceEn']);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const _SectionLabel(
          eyebrow: 'SEGURIDAD',
          title: 'Vincula tu autenticador',
          description: 'Escanea el código o registra la clave manualmente.',
        ),
        const SizedBox(height: 14),
        Container(
          padding: const EdgeInsets.all(18),
          decoration: BoxDecoration(
            color: warmSurface,
            border: Border.all(color: outlineColor),
            borderRadius: BorderRadius.circular(22),
          ),
          child: Column(
            children: [
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(16),
                ),
                child: QrImageView(
                  data: uri,
                  size: 196,
                  semanticsLabel: 'Código QR para configurar el autenticador',
                ),
              ),
              const SizedBox(height: 14),
              Text(
                'Clave de configuración',
                style: Theme.of(context).textTheme.titleMedium,
              ),
              const SizedBox(height: 5),
              SelectableText(secret, textAlign: TextAlign.center),
              TextButton.icon(
                onPressed: secret.isEmpty
                    ? null
                    : () => _copy(secret, 'Clave copiada al portapapeles'),
                icon: const Icon(Icons.copy_rounded),
                label: const Text('Copiar clave'),
              ),
            ],
          ),
        ),
        const SizedBox(height: 14),
        _MessageCard(
          icon: Icons.schedule_rounded,
          message: expiration.isEmpty
              ? 'No compartas esta clave. Usa los datos de configuración cuanto antes.'
              : 'No compartas esta clave. La preparación vence el $expiration.',
        ),
        const SizedBox(height: 22),
        _otpField(label: 'Código de seis dígitos'),
        const SizedBox(height: 14),
        FilledButton.icon(
          onPressed: _busy ? null : () => _run(_activateFactor),
          icon: const Icon(Icons.verified_outlined),
          label: const Text('Activar autenticador'),
        ),
        Center(
          child: TextButton(
            onPressed: _busy
                ? null
                : () => setState(() {
                    _factorSetup = null;
                    _otpController.clear();
                  }),
            child: const Text('Cancelar configuración'),
          ),
        ),
      ],
    );
  }

  Widget _buildRecoveryCodes() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const Icon(Icons.shield_outlined, size: 44, color: terracotta),
        const SizedBox(height: 12),
        Text(
          'Guarda tus códigos',
          style: Theme.of(context).textTheme.headlineSmall,
        ),
        const SizedBox(height: 8),
        const Text(
          'Se muestran una sola vez. Cada código permite configurar de nuevo el autenticador; no sirve para autorizar un pago.',
        ),
        const SizedBox(height: 16),
        Container(
          padding: const EdgeInsets.all(18),
          decoration: BoxDecoration(
            color: warmSurface,
            borderRadius: BorderRadius.circular(18),
            border: Border.all(color: outlineColor),
          ),
          child: SelectableText(
            _recoveryCodes.join('\n'),
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.titleMedium,
          ),
        ),
        const SizedBox(height: 12),
        OutlinedButton.icon(
          onPressed: () => _copy(_recoveryCodes.join('\n'), 'Códigos copiados'),
          icon: const Icon(Icons.copy_rounded),
          label: const Text('Copiar códigos'),
        ),
        const SizedBox(height: 8),
        FilledButton(
          onPressed: () => setState(() => _recoveryCodes = []),
          child: const Text('Ya los guardé'),
        ),
      ],
    );
  }

  Widget _otpField({TextEditingController? controller, required String label}) {
    final fieldController = controller ?? _otpController;
    return TextField(
      controller: fieldController,
      enabled: !_busy,
      keyboardType: TextInputType.number,
      inputFormatters: [
        FilteringTextInputFormatter.digitsOnly,
        LengthLimitingTextInputFormatter(6),
      ],
      enableSuggestions: false,
      autocorrect: false,
      autofillHints: const [AutofillHints.oneTimeCode],
      textInputAction: TextInputAction.done,
      decoration: InputDecoration(
        labelText: label,
        hintText: '000000',
        helperText: 'Puedes pegar el código completo.',
        prefixIcon: const Icon(Icons.password_rounded),
      ),
    );
  }

  String _statusTitle(String state) => switch (state) {
    'APLICADO' => 'Pago realizado',
    'PREPARADO' => 'Revisa tu pago',
    'ENVIADO' || 'PENDIENTE' => 'Pago en proceso',
    'REVISION' => 'Pago en revisión',
    'RECHAZADO' => 'Pago no aplicado',
    'EXPIRADO' => 'Solicitud vencida',
    _ => 'Estamos verificando tu pago',
  };

  String _statusDescription(String state, Map<String, dynamic> payment) {
    if (_text(payment['aviso']).isNotEmpty) return _text(payment['aviso']);
    return switch (state) {
      'APLICADO' => 'El proveedor confirmó que el pago se aplicó.',
      'PREPARADO' =>
        'Aún no se ha cobrado. Revisa los datos y autoriza para continuar.',
      'ENVIADO' || 'PENDIENTE' =>
        'El resultado todavía no está confirmado. Consulta su estado antes de intentar otra vez.',
      'REVISION' => 'El pago requiere revisión. No lo envíes de nuevo.',
      'RECHAZADO' => 'El servidor indicó que el pago no fue aplicado.',
      'EXPIRADO' => 'La solicitud venció antes de completarse.',
      _ => 'Consulta nuevamente para conocer el resultado confirmado.',
    };
  }

  String _referenceRuleMessage(Object? rule) => switch (_text(rule)) {
    'IMPORTE_EXACTO' =>
      'El importe debe coincidir con el saldo que informó el proveedor.',
    'PARCIAL_HASTA_SALDO' || 'HASTA_SALDO_SIN_POLITICA' =>
      'El importe no puede superar el saldo que informó el proveedor.',
    _ =>
      'El proveedor aún no confirma que este importe pueda autorizarse. Consulta su estado o revisa la referencia.',
  };

  String _maskedAccount(Object? value) {
    final number = _text(value);
    if (number.length <= 4) return number;
    return '•••• ${number.substring(number.length - 4)}';
  }

  String _formatDate(Object? value) {
    final text = _text(value);
    if (text.isEmpty) return '';
    final date = DateTime.tryParse(text)?.toLocal();
    if (date == null) return text;
    String two(int value) => value.toString().padLeft(2, '0');
    return '${two(date.day)}/${two(date.month)}/${date.year} ${two(date.hour)}:${two(date.minute)}';
  }

  String _text(Object? value) => value?.toString().trim() ?? '';
}

class _SectionLabel extends StatelessWidget {
  const _SectionLabel({
    required this.eyebrow,
    required this.title,
    this.description,
  });

  final String eyebrow;
  final String title;
  final String? description;

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      Text(
        eyebrow,
        style: Theme.of(context).textTheme.labelMedium?.copyWith(
          color: terracotta,
          fontWeight: FontWeight.w800,
          letterSpacing: 1.1,
        ),
      ),
      const SizedBox(height: 4),
      Text(title, style: Theme.of(context).textTheme.titleLarge),
      if (description != null) ...[
        const SizedBox(height: 4),
        Text(description!, style: Theme.of(context).textTheme.bodyMedium),
      ],
    ],
  );
}

class _MessageCard extends StatelessWidget {
  const _MessageCard({
    required this.icon,
    required this.message,
    this.isError = false,
  });

  final IconData icon;
  final String message;
  final bool isError;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(15),
    decoration: BoxDecoration(
      color: isError ? const Color(0xFFF6E6DF) : sage,
      borderRadius: BorderRadius.circular(17),
    ),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(icon, color: isError ? const Color(0xFF8F352D) : ink),
        const SizedBox(width: 11),
        Expanded(
          child: Text(message, style: Theme.of(context).textTheme.bodyMedium),
        ),
      ],
    ),
  );
}

class _EmptyStateCard extends StatelessWidget {
  const _EmptyStateCard({
    required this.icon,
    required this.title,
    required this.message,
    this.action,
  });

  final IconData icon;
  final String title;
  final String message;
  final Widget? action;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(22),
    decoration: BoxDecoration(
      color: warmSurface,
      border: Border.all(color: outlineColor),
      borderRadius: BorderRadius.circular(23),
    ),
    child: Column(
      children: [
        Icon(icon, size: 42, color: terracotta),
        const SizedBox(height: 12),
        Text(
          title,
          textAlign: TextAlign.center,
          style: Theme.of(context).textTheme.titleLarge,
        ),
        const SizedBox(height: 6),
        Text(
          message,
          textAlign: TextAlign.center,
          style: Theme.of(context).textTheme.bodyMedium,
        ),
        if (action != null) ...[const SizedBox(height: 14), action!],
      ],
    ),
  );
}

class _FixedPriceCard extends StatelessWidget {
  const _FixedPriceCard({required this.price});

  final Object? price;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
    decoration: BoxDecoration(
      color: sage,
      borderRadius: BorderRadius.circular(17),
    ),
    child: Row(
      children: [
        const Icon(Icons.payments_outlined, color: ink),
        const SizedBox(width: 12),
        Expanded(
          child: Text(
            'Importe establecido por el producto',
            style: Theme.of(context).textTheme.bodyMedium,
          ),
        ),
        Text(
          MoneyFormat.mxn(price),
          style: Theme.of(context).textTheme.titleMedium,
        ),
      ],
    ),
  );
}

class _DetailLine extends StatelessWidget {
  const _DetailLine({
    required this.label,
    required this.value,
    this.emphasis = false,
  });

  final String label;
  final String value;
  final bool emphasis;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 14),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Expanded(
          child: Text(
            label,
            style: emphasis
                ? Theme.of(context).textTheme.titleMedium
                : Theme.of(context).textTheme.bodyMedium,
          ),
        ),
        const SizedBox(width: 16),
        Flexible(
          child: Text(
            value,
            textAlign: TextAlign.end,
            style: emphasis
                ? Theme.of(context).textTheme.titleMedium
                : Theme.of(context).textTheme.bodyLarge,
          ),
        ),
      ],
    ),
  );
}
