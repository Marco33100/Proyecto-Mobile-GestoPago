import 'package:flutter/material.dart';

import '../utils/money_format.dart';

/// Categoría de presentación para `idCatTipoServicio` de Gestopago.
///
/// Gestopago no envía nombres ni imágenes de categoría; estos grupos salen de
/// los productos reales del catálogo (p. ej. 2 = Telcel, 5 = luz/gas/TV,
/// 10 = tarjetas de regalo).
class CatalogCategory {
  const CatalogCategory(this.key, this.label, this.icon, this.ids);

  final String key;
  final String label;
  final IconData icon;
  final Set<int> ids;

  static const all = [
    CatalogCategory(
      'recargas',
      'Recargas y paquetes',
      Icons.phone_iphone_rounded,
      {1, 2, 3, 4, 12, 13},
    ),
    CatalogCategory('hogar', 'Luz, gas y TV', Icons.lightbulb_outline_rounded, {
      5,
    }),
    CatalogCategory('internet', 'Internet', Icons.wifi_rounded, {19}),
    CatalogCategory('agua', 'Agua', Icons.water_drop_outlined, {15}),
    CatalogCategory(
      'regalo',
      'Tarjetas de regalo',
      Icons.card_giftcard_rounded,
      {10},
    ),
    CatalogCategory(
      'entretenimiento',
      'Entretenimiento y juegos',
      Icons.sports_esports_outlined,
      {11},
    ),
    CatalogCategory(
      'gobierno',
      'Gobierno e impuestos',
      Icons.account_balance_outlined,
      {7},
    ),
    CatalogCategory('finanzas', 'Créditos y finanzas', Icons.savings_outlined, {
      8,
    }),
    CatalogCategory(
      'catalogo',
      'Venta por catálogo',
      Icons.storefront_outlined,
      {9},
    ),
    CatalogCategory(
      'transporte',
      'Peaje y transporte',
      Icons.directions_car_outlined,
      {18},
    ),
    CatalogCategory('apuestas', 'Apuestas', Icons.casino_outlined, {16}),
  ];

  static const other = CatalogCategory(
    'otros',
    'Otros',
    Icons.more_horiz_rounded,
    {},
  );

  static CatalogCategory of(Map<String, dynamic> product) {
    final id = product['idCatTipoServicio'];
    for (final category in all) {
      if (category.ids.contains(id)) return category;
    }
    return other;
  }
}

/// Tipos de captura que la app sabe pagar (el backend vuelve a validarlo):
/// 1 = precio fijo, 2 = importe que indica el cliente, 4 = por referencia.
bool isPurchasable(Map<String, dynamic> product) =>
    const [1, 2, 4].contains(product['tipoFront']);

bool isGiftCard(Map<String, dynamic> product) =>
    const [10, 11].contains(product['idCatTipoServicio']);

/// Solo los productos de precio fijo traen un precio real; en los demás el
/// importe lo define el recibo o el cliente.
bool hasFixedPrice(Map<String, dynamic> product) => product['tipoFront'] == 1;

String priceLabel(Map<String, dynamic> product) {
  if (!isPurchasable(product)) return 'No disponible en la app';
  return switch (product['tipoFront']) {
    1 => MoneyFormat.mxn(product['precio']),
    4 => 'Importe según tu recibo',
    _ => 'Tú indicas el importe',
  };
}

/// Qué necesita el cliente para comprar el producto.
String requirementLabel(Map<String, dynamic> product) {
  if (!isPurchasable(product)) {
    return 'Este producto aún no se puede comprar desde la app.';
  }
  if (isGiftCard(product)) {
    return 'Al pagar recibes un código (PIN) para canjearlo.';
  }
  if (product['tipoFront'] == 1) {
    return 'Necesitas el número de teléfono a recargar.';
  }
  return 'Necesitas la referencia o número de cuenta de tu recibo.';
}

/// Descripción del proveedor (instrucciones y atención a clientes).
String descriptionOf(Map<String, dynamic> product) {
  final legend = product['legend']?.toString().trim() ?? '';
  return legend.isEmpty
      ? 'El proveedor no publicó una descripción para este producto.'
      : legend;
}

String textOf(Object? value) => value?.toString().trim() ?? '';

/// Un servicio del catálogo (Telcel, CFE, Amazon…) con sus productos.
class CatalogService {
  CatalogService(this.name, this.category, this.products);

  final String name;
  final CatalogCategory category;
  final List<Map<String, dynamic>> products;

  bool get purchasable => products.any(isPurchasable);

  /// "Desde $10.00 MXN" con precio fijo; si no, la forma de pago.
  String get priceSummary {
    final fixed = products
        .where((p) => isPurchasable(p) && hasFixedPrice(p))
        .map((p) => num.tryParse('${p['precio']}'))
        .whereType<num>()
        .where((price) => price > 0)
        .toList();
    if (fixed.isNotEmpty) {
      fixed.sort();
      return products.length == 1
          ? MoneyFormat.mxn(fixed.first)
          : 'Desde ${MoneyFormat.mxn(fixed.first)}';
    }
    final first = products.firstWhere(
      isPurchasable,
      orElse: () => products.first,
    );
    return priceLabel(first);
  }

  /// Agrupa los productos por servicio, en el orden en que llegan.
  static List<CatalogService> group(List<Map<String, dynamic>> products) {
    final byName = <String, List<Map<String, dynamic>>>{};
    for (final product in products) {
      final name = textOf(product['servicio']);
      if (name.isEmpty) continue;
      byName.putIfAbsent(name, () => []).add(product);
    }
    return [
      for (final entry in byName.entries)
        CatalogService(
          entry.key,
          CatalogCategory.of(entry.value.first),
          entry.value,
        ),
    ];
  }
}
