import 'package:flutter/material.dart';

import '../services/payment_api_service.dart';
import '../theme.dart';
import '../utils/money_format.dart';
import '../widgets/management_layout.dart';
import '../widgets/status_message.dart';
import '../widgets/surfaces.dart';

/// Historial de compras exitosas (APLICADO) del cliente, de la más reciente a
/// la más antigua. El detalle de cada compra se consulta al servidor.
class PurchaseHistoryPage extends StatefulWidget {
  const PurchaseHistoryPage({
    super.key,
    required this.api,
    required this.onPay,
  });

  final PaymentApiService api;
  final VoidCallback onPay;

  @override
  State<PurchaseHistoryPage> createState() => _PurchaseHistoryPageState();
}

class _PurchaseHistoryPageState extends State<PurchaseHistoryPage> {
  static const _pageSize = 20;
  final _items = <Map<String, dynamic>>[];
  int _page = 0;
  bool _isLast = true;
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load(0);
  }

  Future<void> _load(int page) async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final data = await widget.api.request('/api/pagos/historial', {
        'pagina': page,
        'tamanio': _pageSize,
      });
      final content = data['contenido'];
      if (!mounted) return;
      setState(() {
        if (page == 0) _items.clear();
        if (content is List) {
          _items.addAll(content.whereType<Map<String, dynamic>>());
        }
        _page = page;
        // Sin compras el servidor responde codigo 0 sin datos.
        _isLast = data['ultima'] != false;
      });
    } on PaymentFailure catch (error) {
      if (mounted) setState(() => _error = error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  void _openDetail(Map<String, dynamic> purchase) => Navigator.of(context).push(
    MaterialPageRoute<void>(
      builder: (_) => PurchaseDetailPage(api: widget.api, purchase: purchase),
    ),
  );

  @override
  Widget build(BuildContext context) => ManagementLayout(
    title: 'Tus compras',
    busy: _loading,
    error: _error,
    children: [
      const Text(
        'Solo aparecen las compras que Gestopago confirmó como exitosas.',
      ),
      Align(
        alignment: Alignment.centerRight,
        child: TextButton.icon(
          onPressed: _loading ? null : () => _load(0),
          icon: const Icon(Icons.refresh_rounded),
          label: const Text('Actualizar'),
        ),
      ),
      const SizedBox(height: AppSpace.sm),
      if (!_loading && _error == null && _items.isEmpty)
        StatusMessage(
          title: 'Aún no tienes compras',
          message:
              'Cuando una compra se confirme, aparecerá aquí con su '
              'fecha, importe y referencia.',
          icon: Icons.receipt_long_outlined,
          tone: StatusTone.neutral,
          action: FilledButton.icon(
            onPressed: widget.onPay,
            icon: const Icon(Icons.shopping_bag_outlined),
            label: const Text('Pagar un servicio'),
          ),
        ),
      for (final purchase in _items)
        Padding(
          padding: const EdgeInsets.only(bottom: AppSpace.md),
          child: _PurchaseTile(
            purchase: purchase,
            onTap: () => _openDetail(purchase),
          ),
        ),
      if (!_isLast)
        OutlinedButton.icon(
          onPressed: _loading ? null : () => _load(_page + 1),
          icon: const Icon(Icons.expand_more_rounded),
          label: const Text('Ver más compras'),
        ),
    ],
  );
}

class _PurchaseTile extends StatelessWidget {
  const _PurchaseTile({required this.purchase, required this.onTap});

  final Map<String, dynamic> purchase;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final concept = purchase['concepto']?.toString() ?? 'Compra';
    final amount = MoneyFormat.mxn(purchase['monto']);
    final date = formatPurchaseDate(purchase['fecha']);
    final reference = purchase['idTransaccion']?.toString();
    // En teléfonos se omite el icono para que el nombre del producto quepa.
    final compact = MediaQuery.sizeOf(context).width < 480;
    return AppCard(
      onTap: onTap,
      padding: const EdgeInsets.all(AppSpace.lg),
      semanticLabel: 'Compra $concept, $amount, $date. Ver detalle',
      child: Row(
        children: [
          if (!compact) ...[
            const IconBadge(icon: Icons.check_circle_outline_rounded),
            const SizedBox(width: AppSpace.lg),
          ],
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  concept,
                  style: theme.textTheme.titleMedium,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
                const SizedBox(height: AppSpace.xs),
                Text(
                  date,
                  style: theme.textTheme.bodySmall?.copyWith(color: mutedInk),
                ),
                if (reference != null)
                  Text(
                    'Ref. $reference',
                    style: theme.textTheme.bodySmall?.copyWith(color: mutedInk),
                  ),
              ],
            ),
          ),
          const SizedBox(width: AppSpace.md),
          Text(
            amount,
            style: theme.textTheme.titleMedium?.copyWith(
              fontWeight: FontWeight.w700,
            ),
          ),
          const Icon(Icons.chevron_right_rounded, color: mutedInk),
        ],
      ),
    );
  }
}

