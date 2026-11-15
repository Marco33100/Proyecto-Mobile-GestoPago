import 'package:flutter/material.dart';
import '../services/admin_api_service.dart';
import '../services/payment_api_service.dart';
import '../utils/money_format.dart';
import '../widgets/management_layout.dart';

final _uuidPattern = RegExp(
  r'^[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$',
);

class ReconciliationPage extends StatefulWidget {
  const ReconciliationPage({super.key, required this.api});
  final AdminApiService api;
  @override
  State<ReconciliationPage> createState() => _ReconciliationPageState();
}

class _ReconciliationPageState extends State<ReconciliationPage> {
  final _proposal = TextEditingController();
  final _form = GlobalKey<FormState>();
  AdminPageResult? _result;
  String? _error;
  bool _busy = false;
  @override
  void initState() {
    super.initState();
    _load(0);
  }

  @override
  void dispose() {
    _proposal.dispose();
    super.dispose();
  }

  Future<void> _load(int page) async {
    if (_busy) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final data = await widget.api.search('conciliacion', {}, page);
      if (mounted) setState(() => _result = data);
    } catch (e) {
      if (mounted) {
        setState(
          () => _error = e is PaymentFailure
              ? e.message
              : 'No se pudo consultar la conciliación.',
        );
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _openProposal() async {
    if (!_form.currentState!.validate()) return;
    await Navigator.push<void>(
      context,
      MaterialPageRoute(
        builder: (_) => ProposalReviewPage(
          api: widget.api,
          proposalId: _proposal.text.trim(),
        ),
      ),
    );
    if (mounted) await _load(0);
  }

  @override
  Widget build(BuildContext context) => ManagementLayout(
    title: 'Conciliación',
    busy: _busy,
    error: _error,
    children: [
      const Text(
        'Solo operadores autorizados. Una persona propone y otra revisa la evidencia y aprueba. Estas acciones no vuelven a enviar el pago.',
      ),
      const SizedBox(height: 18),
      Form(
        key: _form,
        child: Column(
          children: [
            TextFormField(
              key: const ValueKey('proposal-id'),
              controller: _proposal,
              enabled: !_busy,
              decoration: const InputDecoration(
                labelText: 'Identificador de propuesta',
              ),
              validator: (value) => _uuidPattern.hasMatch(value?.trim() ?? '')
                  ? null
                  : 'Escribe el identificador completo de la propuesta',
            ),
            const SizedBox(height: 12),
            OutlinedButton.icon(
              onPressed: _busy ? null : _openProposal,
              icon: const Icon(Icons.search),
              label: const Text('Revisar propuesta'),
            ),
          ],
        ),
      ),
      const SizedBox(height: 26),
      Row(
        children: [
          Expanded(
            child: Text(
              'Pagos en revisión',
              style: Theme.of(context).textTheme.titleLarge,
            ),
          ),
          IconButton(
            tooltip: 'Actualizar pendientes',
            onPressed: _busy ? null : () => _load(_result?.page ?? 0),
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      if (_result case final result?) ...[
        if (result.items.isEmpty)
          const Padding(
            padding: EdgeInsets.symmetric(vertical: 22),
            child: Text('No hay pagos en revisión.'),
          ),
        for (final item in result.items)
          ManagementCard(
            children: [
              SelectableText('Pago: ${item['pagoId']}'),
              Text('Importe: ${MoneyFormat.mxn(item['monto'])}'),
              SelectableText('UPC: ${item['upc']}'),
              Text(
                'En revisión desde ${item['enviadoEn'] ?? 'fecha no disponible'}',
              ),
              const SizedBox(height: 12),
              TextButton.icon(
                onPressed: _busy
                    ? null
                    : () async {
                        await Navigator.push<void>(
                          context,
                          MaterialPageRoute(
                            builder: (_) => ProposalCreatePage(
                              api: widget.api,
                              payment: item,
                            ),
                          ),
                        );
                        if (mounted) await _load(result.page);
                      },
                icon: const Icon(Icons.note_add_outlined),
                label: const Text('Registrar propuesta'),
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
    ],
  );
}

class ProposalCreatePage extends StatefulWidget {
  const ProposalCreatePage({
    super.key,
    required this.api,
    required this.payment,
  });
  final AdminApiService api;
  final Map<String, dynamic> payment;
  @override
  State<ProposalCreatePage> createState() => _ProposalCreatePageState();
}

class _ProposalCreatePageState extends State<ProposalCreatePage> {
  final _form = GlobalKey<FormState>();
  final _fields = <String, TextEditingController>{};
  final _id = PendingPaymentStore.newId();
  String _result = 'APLICADO';
  bool _verified = false;
  bool _busy = false;
  bool _uncertain = false;
  String? _error;
  @override
  void dispose() {
    for (final c in _fields.values) {
      c.dispose();
    }
    super.dispose();
  }

  Widget _field(
    String key,
    String label,
    int min,
    int max, {
    bool numeric = false,
    int lines = 1,
  }) => Padding(
    padding: const EdgeInsets.only(bottom: 14),
    child: TextFormField(
      key: ValueKey('proposal-$key'),
      controller: _fields.putIfAbsent(key, TextEditingController.new),
      enabled: !_busy && !_uncertain,
      maxLength: max,
      minLines: lines,
      maxLines: lines == 1 ? 1 : lines + 3,
      keyboardType: numeric ? TextInputType.number : TextInputType.multiline,
      decoration: InputDecoration(labelText: label),
      validator: (value) {
        final text = value?.trim() ?? '';
        if (text.length < min) return 'Escribe al menos $min caracteres';
        if (numeric &&
            (!RegExp(r'^\d+$').hasMatch(text) ||
                BigInt.parse(text) <= BigInt.zero)) {
          return 'Escribe un número positivo';
        }
        return null;
      },
    ),
  );

  Future<void> _submit() async {
    if (_busy || _uncertain || !_form.currentState!.validate()) return;
    if (!_verified) {
      setState(
        () => _error = 'Confirma que revisaste la evidencia con el proveedor.',
      );
      return;
    }
    final confirmed = await confirmManagementAction(
      context,
      'Registrar propuesta',
      'Se registrará la evidencia para que otro operador la revise. El saldo todavía no cambiará.',
    );
    if (!confirmed || !mounted || _busy) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    final payment = widget.payment;
    try {
      await widget.api.propose({
        'propuestaId': _id,
        'pagoId': payment['pagoId'],
        'versionPago': payment['versionPago'],
        'upc': payment['upc'],
        'monto': payment['monto'].toString(),
        'resultado': _result,
        if (_result == 'APLICADO') ...{
          'idTransaccion': _fields['idTransaccion']!.text.trim(),
          'autorizacion': _fields['autorizacion']!.text.trim(),
        },
        'folioProveedor': _fields['folioProveedor']!.text.trim(),
        'evidenciaProveedor': _fields['evidenciaProveedor']!.text.trim(),
        'motivo': _fields['motivo']!.text.trim(),
      });
      if (!mounted) return;
      await Navigator.push<void>(
        context,
        MaterialPageRoute(
          builder: (_) => ProposalReviewPage(api: widget.api, proposalId: _id),
        ),
      );
      if (mounted) Navigator.pop(context);
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = e is PaymentFailure
              ? e.message
              : 'No se pudo confirmar el registro. Consulta la propuesta antes de repetirlo.';
          _uncertain =
              e is! PaymentFailure || e.status == null || e.status! >= 500;
        });
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) => ManagementLayout(
    title: 'Nueva propuesta',
    busy: _busy,
    error: _error,
    children: [
      ManagementCard(
        children: [
          SelectableText('Pago: ${widget.payment['pagoId']}'),
          Text('Importe: ${MoneyFormat.mxn(widget.payment['monto'])}'),
          SelectableText('UPC: ${widget.payment['upc']}'),
          const SizedBox(height: 8),
          SelectableText('Propuesta: $_id'),
        ],
      ),
      const Text(
        'Conserva el identificador de la propuesta. Usa evidencia oficial verificada; un timeout no demuestra que el pago haya fallado.',
      ),
      const SizedBox(height: 18),
      Form(
        key: _form,
        child: Column(
          children: [
            DropdownButtonFormField<String>(
              initialValue: _result,
              decoration: const InputDecoration(
                labelText: 'Resultado confirmado',
              ),
              items: const [
                DropdownMenuItem(
                  value: 'APLICADO',
                  child: Text('Pago aplicado'),
                ),
                DropdownMenuItem(
                  value: 'NO_APLICADO_DEFINITIVO',
                  child: Text('No aplicado definitivamente'),
                ),
                DropdownMenuItem(
                  value: 'DUPLICADO_YA_CONTABILIZADO',
                  child: Text('Duplicado ya contabilizado'),
                ),
              ],
              onChanged: _busy || _uncertain
                  ? null
                  : (value) => setState(() => _result = value!),
            ),
            const SizedBox(height: 14),
            if (_result == 'APLICADO') ...[
              _field(
                'idTransaccion',
                'Transacción del proveedor',
                1,
                60,
                numeric: true,
              ),
              _field(
                'autorizacion',
                'Autorización del proveedor',
                1,
                60,
                numeric: true,
              ),
            ],
            if (_result == 'NO_APLICADO_DEFINITIVO')
              const Padding(
                padding: EdgeInsets.only(bottom: 14),
                child: Text(
                  'La evidencia debe confirmar que no se aplicó y que no puede aplicarse posteriormente.',
                ),
              ),
            _field('folioProveedor', 'Folio del proveedor', 1, 100),
            _field(
              'evidenciaProveedor',
              'Evidencia verificada con el proveedor',
              20,
              16000,
              lines: 4,
            ),
            _field('motivo', 'Motivo de la resolución', 10, 1000, lines: 2),
            CheckboxListTile(
              contentPadding: EdgeInsets.zero,
              value: _verified,
              title: const Text(
                'Verifiqué la autenticidad y el resultado con el proveedor.',
              ),
              onChanged: _busy || _uncertain
                  ? null
                  : (value) => setState(() => _verified = value ?? false),
            ),
            FilledButton(
              onPressed: _busy || _uncertain ? null : _submit,
              child: const Text('Registrar propuesta'),
            ),
          ],
        ),
      ),
      if (_uncertain) ...[
        const SizedBox(height: 12),
        OutlinedButton(
          onPressed: _busy
              ? null
              : () => Navigator.push<void>(
                  context,
                  MaterialPageRoute(
                    builder: (_) =>
                        ProposalReviewPage(api: widget.api, proposalId: _id),
                  ),
                ),
          child: const Text('Consultar esta propuesta'),
        ),
      ],
    ],
  );
}

class ProposalReviewPage extends StatefulWidget {
  const ProposalReviewPage({
    super.key,
    required this.api,
    required this.proposalId,
  });
  final AdminApiService api;
  final String proposalId;
  @override
  State<ProposalReviewPage> createState() => _ProposalReviewPageState();
}

class _ProposalReviewPageState extends State<ProposalReviewPage> {
  final _form = GlobalKey<FormState>();
  final _observation = TextEditingController();
  Map<String, dynamic>? _proposal;
  bool _verified = false;
  bool _decisionUncertain = false;
  bool _busy = false;
  String? _error;
  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _observation.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    if (_busy) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final proposal = await widget.api.proposal(widget.proposalId);
      if (mounted) {
        setState(() {
          _proposal = proposal;
          _decisionUncertain = false;
          _verified = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(
          () => _error = e is PaymentFailure
              ? e.message
              : 'No se pudo consultar la propuesta.',
        );
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _decide(bool approve) async {
    if (_busy || _decisionUncertain || !_form.currentState!.validate()) return;
    if (!_verified) {
      setState(
        () => _error = 'Confirma que revisaste la evidencia antes de decidir.',
      );
      return;
    }
    final confirmed = await confirmManagementAction(
      context,
      approve ? 'Aprobar resolución' : 'Rechazar propuesta',
      approve
          ? 'Esta decisión puede confirmar el cargo o liberar la retención según el resultado propuesto. ¿Confirmas la evidencia y la resolución?'
          : 'El pago conservará su estado y su retención. ¿Deseas rechazar esta propuesta?',
    );
    if (!confirmed || !mounted || _busy) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final proposal = await widget.api.decide(
        widget.proposalId,
        _observation.text.trim(),
        approve: approve,
      );
      if (mounted) {
        setState(() {
          _proposal = proposal;
          _verified = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _decisionUncertain =
              e is! PaymentFailure || e.status == null || e.status! >= 500;
          _error = e is PaymentFailure
              ? e.message
              : 'No se confirmó la decisión. Consulta la propuesta antes de repetirla.';
        });
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final proposal = _proposal;
    final own = proposal?['propuestoPor'] == widget.api.session.userId;
    final pending = proposal?['estado'] == 'PROPUESTA';
    return ManagementLayout(
      title: 'Revisar propuesta',
      busy: _busy,
      error: _error,
      children: [
        SelectableText('Propuesta: ${widget.proposalId}'),
        TextButton.icon(
          onPressed: _busy ? null : _load,
          icon: const Icon(Icons.refresh),
          label: const Text('Actualizar propuesta'),
        ),
        if (_decisionUncertain)
          const Text(
            'El resultado de la decisión es incierto. Actualiza la propuesta antes de intentar otra decisión.',
          ),
        if (proposal != null) ...[
          ManagementCard(
            children: [
              Text(
                'Estado: ${proposal['estado']}',
                style: Theme.of(context).textTheme.titleLarge,
              ),
              Text('Resultado: ${proposal['resultado']}'),
              SelectableText('Pago: ${proposal['pagoId']}'),
              SelectableText('Propuesto por: ${proposal['propuestoPor']}'),
              Text('Vence: ${proposal['venceEn']}'),
            ],
          ),
          if (proposal['detalle'] case final Map detail)
            ManagementCard(
              children: [
                Text(
                  'Evidencia para revisión',
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                const SizedBox(height: 10),
                for (final field in [
                  ('upc', 'UPC'),
                  ('monto', 'Importe'),
                  ('idTransaccion', 'Transacción'),
                  ('autorizacion', 'Autorización'),
                  ('folioProveedor', 'Folio'),
                  ('evidenciaProveedor', 'Evidencia'),
                  ('motivo', 'Motivo'),
                ])
                  if (detail[field.$1] != null)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 12),
                      child: SelectableText('${field.$2}: ${detail[field.$1]}'),
                    ),
              ],
            ),
          if (pending)
            Form(
              key: _form,
              child: Column(
                children: [
                  if (own)
                    const Padding(
                      padding: EdgeInsets.only(bottom: 14),
                      child: Text(
                        'Creaste esta propuesta. Otro ejecutivo autorizado debe aprobarla. Puedes retirarla mediante Rechazar propuesta.',
                      ),
                    ),
                  TextFormField(
                    key: const ValueKey('decision-observation'),
                    controller: _observation,
                    enabled: !_busy,
                    decoration: const InputDecoration(
                      labelText: 'Observación de la decisión',
                    ),
                    maxLength: 1000,
                    minLines: 2,
                    maxLines: 5,
                    validator: (value) => (value?.trim().length ?? 0) < 10
                        ? 'Escribe al menos diez caracteres'
                        : null,
                  ),
                  CheckboxListTile(
                    contentPadding: EdgeInsets.zero,
                    value: _verified,
                    title: const Text(
                      'Revisé la evidencia oficial y la resolución propuesta.',
                    ),
                    onChanged: _busy
                        ? null
                        : (value) => setState(() => _verified = value ?? false),
                  ),
                  FilledButton(
                    onPressed: _busy || own || _decisionUncertain
                        ? null
                        : () => _decide(true),
                    child: const Text('Aprobar resolución'),
                  ),
                  const SizedBox(height: 12),
                  OutlinedButton(
                    onPressed: _busy || _decisionUncertain
                        ? null
                        : () => _decide(false),
                    child: const Text('Rechazar propuesta'),
                  ),
                ],
              ),
            ),
        ],
      ],
    );
  }
}
