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
  for (final outcome in ['APLICADO', 'RECHAZADO']) {
    testWidgets(
      'regresar de pago $outcome conserva cuenta con saldo actualizado sin referencias antiguas',
      (tester) async {
        final pending = MemoryPending();
        var accountReads = 0;
        final client = MockClient((request) async {
          final path = request.url.path;
          Map<String, dynamic> data;
          if (path.endsWith('/estado')) {
            data = {'activo': true};
          } else if (path.endsWith('/mias/consulta')) {
            accountReads++;
            data = {
              'contenido': [
                {
                  'numeroCuenta': 'CTA-0123456789ABCDEF0123',
                  'activa': true,
                  'saldoDisponible': accountReads == 1 || outcome == 'RECHAZADO'
                      ? 100
                      : 90,
                },
              ],
            };
          } else if (path == '/api/products') {
            data = {
              'productos': {
                'productos': [
                  {
                    'servicio': 'Telcel',
                    'producto': 'Recarga 10',
                    'idServicio': 133,
                    'idProducto': 582,
                    'tipoFront': 1,
                    'precio': 10,
                  },
                ],
              },
            };
          } else if (path.endsWith('/preparar')) {
            data = {
              'pagoId': pending.id,
              'estado': 'PREPARADO',
              'concepto': 'Recarga 10',
              'monto': 10,
              'numeroCuenta': 'CTA-0123456789ABCDEF0123',
              'autorizacionDisponible': true,
            };
          } else if (path.endsWith('/autorizar')) {
            data = {
              'pagoId': pending.id,
              'estado': outcome,
              'concepto': 'Recarga 10',
              'monto': 10,
              'numeroCuenta': 'CTA-0123456789ABCDEF0123',
            };
          } else {
            throw StateError('Ruta inesperada');
          }
          return http.Response(jsonEncode({'codigo': 1, 'datos': data}), 200);
        });
        addTearDown(client.close);
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
        Future<void> tap(Finder finder) async {
          await tester.ensureVisible(finder);
          await tester.pumpAndSettle();
          await tester.tap(finder);
          await tester.pumpAndSettle();
        }

        await tap(find.byType(DropdownButtonFormField<String>));
        await tester.tap(find.textContaining('100.00 MXN').last);
        await tester.pumpAndSettle();
        await tap(find.byKey(const ValueKey('product-selector')));
        await tester.tap(find.text('Recarga 10'));
        await tester.pumpAndSettle();
        final phone = find.byWidgetPredicate(
          (w) =>
              w is TextField &&
              w.decoration?.labelText == 'Teléfono de la recarga',
        );
        await tester.ensureVisible(phone);
        await tester.pumpAndSettle();
        await tester.enterText(phone, '5555555551');
        await tap(find.text('Revisar pago'));
        final otp = find.byWidgetPredicate(
          (w) =>
              w is TextField &&
              w.decoration?.labelText == 'Código del autenticador',
        );
        await tester.ensureVisible(otp);
        await tester.pumpAndSettle();
        await tester.enterText(otp, '123456');
        await tap(find.text('Autorizar pago'));
        await tap(find.text('Volver a mis servicios'));
        expect(accountReads, 2);
        final selector = tester.widget<DropdownButtonFormField<String>>(
          find.byType(DropdownButtonFormField<String>),
        );
        expect(selector.initialValue, 'CTA-0123456789ABCDEF0123');
        expect(
          find.textContaining(
            outcome == 'APLICADO' ? '90.00 MXN' : '100.00 MXN',
          ),
          findsOneWidget,
        );
        expect(pending.id, isNull);
        expect(tester.takeException(), isNull);
      },
    );
  }
  testWidgets(
    'catálogo integra búsqueda y permite cambiar producto sin errores ni pagos',
    (tester) async {
      var prepareCalls = 0;
      final client = MockClient((request) async {
        final path = request.url.path;
        Map<String, dynamic> data;
        if (path.endsWith('/estado')) {
          data = {'activo': true};
        } else if (path.endsWith('/mias/consulta')) {
          data = {
            'contenido': [
              {
                'numeroCuenta': 'CTA-0123456789ABCDEF0123',
                'activa': true,
                'saldoDisponible': 100,
              },
            ],
          };
        } else if (path == '/api/products') {
          data = {
            'productos': {
              'productos': [
                {
                  'servicio': 'Telcel',
                  'producto': 'Recarga 20',
                  'idServicio': 133,
                  'idProducto': 403,
                  'tipoFront': 1,
                  'precio': 20,
                },
                {
                  'servicio': 'Telcel',
                  'producto': 'Recarga 10',
                  'idServicio': 133,
                  'idProducto': 582,
                  'tipoFront': 1,
                  'precio': 10,
                },
                {
                  'servicio': 'Agua',
                  'producto': 'Recibo de agua',
                  'idServicio': 76,
                  'idProducto': 205,
                  'tipoFront': 2,
                },
              ],
            },
          };
        } else {
          prepareCalls++;
          throw StateError('La búsqueda no debe enviar pagos');
        }
        return http.Response(jsonEncode({'codigo': 1, 'datos': data}), 200);
      });
      addTearDown(client.close);
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
      Future<void> openCatalog() async {
        final selector = find.byKey(const ValueKey('product-selector'));
        await tester.ensureVisible(selector);
        await tester.pumpAndSettle();
        await tester.tap(selector);
        await tester.pumpAndSettle();
      }

      Future<void> search(String text) async {
        await tester.enterText(
          find.byKey(const ValueKey('catalog-search')),
          text,
        );
        await tester.pumpAndSettle();
        expect(tester.takeException(), isNull);
      }

      expect(find.byKey(const ValueKey('catalog-search')), findsNothing);
      await openCatalog();
      await search('Telcel');
      expect(find.text('Recibo de agua'), findsNothing);
      await tester.tap(find.text('Recarga 20'));
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('catalog-search')), findsNothing);
      expect(find.text('Telcel · Recarga 20'), findsOneWidget);
      expect(find.text(r'$20.00 MXN'), findsOneWidget);
      await openCatalog();
      await search('Agua');
      expect(find.text('Recarga 20'), findsNothing);
      await search('sin coincidencias');
      expect(
        find.text(
          'No hay coincidencias. Prueba otro nombre o limpia la búsqueda.',
        ),
        findsOneWidget,
      );
      await tester.tap(find.byTooltip('Limpiar búsqueda'));
      await tester.pumpAndSettle();
      expect(find.text('Recarga 10'), findsOneWidget);
      await tester.tap(find.byTooltip('Cerrar catálogo'));
      await tester.pumpAndSettle();
      // Buscar y cancelar no cambia el producto que se estaba preparando.
      expect(find.text('Telcel · Recarga 20'), findsOneWidget);
      await openCatalog();
      await search('Agua');
      await tester.tap(find.text('Recibo de agua'));
      await tester.pumpAndSettle();
      expect(find.text('Referencia del servicio'), findsOneWidget);
      expect(find.text('Teléfono de la recarga'), findsNothing);
      await openCatalog();
      await search('recarga 10');
      await tester.tap(find.text('Recarga 10'));
      await tester.pumpAndSettle();
      expect(find.text('Telcel · Recarga 10'), findsOneWidget);
      expect(find.text(r'$10.00 MXN'), findsOneWidget);
      expect(prepareCalls, 0);
      expect(tester.takeException(), isNull);
    },
  );
  for (final rule in [
    'PARCIAL_HASTA_SALDO',
    'HASTA_SALDO_SIN_POLITICA',
    'IMPORTE_EXACTO',
  ]) {
    testWidgets('explica la regla real del backend: $rule', (tester) async {
      final pending = MemoryPending()..id = 'intent';
      final client = MockClient(
        (_) async => http.Response(
          jsonEncode({
            'codigo': 1,
            'datos': {
              'pagoId': 'intent',
              'estado': 'PREPARADO',
              'concepto': 'Luz',
              'monto': 20,
              'numeroCuenta': '123',
              'reglaImporteReferencia': rule,
              'autorizacionDisponible': false,
            },
          }),
          200,
        ),
      );
      addTearDown(client.close);
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
      expect(
        find.text(
          rule == 'IMPORTE_EXACTO'
              ? 'El importe debe coincidir con el saldo que informó el proveedor.'
              : 'El importe no puede superar el saldo que informó el proveedor.',
        ),
        findsOneWidget,
      );
      expect(
        tester
            .widget<FilledButton>(
              find.widgetWithText(FilledButton, 'Autorizar pago'),
            )
            .onPressed,
        isNull,
      );
    });
  }
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
      expect(find.text('Saldo informado del servicio'), findsOneWidget);
      expect(find.text(r'$30.00 MXN'), findsOneWidget);
      await tester.scrollUntilVisible(
        find.text('Autorizar pago'),
        250,
        scrollable: find.byType(Scrollable).first,
      );
      final button = tester.widget<FilledButton>(
        find
            .ancestor(
              of: find.text('Autorizar pago'),
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
    expect(find.text('Pago en proceso'), findsOneWidget);
    expect(find.text('DESTINO-REAL-123'), findsOneWidget);
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
              'autorizacionDisponible': true,
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
      await tester.ensureVisible(find.text('Autorizar pago'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Autorizar pago'));
      await tester.pumpAndSettle();
      expect(authorizations, 0);
      await tester.enterText(find.byType(TextField), '123456');
      await tester.ensureVisible(find.text('Autorizar pago'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Autorizar pago'));
      await tester.pumpAndSettle();
      expect(authorizations, 1);
      expect(find.text('Pago en proceso'), findsOneWidget);
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
    await tester.pumpAndSettle();
    await tester.tap(find.text('Preparar autenticador'));
    await tester.pumpAndSettle();
    expect(find.text(secret), findsOneWidget);
    await tester.enterText(find.byType(TextField).first, '123456');
    await tester.ensureVisible(find.text('Activar autenticador'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Activar autenticador'));
    await tester.pumpAndSettle();
    expect(find.text(secret), findsNothing);
    expect(find.text('Guarda tus códigos'), findsOneWidget);
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
      await tester.ensureVisible(find.byTooltip('Mostrar PIN'));
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Mostrar PIN'));
      await tester.pump();
      expect(find.text('secret-pin'), findsOneWidget);
      expect(tester.takeException(), isNull);
      client.close();
    },
  );
}
