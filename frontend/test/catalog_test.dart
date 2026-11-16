import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gestopago_app/models/catalog.dart';
import 'package:gestopago_app/models/login_session.dart';
import 'package:gestopago_app/screens/catalog_page.dart';
import 'package:gestopago_app/screens/payment_page.dart';
import 'package:gestopago_app/services/payment_api_service.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

final _session = LoginSession(
  accessToken: 'token-cliente',
  tokenType: 'Bearer',
  expiresAt: DateTime.now().add(const Duration(hours: 1)),
  userId: 'cliente',
  email: 'ana@example.com',
  identifier: 'ana',
  fullName: 'Ana López',
);

class MemoryPending extends PendingPaymentStore {
  MemoryPending([this.id]);
  String? id;
  @override
  Future<String?> read(String userId) async => id;
  @override
  Future<void> write(String userId, String id) async => this.id = id;
  @override
  Future<void> clear(String userId) async => id = null;
}

const _products = [
  {
    'servicio': 'Telcel',
    'producto': 'Recarga 10',
    'idServicio': 133,
    'idProducto': 582,
    'idCatTipoServicio': 2,
    'tipoFront': 1,
    'precio': 10,
    'legend': 'Consulta tu saldo marcando *133#.',
  },
  {
    'servicio': 'Telcel',
    'producto': 'Recarga 50',
    'idServicio': 133,
    'idProducto': 583,
    'idCatTipoServicio': 2,
    'tipoFront': 1,
    'precio': 50,
  },
  {
    'servicio': 'CFE',
    'producto': 'Pago de luz',
    'idServicio': 10,
    'idProducto': 900,
    'idCatTipoServicio': 5,
    'tipoFront': 2,
    'precio': 10,
  },
  {
    'servicio': 'Banco Azteca',
    'producto': 'Deposito',
    'idServicio': 70,
    'idProducto': 1200,
    'idCatTipoServicio': 8,
    'tipoFront': 40,
    'precio': 0,
  },
];

http.Response _ok(Object data) =>
    http.Response(jsonEncode({'codigo': 1, 'datos': data}), 200);

void main() {
  late List<String> paths;

  Future<void> pumpCatalog(WidgetTester tester, {String? pending}) async {
    tester.view.physicalSize = const Size(900, 1400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final client = MockClient((request) async {
      paths.add(request.url.path);
      return switch (request.url.path) {
        '/api/products' => _ok({
          'productos': {'productos': _products},
        }),
        '/api/pagos/segundo-factor/estado' => _ok({'activo': true}),
        '/api/cuentas/mias/consulta' => _ok({
          'contenido': [
            {
              'numeroCuenta': 'CTA-0123456789ABCDEF0123',
              'activa': true,
              'saldoDisponible': 100,
            },
          ],
        }),
        _ => throw StateError('Ruta inesperada ${request.url.path}'),
      };
    });
    addTearDown(client.close);
    await tester.pumpWidget(
      MaterialApp(
        home: CatalogPage(
          session: _session,
          api: PaymentApiService(client, _session, baseUrl: 'http://api'),
          pendingStore: MemoryPending(pending),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  setUp(() => paths = []);

  testWidgets('agrupa por servicio con categoria, precio y disponibilidad', (
    tester,
  ) async {
    await pumpCatalog(tester);

    expect(paths, ['/api/products']);
    expect(find.text('3 servicios'), findsOneWidget);
    expect(find.text('Telcel'), findsOneWidget);
    expect(find.text('Desde \$10.00 MXN'), findsOneWidget);
    expect(find.text('Tú indicas el importe'), findsOneWidget);
    expect(find.text('Solo consulta'), findsOneWidget);
    expect(find.widgetWithText(ChoiceChip, 'Recargas y paquetes'), findsOne);
    expect(find.widgetWithText(ChoiceChip, 'Luz, gas y TV'), findsOne);
    expect(tester.takeException(), isNull);
  });

  testWidgets('filtra por categoria y por busqueda sin acentos', (
    tester,
  ) async {
    await pumpCatalog(tester);
    await tester.tap(find.widgetWithText(ChoiceChip, 'Luz, gas y TV'));
    await tester.pumpAndSettle();
    expect(find.text('1 servicio'), findsOneWidget);
    expect(find.text('CFE'), findsOneWidget);
    expect(find.text('Telcel'), findsNothing);

    await tester.tap(find.widgetWithText(ChoiceChip, 'Todo'));
    await tester.enterText(
      find.byKey(const ValueKey('catalog-search')),
      'DEPÓSITO',
    );
    await tester.pumpAndSettle();
    expect(find.text('Banco Azteca'), findsOneWidget);
    expect(find.text('Telcel'), findsNothing);

    await tester.enterText(find.byKey(const ValueKey('catalog-search')), 'zzz');
    await tester.pumpAndSettle();
    expect(find.text('Sin resultados'), findsOneWidget);
  });

  testWidgets(
    'detalle muestra descripcion y comprar abre el pago con el producto',
    (tester) async {
      await pumpCatalog(tester);
      await tester.tap(find.text('Telcel'));
      await tester.pumpAndSettle();
      expect(find.text('Recarga 10'), findsOneWidget);
      expect(find.text('\$50.00 MXN'), findsOneWidget);

      await tester.tap(find.text('Recarga 10'));
      await tester.pumpAndSettle();
      expect(find.text('Consulta tu saldo marcando *133#.'), findsOneWidget);
      expect(
        find.text('Necesitas el número de teléfono a recargar.'),
        findsOneWidget,
      );

      await tester.tap(find.text('Comprar'));
      await tester.pumpAndSettle();
      expect(find.byType(PaymentPage), findsOneWidget);
      expect(find.text('Telcel · Recarga 10'), findsWidgets);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('un producto que la app no puede pagar no ofrece comprar', (
    tester,
  ) async {
    await pumpCatalog(tester);
    await tester.tap(find.text('Banco Azteca'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Deposito'));
    await tester.pumpAndSettle();

    final button = tester.widget<FilledButton>(
      find.widgetWithText(FilledButton, 'No disponible'),
    );
    expect(button.onPressed, isNull);
    expect(find.text('Comprar'), findsNothing);
  });

  testWidgets('en telefono usa una tarjeta por fila sin desbordes', (
    tester,
  ) async {
    await pumpCatalog(tester);
    tester.view.physicalSize = const Size(390, 844);
    await tester.pumpAndSettle();
    expect(find.text('Desde \$10.00 MXN'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('un pago en proceso se avisa antes de otra compra', (
    tester,
  ) async {
    await pumpCatalog(tester, pending: 'pago-1');
    expect(find.textContaining('Tienes un pago en proceso'), findsOneWidget);
    expect(find.text('Ver estado'), findsOneWidget);
  });

  test('categorias, precios y resumen salen de los datos de Gestopago', () {
    expect(
      CatalogCategory.of({'idCatTipoServicio': 13}).label,
      'Recargas y paquetes',
    );
    expect(CatalogCategory.of({'idCatTipoServicio': 999}).key, 'otros');
    expect(
      priceLabel({'tipoFront': 4, 'precio': 0}),
      'Importe según tu recibo',
    );
    expect(priceLabel({'tipoFront': 40}), 'No disponible en la app');
    expect(descriptionOf({'legend': '  '}), contains('no publicó'));

    final services = CatalogService.group(
      _products.map(Map<String, dynamic>.from).toList(),
    );
    expect(services.map((s) => s.name), ['Telcel', 'CFE', 'Banco Azteca']);
    expect(services.first.priceSummary, 'Desde \$10.00 MXN');
    expect(services.last.purchasable, isFalse);
  });
}
