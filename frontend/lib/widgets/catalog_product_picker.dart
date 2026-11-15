import 'package:flutter/material.dart';
import '../theme.dart';

/// Un solo selector: el filtro pertenece al catálogo, no al formulario de pago.
class CatalogProductPicker extends StatelessWidget {
  const CatalogProductPicker({
    super.key,
    required this.products,
    required this.selected,
    required this.onChanged,
    this.enabled = true,
    this.initialSearch = '',
  });
  final List<Map<String, dynamic>> products;
  final Map<String, dynamic>? selected;
  final ValueChanged<Map<String, dynamic>> onChanged;
  final bool enabled;
  final String initialSearch;

  @override
  Widget build(BuildContext context) => FormField<Map<String, dynamic>>(
    key: ObjectKey(selected),
    initialValue: products.contains(selected) ? selected : null,
    enabled: enabled,
    validator: (value) =>
        value == null ? 'Selecciona un producto del catálogo' : null,
    builder: (field) => Semantics(
      button: true,
      child: InkWell(
        key: const ValueKey('product-selector'),
        borderRadius: BorderRadius.circular(18),
        onTap: !enabled
            ? null
            : () async {
                final choice = await showModalBottomSheet<Map<String, dynamic>>(
                  context: context,
                  isScrollControlled: true,
                  useSafeArea: true,
                  showDragHandle: MediaQuery.sizeOf(context).height >= 500,
                  backgroundColor: paper,
                  constraints: const BoxConstraints(maxWidth: 620),
                  builder: (_) => _CatalogSheet(
                    products: products,
                    selected: selected,
                    initialSearch: initialSearch,
                  ),
                );
                if (choice != null && field.mounted) {
                  field.didChange(choice);
                  onChanged(choice);
                }
              },
        child: InputDecorator(
          isEmpty: false,
          decoration: InputDecoration(
            labelText: 'Producto disponible',
            enabled: enabled,
            errorText: field.errorText,
            prefixIcon: const Icon(Icons.receipt_long_outlined),
            suffixIcon: const Icon(Icons.expand_more_rounded),
          ),
          child: Text(
            field.value == null
                ? 'Abrir catálogo'
                : '${field.value!['servicio']} · ${field.value!['producto']}',
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
          ),
        ),
      ),
    ),
  );
}

class _CatalogSheet extends StatefulWidget {
  const _CatalogSheet({
    required this.products,
    required this.selected,
    required this.initialSearch,
  });
  final List<Map<String, dynamic>> products;
  final Map<String, dynamic>? selected;
  final String initialSearch;
  @override
  State<_CatalogSheet> createState() => _CatalogSheetState();
}

class _CatalogSheetState extends State<_CatalogSheet> {
  late final _search = TextEditingController(text: widget.initialSearch);
  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final query = _search.text.trim().toLowerCase();
    final results = widget.products
        .where(
          (product) =>
              '${product['servicio'] ?? ''} ${product['producto'] ?? ''}'
                  .toLowerCase()
                  .contains(query),
        )
        .toList(growable: false);
    return FractionallySizedBox(
      heightFactor: MediaQuery.sizeOf(context).height < 500 ? 1 : 0.9,
      child: Padding(
        padding: EdgeInsets.only(
          bottom: MediaQuery.viewInsetsOf(context).bottom,
        ),
        child: LayoutBuilder(
          builder: (context, constraints) {
            final compact = constraints.maxHeight < 230;
            return Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                if (!compact)
                  Padding(
                    padding: const EdgeInsets.fromLTRB(24, 0, 12, 12),
                    child: Row(
                      children: [
                        Expanded(
                          child: Text(
                            'Elige un producto',
                            style: Theme.of(context).textTheme.titleLarge,
                          ),
                        ),
                        IconButton(
                          tooltip: 'Cerrar catálogo',
                          onPressed: () => Navigator.pop(context),
                          icon: const Icon(Icons.close),
                        ),
                      ],
                    ),
                  ),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 24),
                  child: Row(
                    children: [
                      Expanded(
                        child: TextField(
                          key: const ValueKey('catalog-search'),
                          controller: _search,
                          textInputAction: TextInputAction.search,
                          onChanged: (_) => setState(() {}),
                          decoration: InputDecoration(
                            labelText: 'Buscar servicio o producto',
                            prefixIcon: const Icon(Icons.search_rounded),
                            suffixIcon: query.isEmpty
                                ? null
                                : IconButton(
                                    tooltip: 'Limpiar búsqueda',
                                    onPressed: () => setState(_search.clear),
                                    icon: const Icon(Icons.close),
                                  ),
                          ),
                        ),
                      ),
                      if (compact)
                        IconButton(
                          tooltip: 'Cerrar catálogo',
                          onPressed: () => Navigator.pop(context),
                          icon: const Icon(Icons.close),
                        ),
                    ],
                  ),
                ),
                if (!compact)
                  Padding(
                    padding: const EdgeInsets.fromLTRB(24, 12, 24, 8),
                    child: Text(
                      '${results.length} ${results.length == 1 ? 'producto' : 'productos'}',
                    ),
                  ),
                Expanded(
                  child: results.isEmpty
                      ? ListView(
                          children: [
                            Padding(
                              padding: const EdgeInsets.all(24),
                              child: Text(
                                'No hay coincidencias. Prueba otro nombre o limpia la búsqueda.',
                                style: Theme.of(context).textTheme.bodyLarge,
                                textAlign: TextAlign.center,
                              ),
                            ),
                          ],
                        )
                      : ListView.separated(
                          keyboardDismissBehavior:
                              ScrollViewKeyboardDismissBehavior.onDrag,
                          padding: EdgeInsets.only(
                            bottom: MediaQuery.paddingOf(context).bottom + 16,
                          ),
                          itemCount: results.length,
                          separatorBuilder: (_, index) => const Divider(
                            height: 1,
                            indent: 24,
                            endIndent: 24,
                          ),
                          itemBuilder: (context, index) {
                            final product = results[index];
                            final selected = identical(
                              product,
                              widget.selected,
                            );
                            return ListTile(
                              contentPadding: const EdgeInsets.symmetric(
                                horizontal: 24,
                                vertical: 6,
                              ),
                              title: Text('${product['servicio'] ?? ''}'),
                              subtitle: Text('${product['producto'] ?? ''}'),
                              selected: selected,
                              trailing: Icon(
                                selected
                                    ? Icons.check_circle_outline
                                    : Icons.chevron_right,
                              ),
                              onTap: () => Navigator.pop(context, product),
                            );
                          },
                        ),
                ),
              ],
            );
          },
        ),
      ),
    );
  }
}
