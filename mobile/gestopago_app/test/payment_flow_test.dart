import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:gestopago_app/models/login_session.dart';
import 'package:gestopago_app/services/payment_api_service.dart';
import 'package:gestopago_app/screens/payment_page.dart';

final session = LoginSession(
  accessToken: 'test',
  tokenType: 'Bearer',
  expiresAt: DateTime.utc(2030),
  userId: 'user',
  email: 'test@example.com',
  identifier: 'test',
  fullName: 'Cliente',
);

class MemoryPending extends PendingPaymentStore {
  String? id;
  @override
  Future<String?> read(String userId) async => id;
  @override
  Future<void> write(String userId, String id) async {
    this.id = id;
  }

  @override
  Future<void> clear(String userId) async {
    id = null;
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  testWidgets(
    'muestra deuda consultada y bloquea autorizar sin regla confirmada',
    (tester) async {
      final pending = MemoryPending()..id = 'intent';
      final client = MockClient((request) async {
        expect(request.url.path, '/api/pagos/consulta');
        return http.Response(
          jsonEncode({
            'codigo': '02',
            'datos': {
              'pagoId': 'intent',
              'estado': 'PREPARADO',
              'concepto': 'Luz',
              'monto': 20,
              'numeroCuenta': '123',
              'upc': 'ABC1234567',
              'saldoReferencia': 30,
              'consultaReferenciaEn': '2026-10-29T12:00:00Z',
              'reglaImporteReferencia': 'SIN_DEFINIR',
              'autorizacionDisponible': false,
            },
          }),
          200,
        );
      });
      await tester.pumpWidget(
        MaterialApp(
          home: PaymentPage(
            session: session,
            api: PaymentApiService(client, session),
            pendingStore: pending,
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Saldo informado del servicio: 30 MXN'), findsOneWidget);
      expect(find.text('Regla de importe: SIN_DEFINIR'), findsOneWidget);
      await tester.scrollUntilVisible(
        find.text('Autorizar este pago'),
        250,
        scrollable: find.byType(Scrollable).first,
      );
      final button = tester.widget<FilledButton>(
        find
            .ancestor(
              of: find.text('Autorizar este pago'),
              matching: find.byWidgetPredicate(
                (widget) => widget is FilledButton,
              ),
            )
            .first,
      );
      expect(button.onPressed, isNull);
      expect(tester.takeException(), isNull);
      client.close();
    },
  );
  test(
    'acepta 02 pendiente con datos sin interpretarlo como un pago rechazado',
    () async {
      var calls = 0;
      final client = MockClient((request) async {
        calls++;
        expect(request.headers['Authorization'], 'Bearer test');
        return http.Response(
          jsonEncode({
            'codigo': '02',
            'mensaje': 'Servicio no Disponible',
            'datos': {'pagoId': 'intent', 'estado': 'PENDIENTE'},
          }),
          200,
        );
      });
      final result = await PaymentApiService(client, session).request(
        '/api/pagos/autorizar',
        {'pagoId': 'intent', 'codigo': '123456'},
      );
      expect(result['estado'], 'PENDIENTE');
      expect(calls, 1);
      client.close();
    },
  );
  test('conexion perdida nunca reintenta send ni autorizar', () async {
    var calls = 0;
    final client = MockClient((_) async {
      calls++;
      throw http.ClientException('offline');
    });
    await expectLater(
      PaymentApiService(client, session).request('/api/pagos/autorizar', {}),
      throwsA(isA<PaymentFailure>()),
    );
    expect(calls, 1);
    client.close();
  });
  test('UUID aleatorio de intencion tiene version 4', () {
    final ids = List.generate(500, (_) => PendingPaymentStore.newId());
    expect(ids.toSet().length, 500);
    for (final id in ids) {
      expect(
        id,
        matches(
          r'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$',
        ),
      );
    }
  });
  testWidgets('al regresar recupera pago pendiente y bloquea un nuevo pago', (
    tester,
  ) async {
    final pending = MemoryPending()..id = 'intent';
    final client = MockClient((request) async {
      expect(request.url.path, '/api/pagos/consulta');
      return http.Response(
        jsonEncode({
          'codigo': '02',
          'datos': {
            'pagoId': 'intent',
            'estado': 'PENDIENTE',
            'concepto': 'Luz',
            'monto': 20,
            'numeroCuenta': '123',
            'upc': 'ABC1234567',
            'referencia': 'DESTINO-REAL-123',
            'aviso': 'No repitas el pago.',
          },
        }),
        200,
      );
    });
    await tester.pumpWidget(
      MaterialApp(
        home: PaymentPage(
          session: session,
          api: PaymentApiService(client, session),
          pendingStore: pending,
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('PENDIENTE'), findsOneWidget);
    expect(find.text('Referencia: DESTINO-REAL-123'), findsOneWidget);
    expect(find.text('Revisar pago'), findsNothing);
    expect(pending.id, 'intent');
    expect(tester.takeException(), isNull);
    client.close();
  });
  testWidgets(
    'codigo invalido no envia y codigo valido autoriza exactamente una vez',
    (tester) async {
      final pending = MemoryPending()..id = 'intent';
      var authorizations = 0;
      final client = MockClient((request) async {
        if (request.url.path.endsWith('/autorizar')) {
          authorizations++;
          expect(jsonDecode(request.body), {
            'pagoId': 'intent',
            'codigo': '123456',
          });
          return http.Response(
            jsonEncode({
              'codigo': '02',
              'datos': {'pagoId': 'intent', 'estado': 'PENDIENTE'},
            }),
            200,
          );
        }
        return http.Response(
          jsonEncode({
            'codigo': '02',
            'datos': {
              'pagoId': 'intent',
              'estado': 'PREPARADO',
              'concepto': 'Luz',
              'monto': 20,
              'numeroCuenta': '123',
              'upc': 'ABC1234567',
              'referencia': 'REAL',
            },
          }),
          200,
        );
      });
      await tester.pumpWidget(
        MaterialApp(
          home: PaymentPage(
            session: session,
            api: PaymentApiService(client, session),
            pendingStore: pending,
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.text('Autorizar este pago'));
      await tester.tap(find.text('Autorizar este pago'));
      await tester.pumpAndSettle();
      expect(authorizations, 0);
      await tester.enterText(find.byType(TextField), '123456');
      await tester.ensureVisible(find.text('Autorizar este pago'));
      await tester.tap(find.text('Autorizar este pago'));
      await tester.pumpAndSettle();
      expect(authorizations, 1);
      expect(find.text('PENDIENTE'), findsOneWidget);
      expect(tester.takeException(), isNull);
      client.close();
    },
  );

  testWidgets('registro TOTP muestra QR y recuperacion sin canal biometrico', (
    tester,
  ) async {
    const secret = 'GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ';
    final client = MockClient((request) async {
      final path = request.url.path;
      Map<String, dynamic> data;
      if (path.endsWith('/estado')) {
        data = {'activo': false};
      } else if (path.endsWith('/mias/consulta')) {
        data = {'contenido': []};
      } else if (path == '/api/products') {
        return http.Response(jsonEncode({'codigo': 0}), 200);
      } else if (path.endsWith('/segundo-factor/preparar')) {
        expect(jsonDecode(request.body), {'contrasena': 'password-fixture'});
        data = {
          'secreto': secret,
          'uri':
              'otpauth://totp/Gestopago:test?secret=$secret&issuer=Gestopago',
        };
      } else if (path.endsWith('/activar')) {
        expect(jsonDecode(request.body), {'codigo': '123456'});
        data = {
          'activo': true,
          'codigosRecuperacion': ['01234567-89ABCDEF-01234567-89ABCDEF'],
        };
      } else {
        throw StateError('Ruta inesperada');
      }
      return http.Response(jsonEncode({'codigo': 1, 'datos': data}), 200);
    });
    await tester.pumpWidget(
      MaterialApp(
        home: PaymentPage(
          session: session,
          api: PaymentApiService(client, session),
          pendingStore: MemoryPending(),
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField).first, 'password-fixture');
    await tester.ensureVisible(find.text('Preparar autenticador'));
    await tester.tap(find.text('Preparar autenticador'));
    await tester.pumpAndSettle();
    expect(find.text(secret), findsOneWidget);
    await tester.enterText(find.byType(TextField).first, '123456');
    await tester.ensureVisible(find.text('Activar segundo factor'));
    await tester.tap(find.text('Activar segundo factor'));
    await tester.pumpAndSettle();
    expect(find.text(secret), findsNothing);
    expect(find.text('Guarda tus codigos de recuperacion'), findsOneWidget);
    expect(tester.takeException(), isNull);
    client.close();
  });

  testWidgets(
    'comprobante exitoso oculta PIN hasta que el cliente lo solicita',
    (tester) async {
      final pending = MemoryPending()..id = 'intent';
      final client = MockClient(
        (_) async => http.Response(
          jsonEncode({
            'codigo': '01',
            'datos': {
              'pagoId': 'intent',
              'estado': 'APLICADO',
              'concepto': 'Tarjeta',
              'monto': 25,
              'numeroCuenta': '123',
              'upc': 'ABC1234567',
              'pin': 'secret-pin',
              'autorizacion': '456',
            },
          }),
          200,
        ),
      );
      await tester.pumpWidget(
        MaterialApp(
          home: PaymentPage(
            session: session,
            api: PaymentApiService(client, session),
            pendingStore: pending,
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.textContaining('secret-pin'), findsNothing);
      await tester.tap(find.text('Mostrar PIN de la tarjeta'));
      await tester.pump();
      expect(find.text('PIN: secret-pin'), findsOneWidget);
      expect(tester.takeException(), isNull);
      client.close();
    },
  );
}