/// Detalle de una compra: se vuelve a consultar al servidor para mostrar el
/// comprobante vigente (autorización, referencia, PIN si aplica).
class PurchaseDetailPage extends StatefulWidget {
  const PurchaseDetailPage({
    super.key,
    required this.api,
    required this.purchase,
  });

  final PaymentApiService api;
  final Map<String, dynamic> purchase;

  @override
  State<PurchaseDetailPage> createState() => _PurchaseDetailPageState();
}

class _PurchaseDetailPageState extends State<PurchaseDetailPage> {
  Map<String, dynamic>? _detail;
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final detail = await widget.api.request('/api/pagos/consulta', {
        'pagoId': widget.purchase['pagoId'],
      });
      if (mounted) setState(() => _detail = detail);
    } on PaymentFailure catch (error) {
      if (mounted) setState(() => _error = error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    // Mientras llega el detalle se muestra lo que ya trae el historial.
    final data = {...widget.purchase, ...?_detail};
    final theme = Theme.of(context);
    return ManagementLayout(
      title: 'Detalle de la compra',
      busy: _loading,
      error: _error,
      children: [
        ManagementCard(
          children: [
            Row(
              children: [
                const IconBadge(
                  icon: Icons.check_rounded,
                  background: successSurface,
                ),
                const SizedBox(width: AppSpace.md),
                Expanded(
                  child: Text(
                    'Compra exitosa',
                    style: theme.textTheme.titleMedium,
                  ),
                ),
              ],
            ),
            const SizedBox(height: AppSpace.lg),
            Text(
              MoneyFormat.mxn(data['monto']),
              style: theme.textTheme.headlineMedium,
            ),
            const SizedBox(height: AppSpace.xs),
            Text(data['concepto']?.toString() ?? 'Compra'),
          ],
        ),
        ManagementCard(
          children: [
            _row('Fecha y hora', formatPurchaseDate(widget.purchase['fecha'])),
            _row('Transacción', data['idTransaccion']),
            _row('Autorización', data['autorizacion']),
            _row('Cuenta', maskAccount(data['numeroCuenta'])),
            _row('Referencia', data['referencia']),
            _row('Teléfono', data['telefono']),
            _row('Folio interno', data['upc']),
            _row('PIN', data['pin']),
            _row('Instrucciones', data['leyenda']),
          ],
        ),
      ],
    );
  }

  Widget _row(String label, Object? value) {
    final text = value?.toString().trim() ?? '';
    if (text.isEmpty) return const SizedBox.shrink();
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: AppSpace.sm),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 112,
            child: Text(label, style: const TextStyle(color: mutedInk)),
          ),
          Expanded(child: SelectableText(text)),
        ],
      ),
    );
  }
}

/// Muestra solo los últimos cuatro dígitos de la cuenta: `•••• 0001`.
String? maskAccount(Object? value) {
  final text = value?.toString().trim() ?? '';
  if (text.isEmpty) return null;
  return text.length <= 4 ? text : '•••• ${text.substring(text.length - 4)}';
}

/// Fecha ISO del servidor en hora local: `15/11/2026 10:32`.
String formatPurchaseDate(Object? value) {
  final date = value is String ? DateTime.tryParse(value)?.toLocal() : null;
  if (date == null) return 'Fecha no disponible';
  String two(int n) => n.toString().padLeft(2, '0');
  return '${two(date.day)}/${two(date.month)}/${date.year} '
      '${two(date.hour)}:${two(date.minute)}';
}
