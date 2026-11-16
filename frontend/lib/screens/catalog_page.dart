import 'package:flutter/material.dart';

import '../models/catalog.dart';
import '../models/login_session.dart';
import '../services/payment_api_service.dart';
import '../theme.dart';
import '../widgets/paper_background.dart';
import '../widgets/status_message.dart';
import '../widgets/surfaces.dart';
import 'payment_page.dart';

/// Catálogo de Gestopago: categorías, búsqueda y servicios con sus productos.
/// Comprar abre el pago con el producto ya elegido.
class CatalogPage extends StatefulWidget {
  const CatalogPage({
    super.key,
    required this.session,
    required this.api,
    this.initialSearch,
    this.pendingStore,
  });

  final LoginSession session;
  final PaymentApiService api;
  final String? initialSearch;
  final PendingPaymentStore? pendingStore;

  @override
  State<CatalogPage> createState() => _CatalogPageState();
}

class _CatalogPageState extends State<CatalogPage> {
  late final _search = TextEditingController(text: widget.initialSearch ?? '');
  late final PendingPaymentStore _pending =
      widget.pendingStore ?? const PendingPaymentStore();
  List<CatalogService> _services = const [];
  String? _category;
  bool _loading = true;
  bool _hasPending = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    await _checkPending();
    try {
      final response = await widget.api.request('/api/products');
      final container = response['productos'];
      final items = container is Map ? container['productos'] : null;
      final products = (items is List ? items : const [])
          .whereType<Map<String, dynamic>>()
          .toList(growable: false);
      if (mounted) setState(() => _services = CatalogService.group(products));
    } on PaymentFailure catch (error) {
      if (mounted) setState(() => _error = error.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _checkPending() async {
    try {
      final id = await _pending.read(widget.session.userId);
      if (mounted) setState(() => _hasPending = id != null);
    } catch (_) {
      // Sin almacenamiento seguro no se puede saber; el pago lo vuelve a revisar.
    }
  }

  List<CatalogService> get _visible {
    final query = _normalize(_search.text);
    return _services
        .where((service) {
          if (_category != null && service.category.key != _category) {
            return false;
          }
          if (query.isEmpty) return true;
          return _normalize(service.name).contains(query) ||
              service.products.any(
                (p) => _normalize(textOf(p['producto'])).contains(query),
              );
        })
        .toList(growable: false);
  }

  List<CatalogCategory> get _categories {
    final present = _services.map((s) => s.category.key).toSet();
    return [
      ...CatalogCategory.all.where((c) => present.contains(c.key)),
      if (present.contains(CatalogCategory.other.key)) CatalogCategory.other,
    ];
  }

  Future<void> _openPayment({int? productId}) async {
    await Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => PaymentPage(
          session: widget.session,
          api: widget.api,
          pendingStore: widget.pendingStore,
          initialProductId: productId,
        ),
      ),
    );
    await _checkPending();
  }

  void _openService(CatalogService service) => Navigator.of(context).push(
    MaterialPageRoute<void>(
      builder: (_) => ServiceProductsPage(
        service: service,
        onBuy: (product) => _openPayment(productId: product['idProducto']),
      ),
    ),
  );

