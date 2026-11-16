import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gestopago_app/models/login_session.dart';
import 'package:gestopago_app/screens/purchase_history_page.dart';
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

Map<String, dynamic> _purchase(String id, String concept, String amount) => {
  'pagoId': id,
  'concepto': concept,
  'monto': amount,
  'fecha': '2026-11-15T16:32:00Z',
  'idTransaccion': 'TX-$id',
  'autorizacion': 'AUT-$id',
  'numeroCuenta': '00000000000000000001',
};

http.Response _page(List<Map<String, dynamic>> items, {required bool last}) =>
    http.Response(
      jsonEncode({
        'codigo': 1,
        'mensaje': 'Historial de compras',
        'datos': {
          'contenido': items,
          'pagina': 0,
          'tamanio': 20,
          'totalElementos': items.length,
          'totalPaginas': 1,
          'ultima': last,
        },
      }),
      200,
    );

void main() {
  late List<Map<String, dynamic>> requests;
  var paid = 0;

  Future<void> pumpHistory(
    WidgetTester tester,
    Future<http.Response> Function(http.Request) handler,
  ) async {
    tester.view.physicalSize = const Size(800, 1400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final client = MockClient((request) {
      requests.add({'path': request.url.path, ...jsonDecode(request.body)});
      expect(request.headers['Authorization'], 'Bearer token-cliente');
      return handler(request);
    });
    addTearDown(client.close);
    await tester.pumpWidget(
      MaterialApp(
        home: PurchaseHistoryPage(
          api: PaymentApiService(client, _session, baseUrl: 'http://api'),
          onPay: () => paid++,
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  setUp(() {
    requests = [];
    paid = 0;
  });

  testWidgets('muestra las compras con importe, fecha y referencia', (
    tester,
  ) async {
    await pumpHistory(
      tester,
      (_) async => _page([
        _purchase('b', 'Recarga Telcel', '100.00'),
        _purchase('a', 'Pago CFE', '350.5'),
      ], last: true),
    );

    expect(requests.single, {
      'path': '/api/pagos/historial',
      'pagina': 0,
      'tamanio': 20,
    });
    expect(find.text('Recarga Telcel'), findsOneWidget);
    expect(find.text('\$100.00 MXN'), findsOneWidget);
    expect(find.text('\$350.50 MXN'), findsOneWidget);
    expect(find.text('Ref. TX-b'), findsOneWidget);
    expect(find.text('Ver más compras'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('sin compras ofrece pagar un servicio', (tester) async {
    await pumpHistory(
      tester,
      (_) async => http.Response(
        jsonEncode({'codigo': 0, 'mensaje': 'No se encontraron datos'}),
        200,
      ),
    );
    expect(find.text('Aún no tienes compras'), findsOneWidget);
    await tester.tap(find.text('Pagar un servicio'));
    expect(paid, 1);
  });

  testWidgets('carga la siguiente pagina sin perder las anteriores', (
    tester,
  ) async {
    await pumpHistory(
      tester,
      (request) async => jsonDecode(request.body)['pagina'] == 0
          ? _page([_purchase('b', 'Recarga Telcel', '100')], last: false)
          : _page([_purchase('a', 'Pago CFE', '350')], last: true),
    );
    await tester.tap(find.text('Ver más compras'));
    await tester.pumpAndSettle();

    expect(requests.last['pagina'], 1);
    expect(find.text('Recarga Telcel'), findsOneWidget);
    expect(find.text('Pago CFE'), findsOneWidget);
    expect(find.text('Ver más compras'), findsNothing);
  });

  testWidgets('el detalle se consulta al servidor y muestra el comprobante', (
    tester,
  ) async {
    await pumpHistory(tester, (request) async {
      if (request.url.path == '/api/pagos/historial') {
        return _page([_purchase('b', 'Tarjeta de regalo', '200')], last: true);
      }
      return http.Response(
        jsonEncode({
          'codigo': '01',
          'mensaje': 'Pago realizado correctamente',
          'datos': {
            ..._purchase('b', 'Tarjeta de regalo', '200'),
            'estado': 'APLICADO',
            'upc': 'ABC123XYZ0',
            'pin': '9876-5432',
          },
        }),
        200,
      );
    });
    await tester.tap(find.text('Tarjeta de regalo'));
    await tester.pumpAndSettle();

    expect(requests.last, {'path': '/api/pagos/consulta', 'pagoId': 'b'});
    expect(find.text('Detalle de la compra'), findsWidgets);
    expect(find.text('Compra exitosa'), findsOneWidget);
    expect(find.text('AUT-b'), findsOneWidget);
    expect(find.text('9876-5432'), findsOneWidget);
    expect(find.text('ABC123XYZ0'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('un error del servidor se muestra sin datos inventados', (
    tester,
  ) async {
    await pumpHistory(
      tester,
      (_) async => http.Response(
        jsonEncode({'codigo': 2, 'mensaje': 'Servicio no Disponible'}),
        503,
      ),
    );
    expect(find.textContaining('no está disponible'), findsOneWidget);
    expect(find.text('Aún no tienes compras'), findsNothing);
  });

  test('formatea la fecha en hora local', () {
    final local = DateTime.utc(2026, 11, 15, 16, 32).toLocal();
    String two(int n) => n.toString().padLeft(2, '0');
    expect(
      formatPurchaseDate('2026-11-15T16:32:00Z'),
      '${two(local.day)}/${two(local.month)}/${local.year} '
      '${two(local.hour)}:${two(local.minute)}',
    );
    expect(formatPurchaseDate(null), 'Fecha no disponible');
  });

  test('la cuenta se muestra enmascarada', () {
    expect(maskAccount('00000000000000000001'), '•••• 0001');
    expect(maskAccount(null), isNull);
  });
}
