import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:gestopago_app/models/login_session.dart';
import 'package:gestopago_app/screens/admin_search_page.dart';
import 'package:gestopago_app/screens/reconciliation_page.dart';
import 'package:gestopago_app/screens/register_page.dart';
import 'package:gestopago_app/screens/management_page.dart';
import 'package:gestopago_app/services/admin_api_service.dart';
import 'package:gestopago_app/services/auth_api_service.dart';
import 'package:gestopago_app/services/payment_api_service.dart';
import 'package:gestopago_app/theme.dart';

LoginSession session({String role = 'EJECUTIVO'}) => LoginSession(
  accessToken: 'test-token',
  tokenType: 'Bearer',
  expiresAt: DateTime.utc(2030),
  userId: 'operator-two',
  email: 'operator@example.com',
  identifier: 'operator',
  fullName: 'Operador',
  rol: role,
);

http.Response ok(Map<String, dynamic> data) => http.Response(
  jsonEncode({'codigo': 1, 'datos': data}),
  200,
  headers: {'content-type': 'application/json; charset=utf-8'},
);

AdminApiService api(http.Client client, {String role = 'EJECUTIVO'}) {
  final user = session(role: role);
  return AdminApiService(PaymentApiService(client, user), user);
}

Future<void> tapVisible(WidgetTester tester, Finder finder) async {
  await tester.ensureVisible(finder);
  await tester.pumpAndSettle();
  await tester.tap(finder);
  await tester.pumpAndSettle();
}