  @override
  Widget build(BuildContext context) {
    final visible = _visible;
    final width = MediaQuery.sizeOf(context).width;
    final phone = width < Breakpoints.medium;
    return Scaffold(
      appBar: AppBar(title: const Text('Catálogo')),
      body: PaperBackground(
        child: SafeArea(
          child: RefreshIndicator(
            onRefresh: _load,
            child: CustomScrollView(
              slivers: [
                SliverPadding(
                  padding: const EdgeInsets.fromLTRB(24, 12, 24, 8),
                  sliver: SliverToBoxAdapter(
                    child: _centered(
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          Text(
                            '¿Qué quieres pagar hoy?',
                            style: Theme.of(context).textTheme.headlineMedium,
                          ),
                          const SizedBox(height: AppSpace.lg),
                          if (_hasPending) ...[
                            StatusMessage(
                              icon: Icons.hourglass_top_rounded,
                              message:
                                  'Tienes un pago en proceso. Revisa su estado '
                                  'antes de hacer otra compra.',
                              action: TextButton(
                                onPressed: () => _openPayment(),
                                child: const Text('Ver estado'),
                              ),
                            ),
                            const SizedBox(height: AppSpace.lg),
                          ],
                          TextField(
                            key: const ValueKey('catalog-search'),
                            controller: _search,
                            onChanged: (_) => setState(() {}),
                            textInputAction: TextInputAction.search,
                            decoration: InputDecoration(
                              prefixIcon: const Icon(Icons.search_rounded),
                              hintText: 'Busca Telcel, CFE, Amazon…',
                              suffixIcon: _search.text.isEmpty
                                  ? null
                                  : IconButton(
                                      tooltip: 'Borrar búsqueda',
                                      icon: const Icon(Icons.close_rounded),
                                      onPressed: () => setState(_search.clear),
                                    ),
                            ),
                          ),
                          const SizedBox(height: AppSpace.md),
                          if (_categories.isNotEmpty) _categoryChips(),
                          if (_loading) ...[
                            const SizedBox(height: AppSpace.lg),
                            const StatusMessage.loading(
                              message: 'Cargando el catálogo',
                            ),
                          ],
                          if (_error != null) ...[
                            const SizedBox(height: AppSpace.lg),
                            StatusMessage(
                              message: _error!,
                              icon: Icons.error_outline_rounded,
                              tone: StatusTone.error,
                              action: TextButton(
                                onPressed: _load,
                                child: const Text('Reintentar'),
                              ),
                            ),
                          ],
                          if (!_loading && _error == null && visible.isEmpty)
                            Padding(
                              padding: const EdgeInsets.only(top: AppSpace.lg),
                              child: StatusMessage(
                                title: _services.isEmpty
                                    ? 'Catálogo sin productos'
                                    : 'Sin resultados',
                                message: _services.isEmpty
                                    ? 'Gestopago no devolvió productos por ahora.'
                                    : 'Prueba con otro nombre o categoría.',
                                icon: Icons.search_off_rounded,
                                tone: StatusTone.neutral,
                              ),
                            ),
                          if (visible.isNotEmpty)
                            Padding(
                              padding: const EdgeInsets.only(top: AppSpace.md),
                              child: Text(
                                '${visible.length} '
                                '${visible.length == 1 ? 'servicio' : 'servicios'}',
                                style: Theme.of(context).textTheme.labelLarge
                                    ?.copyWith(color: mutedInk),
                              ),
                            ),
                        ],
                      ),
                    ),
                  ),
                ),
                SliverPadding(
                  padding: EdgeInsets.fromLTRB(
                    _sidePadding(width),
                    8,
                    _sidePadding(width),
                    32,
                  ),
                  sliver: SliverGrid.builder(
                    // Teléfono: una tarjeta por fila para que el precio no se corte.
                    gridDelegate: SliverGridDelegateWithMaxCrossAxisExtent(
                      maxCrossAxisExtent: phone ? 640 : 260,
                      mainAxisExtent: phone ? 108 : 176,
                      crossAxisSpacing: AppSpace.md,
                      mainAxisSpacing: AppSpace.md,
                    ),
                    itemCount: visible.length,
                    itemBuilder: (context, index) => _ServiceCard(
                      service: visible[index],
                      horizontal: phone,
                      onTap: () => _openService(visible[index]),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  /// Mismo ancho máximo que el encabezado, centrado en pantallas grandes.
  static double _sidePadding(double width) =>
      width > ContentWidth.wide + 48 ? (width - ContentWidth.wide) / 2 : 24;

  Widget _centered(Widget child) => Center(
    child: ConstrainedBox(
      constraints: const BoxConstraints(maxWidth: ContentWidth.wide),
      child: child,
    ),
  );

  Widget _categoryChips() => SingleChildScrollView(
    scrollDirection: Axis.horizontal,
    child: Row(
      children: [
        Padding(
          padding: const EdgeInsets.only(right: AppSpace.sm),
          child: ChoiceChip(
            label: const Text('Todo'),
            selected: _category == null,
            onSelected: (_) => setState(() => _category = null),
          ),
        ),
        for (final category in _categories)
          Padding(
            padding: const EdgeInsets.only(right: AppSpace.sm),
            child: ChoiceChip(
              avatar: Icon(category.icon, size: 18),
              label: Text(category.label),
              selected: _category == category.key,
              onSelected: (selected) =>
                  setState(() => _category = selected ? category.key : null),
            ),
          ),
      ],
    ),
  );
}

String _normalize(String text) {
  const accents = {'á': 'a', 'é': 'e', 'í': 'i', 'ó': 'o', 'ú': 'u', 'ü': 'u'};
  return text.toLowerCase().split('').map((c) => accents[c] ?? c).join().trim();
}

/// Imagen representativa: Gestopago no envía logotipos, así que se usa el
/// icono de la categoría con las iniciales del servicio.
class ServiceAvatar extends StatelessWidget {
  const ServiceAvatar({super.key, required this.service, this.size = 52});

  final CatalogService service;
  final double size;

  @override
  Widget build(BuildContext context) {
    final words = service.name
        .split(RegExp(r'[^A-Za-z0-9ÁÉÍÓÚÑáéíóúñ]+'))
        .where((w) => w.isNotEmpty)
        .toList();
    final initials = words.isEmpty
        ? '?'
        : words.take(2).map((w) => w[0].toUpperCase()).join();
    return ExcludeSemantics(
      child: SizedBox(
        width: size,
        height: size,
        child: Stack(
          clipBehavior: Clip.none,
          children: [
            Container(
              width: size,
              height: size,
              alignment: Alignment.center,
              decoration: BoxDecoration(
                color: inkRaised,
                borderRadius: BorderRadius.circular(AppRadius.sm),
              ),
              child: Text(
                initials,
                style: TextStyle(
                  color: paperHighlight,
                  fontWeight: FontWeight.w800,
                  fontSize: size * 0.34,
                ),
              ),
            ),
            Positioned(
              right: -6,
              bottom: -6,
              child: Container(
                padding: const EdgeInsets.all(4),
                decoration: BoxDecoration(
                  color: terracottaSoft,
                  shape: BoxShape.circle,
                  border: Border.all(color: warmSurface, width: 2),
                ),
                child: Icon(service.category.icon, size: 14, color: terracotta),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _ServiceCard extends StatelessWidget {
  const _ServiceCard({
    required this.service,
    required this.onTap,
    this.horizontal = false,
  });

  final CatalogService service;
  final VoidCallback onTap;

  /// Teléfono: icono a la izquierda y textos a todo lo ancho.
  final bool horizontal;

  @override
  Widget build(BuildContext context) {
    final details = _details(context);
    return AppCard(
      onTap: onTap,
      padding: const EdgeInsets.all(AppSpace.lg),
      semanticLabel:
          '${service.name}, ${service.category.label}, ${service.priceSummary}'
          '${service.purchasable ? '' : ', solo consulta'}. Ver productos',
      child: horizontal
          ? Row(
              children: [
                ServiceAvatar(service: service, size: 48),
                const SizedBox(width: AppSpace.lg),
                Expanded(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: details,
                  ),
                ),
                const Icon(Icons.chevron_right_rounded, color: mutedInk),
              ],
            )
          : Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                ServiceAvatar(service: service, size: 44),
                const Spacer(),
                ...details,
              ],
            ),
    );
  }

  List<Widget> _details(BuildContext context) {
    final theme = Theme.of(context);
    final count = service.products.length;
    return [
      Text(
        service.name,
        style: theme.textTheme.titleMedium,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
      Text(
        '${service.category.label} · $count '
        '${count == 1 ? 'producto' : 'productos'}',
        style: theme.textTheme.bodySmall?.copyWith(color: mutedInk),
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
      const SizedBox(height: AppSpace.xs),
      if (service.purchasable)
        Text(
          service.priceSummary,
          style: theme.textTheme.labelLarge?.copyWith(
            color: terracotta,
            fontWeight: FontWeight.w700,
          ),
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
        )
      else
        const _Badge('Solo consulta'),
    ];
  }
}

class _Badge extends StatelessWidget {
  const _Badge(this.text);
  final String text;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
    decoration: BoxDecoration(
      color: sage,
      borderRadius: BorderRadius.circular(99),
    ),
    child: Text(
      text,
      style: const TextStyle(
        fontSize: 12,
        color: mutedInk,
        fontWeight: FontWeight.w600,
      ),
    ),
  );
}

/// Productos de un servicio con precio y disponibilidad.
class ServiceProductsPage extends StatelessWidget {
  const ServiceProductsPage({
    super.key,
    required this.service,
    required this.onBuy,
  });

  final CatalogService service;
  final ValueChanged<Map<String, dynamic>> onBuy;

  Future<void> _showDetail(
    BuildContext context,
    Map<String, dynamic> product,
  ) async {
    final buy = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      backgroundColor: warmSurface,
      constraints: const BoxConstraints(maxWidth: 640),
      builder: (context) =>
          ProductDetailSheet(service: service, product: product),
    );
    if (buy == true) onBuy(product);
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(service.name)),
      body: PaperBackground(
        child: SafeArea(
          child: ListView(
            padding: const EdgeInsets.fromLTRB(24, 12, 24, 32),
            children: [
              Center(
                child: ConstrainedBox(
                  constraints: const BoxConstraints(
                    maxWidth: ContentWidth.page,
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Row(
                        children: [
                          ServiceAvatar(service: service, size: 60),
                          const SizedBox(width: AppSpace.xl),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  service.name,
                                  style: theme.textTheme.headlineSmall,
                                ),
                                Text(
                                  service.category.label,
                                  style: theme.textTheme.bodyMedium?.copyWith(
                                    color: mutedInk,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: AppSpace.xl),
                      for (final product in service.products)
                        Padding(
                          padding: const EdgeInsets.only(bottom: AppSpace.md),
                          child: _ProductTile(
                            product: product,
                            onTap: () => _showDetail(context, product),
                          ),
                        ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _ProductTile extends StatelessWidget {
  const _ProductTile({required this.product, required this.onTap});

  final Map<String, dynamic> product;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final available = isPurchasable(product);
    final name = textOf(product['producto']);
    final price = priceLabel(product);
    return AppCard(
      onTap: onTap,
      padding: const EdgeInsets.all(AppSpace.lg),
      semanticLabel: '$name, $price. Ver detalle',
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(name, style: theme.textTheme.titleMedium),
                const SizedBox(height: AppSpace.xs),
                Text(
                  price,
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: available ? terracotta : mutedInk,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ],
            ),
          ),
          if (!available) const _Badge('Solo consulta'),
          const Icon(Icons.chevron_right_rounded, color: mutedInk),
        ],
      ),
    );
  }
}

/// Detalle de un producto: precio, qué se necesita, descripción y comprar.
class ProductDetailSheet extends StatelessWidget {
  const ProductDetailSheet({
    super.key,
    required this.service,
    required this.product,
  });

  final CatalogService service;
  final Map<String, dynamic> product;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final available = isPurchasable(product);
    return SafeArea(
      child: ConstrainedBox(
        constraints: BoxConstraints(
          maxHeight: MediaQuery.sizeOf(context).height * 0.85,
        ),
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(24, 0, 24, 24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                children: [
                  ServiceAvatar(service: service, size: 48),
                  const SizedBox(width: AppSpace.lg),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          textOf(product['producto']),
                          style: theme.textTheme.titleLarge,
                        ),
                        Text(
                          '${service.name} · ${service.category.label}',
                          style: theme.textTheme.bodySmall?.copyWith(
                            color: mutedInk,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: AppSpace.xl),
              Text(
                priceLabel(product),
                style: theme.textTheme.headlineMedium?.copyWith(
                  color: available ? ink : mutedInk,
                ),
              ),
              if (available && hasFixedPrice(product))
                Text(
                  'Precio final, sin comisión adicional.',
                  style: theme.textTheme.bodySmall?.copyWith(color: mutedInk),
                ),
              const SizedBox(height: AppSpace.lg),
              StatusMessage(
                icon: available
                    ? Icons.info_outline_rounded
                    : Icons.block_rounded,
                tone: available ? StatusTone.info : StatusTone.neutral,
                message: requirementLabel(product),
              ),
              const SizedBox(height: AppSpace.lg),
              Text('Descripción', style: theme.textTheme.titleSmall),
              const SizedBox(height: AppSpace.xs),
              Text(descriptionOf(product)),
              const SizedBox(height: AppSpace.xl),
              FilledButton.icon(
                onPressed: available
                    ? () => Navigator.of(context).pop(true)
                    : null,
                style: FilledButton.styleFrom(
                  minimumSize: const Size.fromHeight(52),
                ),
                icon: const Icon(Icons.shopping_bag_outlined),
                label: Text(available ? 'Comprar' : 'No disponible'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
