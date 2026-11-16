import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gestopago_app/models/login_session.dart';
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

Map<String, dynamic> _payment(String state, [Map<String, dynamic>? extra]) => {
  'pagoId': 'intent',
  'estado': state,
  'concepto': 'Telcel Recarga 100',
  'monto': 100,
  'comisionCliente': 0,
  'numeroCuenta': '00000000000000000001',
  'upc': 'ABC1234567',
  'telefono': '5512345678',
  'autorizacionDisponible': true,
  ...?extra,
};

http.Response _result(String code, Map<String, dynamic> data) =>
    http.Response(jsonEncode({'codigo': code, 'datos': data}), 200);

void main() {
  late int authorizations;

  /// Pago ya preparado (recuperado del almacenamiento) listo para autorizar.
  Future<void> pumpPrepared(
    WidgetTester tester,
    Future<http.Response> Function() onAuthorize,
  ) async {
    tester.view.physicalSize = const Size(800, 1600);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final client = MockClient((request) async {
      if (request.url.path.endsWith('/autorizar')) {
        authorizations++;
        return onAuthorize();
      }
      return _result('02', _payment('PREPARADO'));
    });
    addTearDown(client.close);
    await tester.pumpWidget(
      MaterialApp(
        home: PaymentPage(
          session: _session,
          api: PaymentApiService(client, _session),
          pendingStore: MemoryPending('intent'),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  Future<void> authorize(WidgetTester tester) async {
    await tester.enterText(find.byType(TextField), '123456');
    await tester.ensureVisible(find.text('Autorizar pago'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Autorizar pago'));
    await tester.pumpAndSettle();
  }

  setUp(() => authorizations = 0);

  testWidgets('compra exitosa muestra importe, transaccion y autorizacion', (
    tester,
  ) async {
    await pumpPrepared(
      tester,
      () async => _result(
        '01',
        _payment('APLICADO', {
          'idTransaccion': '812345',
          'autorizacion': '556677',
          'aviso': 'Conserva tu comprobante.',
        }),
      ),
    );
    await authorize(tester);

    expect(find.text('Compra exitosa'), findsOneWidget);
    expect(find.text('Gestopago confirmó tu compra.'), findsOneWidget);
    expect(find.text('\$100.00 MXN'), findsWidgets);
    expect(find.text('812345'), findsOneWidget);
    expect(find.text('556677'), findsOneWidget);
    expect(find.text('Conserva tu comprobante.'), findsOneWidget);
    expect(find.byIcon(Icons.check_circle_rounded), findsOneWidget);
    expect(find.text('Volver a mis servicios'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('compra rechazada se distingue y no muestra importe cobrado', (
    tester,
  ) async {
    await pumpPrepared(
      tester,
      () async => _result('02', _payment('RECHAZADO')),
    );
    await authorize(tester);

    expect(find.text('Compra rechazada'), findsOneWidget);
    expect(find.byIcon(Icons.cancel_rounded), findsOneWidget);
    expect(find.text('Compra exitosa'), findsNothing);
  });

  testWidgets('compra pendiente pide consultar y no iniciar otra', (
    tester,
  ) async {
    await pumpPrepared(
      tester,
      () async => _result('02', _payment('PENDIENTE')),
    );
    await authorize(tester);

    expect(find.text('Compra pendiente'), findsOneWidget);
    expect(find.byIcon(Icons.hourglass_top_rounded), findsOneWidget);
    expect(find.text('Consultar estado'), findsOneWidget);
  });

  testWidgets('error de comunicacion no deja volver a autorizar', (
    tester,
  ) async {
    await pumpPrepared(
      tester,
      () async => throw const SocketException('sin red'),
    );
    await authorize(tester);

    expect(authorizations, 1);
    expect(find.text('No pudimos confirmar tu compra'), findsOneWidget);
    expect(find.byIcon(Icons.wifi_off_rounded), findsOneWidget);
    expect(find.text('Autorizar pago'), findsNothing);
    expect(find.text('Consultar estado'), findsOneWidget);
  });

  testWidgets('un codigo mal escrito no es error de comunicacion', (
    tester,
  ) async {
    await pumpPrepared(tester, () async => _result('01', _payment('APLICADO')));
    await tester.ensureVisible(find.text('Autorizar pago'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Autorizar pago'));
    await tester.pumpAndSettle();

    expect(authorizations, 0);
    expect(find.text('No pudimos confirmar tu compra'), findsNothing);
    expect(find.text('Escribe los seis dígitos del autenticador.'), findsOne);
    expect(find.text('Autorizar pago'), findsOneWidget);
  });

  testWidgets('abierta desde el catalogo, terminar regresa al catalogo', (
    tester,
  ) async {
    final client = MockClient((_) async => _result('01', _payment('APLICADO')));
    addTearDown(client.close);
    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) => TextButton(
            onPressed: () => Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (_) => PaymentPage(
                  session: _session,
                  api: PaymentApiService(client, _session),
                  pendingStore: MemoryPending('intent'),
                ),
              ),
            ),
            child: const Text('Catálogo'),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Catálogo'));
    await tester.pumpAndSettle();
    expect(find.text('Compra exitosa'), findsOneWidget);

    await tester.ensureVisible(find.text('Volver a mis servicios'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Volver a mis servicios'));
    await tester.pumpAndSettle();
    expect(find.byType(PaymentPage), findsNothing);
    expect(find.text('Catálogo'), findsOneWidget);
  });
}