void main() {
  testWidgets(
    'menú de gestión móvil muestra las cuatro secciones sin errores visuales',
    (tester) async {
      tester.view.physicalSize = const Size(390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final client = MockClient((_) async => ok({}));
      addTearDown(client.close);
      await tester.pumpWidget(
        MaterialApp(
          theme: gestopagoTheme(),
          home: ManagementPage(
            session: session(),
            authApi: AuthApiService(client),
            api: api(client),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Clientes'), findsOneWidget);
      expect(find.text('Usuarios'), findsOneWidget);
      expect(find.text('Cuentas bancarias'), findsOneWidget);
      expect(find.text('Conciliación'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );
  testWidgets(
    'editar cliente conserva CURP y RFC y no envía saldo ni contraseña',
    (tester) async {
      Map<String, dynamic>? sent;
      final client = MockClient((request) async {
        expect(request.method, 'PUT');
        expect(request.url.path, '/api/clientes');
        sent = jsonDecode(request.body) as Map<String, dynamic>;
        return ok({'id': 7});
      });
      addTearDown(client.close);
      await tester.pumpWidget(
        MaterialApp(
          theme: gestopagoTheme(),
          home: RegisterPage(
            authApi: AuthApiService(client),
            session: session(),
            adminApi: api(client),
            initialClient: const {
              'id': 7,
              'nombre': 'Ana',
              'apellidoPaterno': 'Martinez',
              'apellidoMaterno': 'Lopez',
              'fechaNacimiento': '1990-01-01',
              'curp': 'MALM900101HDFRPR01',
              'rfc': 'MALM900101AB1',
              'nacionalidad': 'Mexicana',
              'correo': 'ana@example.com',
              'telefonoMovil': '5512345678',
              'sexo': 'FEMENINO',
              'estadoCivil': 'SOLTERO',
              'ocupacion': 'Ingeniera',
              'empresa': 'Empresa',
              'ingresoMensual': 15000.50,
              'domicilio': {
                'calle': 'Reforma',
                'numeroExterior': '12',
                'colonia': 'Centro',
                'municipio': 'Cuauhtemoc',
                'estado': 'CDMX',
                'codigoPostal': '01234',
                'pais': 'Mexico',
              },
            },
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(
        tester
            .widget<TextFormField>(find.byKey(const ValueKey('curp')))
            .enabled,
        isFalse,
      );
      await tapVisible(tester, find.text('Continuar'));
      await tapVisible(tester, find.text('Continuar'));
      expect(find.byKey(const ValueKey('contrasena')), findsNothing);
      await tapVisible(tester, find.text('Guardar cambios').last);
      expect(sent?['id'], 7);
      final data = sent?['datos'] as Map<String, dynamic>;
      expect(data['nombre'], 'Ana');
      expect(data.keys, isNot(contains('curp')));
      expect(data.keys, isNot(contains('rfc')));
      expect(data.keys, isNot(contains('contrasena')));
      expect(data.keys, isNot(contains('saldo')));
      expect(data.keys, isNot(contains('id')));
    },
  );

  testWidgets('propuesta usa datos originales del caso y exige evidencia', (
    tester,
  ) async {
    Map<String, dynamic>? sent;
    final client = MockClient((request) async {
      if (request.url.path.endsWith('/consulta')) {
        return ok({'estado': 'PROPUESTA', 'propuestoPor': 'operator-two'});
      }
      sent = jsonDecode(request.body) as Map<String, dynamic>;
      return ok({'propuestaId': sent!['propuestaId']});
    });
    addTearDown(client.close);
    await tester.pumpWidget(
      MaterialApp(
        theme: gestopagoTheme(),
        home: ProposalCreatePage(
          api: api(client),
          payment: const {
            'pagoId': 'payment',
            'versionPago': 4,
            'upc': 'ABC1234567',
            'monto': 10,
          },
        ),
      ),
    );
    await tester.pumpAndSettle();
    for (final field in {
      'idTransaccion': '123',
      'autorizacion': '456',
      'folioProveedor': 'folio',
      'evidenciaProveedor': 'Evidencia oficial verificada con el proveedor',
      'motivo': 'Confirmación oficial del pago',
    }.entries) {
      final finder = find.byKey(ValueKey('proposal-${field.key}'));
      await tester.ensureVisible(finder);
      await tester.pumpAndSettle();
      await tester.enterText(finder, field.value);
    }
    await tapVisible(tester, find.text('Registrar propuesta'));
    expect(sent, isNull);
    await tapVisible(tester, find.byType(CheckboxListTile));
    await tapVisible(tester, find.text('Registrar propuesta'));
    expect(sent, isNull);
    await tapVisible(tester, find.text('Confirmar'));
    expect(sent?['pagoId'], 'payment');
    expect(sent?['versionPago'], 4);
    expect(sent?['upc'], 'ABC1234567');
    expect(sent?['monto'], '10');
    expect(sent?['propuestaId'], matches(r'^[0-9a-f-]{36}$'));
    expect(find.text('Revisar propuesta'), findsWidgets);
  });

  test('conserva mensajes públicos de conflicto sin reintentar', () async {
    var calls = 0;
    final client = MockClient((_) async {
      calls++;
      return http.Response(
        jsonEncode({
          'codigo': '02',
          'mensaje': 'Saldo disponible insuficiente para realizar el pago.',
        }),
        409,
        headers: {'content-type': 'application/json; charset=utf-8'},
      );
    });
    addTearDown(client.close);
    await expectLater(
      PaymentApiService(client, session()).request('/api/pagos/preparar', {}),
      throwsA(
        isA<PaymentFailure>()
            .having((e) => e.status, 'status', 409)
            .having(
              (e) => e.message,
              'mensaje',
              contains('Saldo disponible insuficiente'),
            ),
      ),
    );
    expect(calls, 1);
  });

  test(
    'sanitiza fallos internos y conserva estado HTTP de respuestas HTML',
    () async {
      for (final body in [
        '<html>SQL password trace</html>',
        jsonEncode({'codigo': '02', 'mensaje': 'SQL password trace'}),
      ]) {
        final client = MockClient((_) async => http.Response(body, 503));
        addTearDown(client.close);
        await expectLater(
          PaymentApiService(
            client,
            session(),
          ).request('/api/pagos/consulta', {}),
          throwsA(
            isA<PaymentFailure>()
                .having((e) => e.status, 'status', 503)
                .having(
                  (e) => e.message,
                  'sin detalles internos',
                  isNot(contains('SQL')),
                ),
          ),
        );
      }
    },
  );

  test(
    'una respuesta pendiente conserva el estado y no provoca otro envío',
    () async {
      var calls = 0;
      final client = MockClient((_) async {
        calls++;
        return http.Response(
          jsonEncode({
            'codigo': '02',
            'datos': {'estado': 'REVISION', 'pagoId': 'p'},
          }),
          202,
        );
      });
      addTearDown(client.close);
      final data = await PaymentApiService(
        client,
        session(),
      ).request('/api/pagos/autorizar', {});
      expect(data['estado'], 'REVISION');
      expect(calls, 1);
    },
  );

  test(
    'cliente no puede invocar acciones administrativas desde el adaptador',
    () async {
      var calls = 0;
      final client = MockClient((_) async {
        calls++;
        return ok({});
      });
      addTearDown(client.close);
      await expectLater(
        api(client, role: 'CLIENTE').search('usuarios', {}, 0),
        throwsA(isA<PaymentFailure>()),
      );
      expect(calls, 0);
    },
  );

  test(
    'búsqueda paginada envía criterios por DTO y estado vacío es válido',
    () async {
      final client = MockClient((request) async {
        expect(request.method, 'POST');
        expect(request.url.path, '/api/clientes/consulta');
        expect(request.headers['Authorization'], 'Bearer test-token');
        expect(jsonDecode(request.body), {
          'nombre': 'Ana',
          'pagina': 2,
          'tamanio': 20,
        });
        return http.Response(
          '{"codigo":0,"mensaje":"No se encontraron datos"}',
          200,
        );
      });
      addTearDown(client.close);
      final result = await api(client).search('clientes', {'nombre': 'Ana'}, 2);
      expect(result.items, isEmpty);
      expect(result.last, isTrue);
    },
  );

  test(
    'desactivar cuenta usa PATCH y una sola identificación sin saldo',
    () async {
      final client = MockClient((request) async {
        expect(request.method, 'PATCH');
        expect(request.url.path, '/api/cuentas');
        expect(jsonDecode(request.body), {
          'numeroCuenta': 'CTA-TEST',
          'cambios': {'activa': false},
        });
        return ok({'activa': false});
      });
      addTearDown(client.close);
      await api(client).deactivateAccount('CTA-TEST');
    },
  );

  testWidgets('usuarios pagina conservando los filtros aplicados', (
    tester,
  ) async {
    final bodies = <Map<String, dynamic>>[];
    final client = MockClient((request) async {
      final body = jsonDecode(request.body) as Map<String, dynamic>;
      bodies.add(body);
      return ok({
        'contenido': [
          {'nombre': 'Ana', 'correo': 'ana@example.com', 'activo': true},
        ],
        'pagina': body['pagina'],
        'ultima': body['pagina'] == 1,
        'totalElementos': 21,
      });
    });
    addTearDown(client.close);
    await tester.pumpWidget(
      MaterialApp(
        theme: gestopagoTheme(),
        home: AdminSearchPage(
          section: 'usuarios',
          api: api(client),
          authApi: AuthApiService(client),
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const ValueKey('filter-nombre')), 'Ana');
    await tapVisible(tester, find.text('Buscar'));
    await tapVisible(tester, find.text('Siguiente'));
    expect(bodies.last, {'nombre': 'Ana', 'pagina': 1, 'tamanio': 20});
    expect(tester.takeException(), isNull);
  });

  testWidgets('conciliación vacía no ofrece resolver un pago inexistente', (
    tester,
  ) async {
    final client = MockClient((_) async => http.Response('{"codigo":0}', 200));
    addTearDown(client.close);
    await tester.pumpWidget(
      MaterialApp(
        theme: gestopagoTheme(),
        home: ReconciliationPage(api: api(client)),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('No hay pagos en revisión.'), findsOneWidget);
    expect(find.text('Registrar propuesta'), findsNothing);
  });

  Map<String, dynamic> proposal(String owner) => {
    'propuestaId': 'proposal',
    'pagoId': 'payment',
    'estado': 'PROPUESTA',
    'resultado': 'APLICADO',
    'propuestoPor': owner,
    'venceEn': '2030-01-01',
    'detalle': {
      'upc': 'ABC1234567',
      'monto': 10,
      'folioProveedor': 'folio',
      'evidenciaProveedor': 'Evidencia oficial confirmada por proveedor',
      'motivo': 'Verificación del resultado',
    },
  };

  testWidgets('el proponente no puede aprobar su propia propuesta', (
    tester,
  ) async {
    final client = MockClient((_) async => ok(proposal('operator-two')));
    addTearDown(client.close);
    await tester.pumpWidget(
      MaterialApp(
        theme: gestopagoTheme(),
        home: ProposalReviewPage(api: api(client), proposalId: 'proposal'),
      ),
    );
    await tester.pumpAndSettle();
    final button = tester.widget<FilledButton>(
      find.widgetWithText(FilledButton, 'Aprobar resolución'),
    );
    expect(button.onPressed, isNull);
  });

  testWidgets(
    'aprobar exige evidencia y confirmación; timeout obliga a consultar',
    (tester) async {
      var decisions = 0;
      final client = MockClient((request) async {
        if (request.url.path.endsWith('/aprobar')) {
          decisions++;
          expect(jsonDecode(request.body), {
            'propuestaId': 'proposal',
            'observacion': 'Resultado verificado oficialmente',
          });
          throw http.ClientException('offline');
        }
        return ok(proposal('operator-one'));
      });
      addTearDown(client.close);
      await tester.pumpWidget(
        MaterialApp(
          theme: gestopagoTheme(),
          home: ProposalReviewPage(api: api(client), proposalId: 'proposal'),
        ),
      );
      await tester.pumpAndSettle();
      final observation = find.byKey(const ValueKey('decision-observation'));
      await tester.ensureVisible(observation);
      await tester.pumpAndSettle();
      await tester.enterText(observation, 'Resultado verificado oficialmente');
      await tapVisible(tester, find.text('Aprobar resolución'));
      expect(decisions, 0);
      await tapVisible(tester, find.byType(CheckboxListTile));
      await tapVisible(tester, find.text('Aprobar resolución'));
      expect(decisions, 0);
      await tapVisible(tester, find.text('Confirmar'));
      expect(decisions, 1);
      expect(
        tester
            .widget<FilledButton>(
              find.widgetWithText(FilledButton, 'Aprobar resolución'),
            )
            .onPressed,
        isNull,
      );
      await tapVisible(tester, find.text('Actualizar propuesta'));
      expect(
        tester
            .widget<FilledButton>(
              find.widgetWithText(FilledButton, 'Aprobar resolución'),
            )
            .onPressed,
        isNotNull,
      );
      expect(decisions, 1);
      expect(tester.takeException(), isNull);
    },
  );
}
