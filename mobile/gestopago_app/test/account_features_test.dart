import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:gestopago_app/screens/account_page.dart';
import 'package:gestopago_app/screens/bank_accounts_page.dart';
import 'package:gestopago_app/screens/admin_search_page.dart';
import 'package:gestopago_app/services/profile_api_service.dart';
import 'package:gestopago_app/services/auth_api_service.dart';
import 'package:gestopago_app/services/admin_api_service.dart';
import 'package:gestopago_app/services/payment_api_service.dart';
import 'package:gestopago_app/models/login_session.dart';
import 'package:gestopago_app/theme.dart';

final user = LoginSession(
  accessToken: 'test',
  tokenType: 'Bearer',
  expiresAt: DateTime.utc(2030),
  userId: 'user',
  email: 'ana@example.com',
  identifier: 'ana',
  fullName: 'Ana Lopez',
  rol: 'EJECUTIVO',
);
const number = 'CTA-0123456789ABCDEF0123';
http.Response ok(Map<String, dynamic> data) => http.Response(
  jsonEncode({'codigo': 1, 'datos': data}),
  200,
  headers: {'content-type': 'application/json; charset=utf-8'},
);
PaymentApiService transport(http.Client client) =>
    PaymentApiService(client, user);
AdminApiService admin(http.Client client) =>
    AdminApiService(transport(client), user);
Future<void> tap(WidgetTester tester, Finder finder) async {
  await tester.ensureVisible(finder);
  await tester.pumpAndSettle();
  await tester.tap(finder);
  await tester.pumpAndSettle();
}

Future<void> enter(WidgetTester tester, String key, String value) async {
  final finder = find.byKey(ValueKey(key));
  await tester.ensureVisible(finder);
  await tester.pumpAndSettle();
  await tester.enterText(finder, value);
}

void main() {
  testWidgets(
    'perfil consulta al servidor con nombre y correo y muestra datos actuales',
    (tester) async {
      final client = MockClient((request) async {
        expect(request.method, 'POST');
        expect(request.url.path, '/api/usuarios/consulta');
        expect(jsonDecode(request.body), {
          'nombre': 'Ana Lopez',
          'correo': 'ana@example.com',
        });
        return ok({
          'nombre': 'Ana Lopez',
          'correo': 'ana@example.com',
          'telefono': '5512345678',
          'activo': true,
        });
      });
      addTearDown(client.close);
      await tester.pumpWidget(
        MaterialApp(
          theme: gestopagoTheme(),
          home: AccountPage(
            session: user,
            onLogout: () async {},
            api: ProfileApiService(transport(client)),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('5512345678'), findsOneWidget);
      expect(
        find.text('Perfil actualizado desde el servidor.'),
        findsOneWidget,
      );
    },
  );

  testWidgets(
    'contraseña débil no se envía; 204 solicita cerrar sesión una sola vez',
    (tester) async {
      var changes = 0;
      var logouts = 0;
      final client = MockClient((request) async {
        if (request.url.path.endsWith('/password')) {
          changes++;
          expect(request.method, 'PUT');
          expect(jsonDecode(request.body), {
            'nombre': 'Ana Lopez',
            'correo': 'ana@example.com',
            'credenciales': {
              'contrasenaActual': 'Anterior123!',
              'nuevaContrasena': 'NuevaSegura123!',
            },
          });
          return http.Response('', 204);
        }
        return ok({
          'nombre': 'Ana Lopez',
          'correo': 'ana@example.com',
          'activo': true,
        });
      });
      addTearDown(client.close);
      await tester.pumpWidget(
        MaterialApp(
          theme: gestopagoTheme(),
          home: AccountPage(
            session: user,
            onLogout: () async {
              logouts++;
            },
            api: ProfileApiService(transport(client)),
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tap(tester, find.text('Cambiar contraseña'));
      await enter(tester, 'password-current', 'Anterior123!');
      await enter(tester, 'password-new', 'abc');
      await enter(tester, 'password-confirm', 'abc');
      await tap(tester, find.text('Guardar contraseña'));
      expect(changes, 0);
      await enter(tester, 'password-new', 'NuevaSegura123!');
      await enter(tester, 'password-confirm', 'NuevaSegura123!');
      await tap(tester, find.text('Guardar contraseña'));
      expect(changes, 0);
      await tap(tester, find.text('Confirmar'));
      expect(changes, 1);
      expect(logouts, 1);
      expect(
        tester
            .widget<TextFormField>(
              find.byKey(const ValueKey('password-current')),
            )
            .controller!
            .text,
        isEmpty,
      );
    },
  );

  testWidgets(
    'validación del servidor se presenta al usuario sin cerrar sesión',
    (tester) async {
      var logouts = 0;
      final client = MockClient((request) async {
        if (request.url.path.endsWith('/password')) {
          return http.Response(
            jsonEncode({
              'codigo': 2,
              'mensaje': 'La contraseña actual es incorrecta.',
            }),
            400,
            headers: {'content-type': 'application/json; charset=utf-8'},
          );
        }
        return ok({
          'nombre': 'Ana Lopez',
          'correo': 'ana@example.com',
          'activo': true,
        });
      });
      addTearDown(client.close);
      await tester.pumpWidget(
        MaterialApp(
          theme: gestopagoTheme(),
          home: AccountPage(
            session: user,
            onLogout: () async {
              logouts++;
            },
            api: ProfileApiService(transport(client)),
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tap(tester, find.text('Cambiar contraseña'));
      await enter(tester, 'password-current', 'Anterior123!');
      await enter(tester, 'password-new', 'NuevaSegura123!');
      await enter(tester, 'password-confirm', 'NuevaSegura123!');
      await tap(tester, find.text('Guardar contraseña'));
      await tap(tester, find.text('Confirmar'));
      expect(find.text('La contraseña actual es incorrecta.'), findsOneWidget);
      expect(logouts, 0);
    },
  );

  testWidgets(
    'consulta cuentas activas, cuenta específica y saldo sin enviar campos protegidos',
    (tester) async {
      final calls = <String>[];
      final client = MockClient((request) async {
        calls.add(request.url.path);
        expect(request.method, 'POST');
        if (request.url.path.endsWith('/activas/consulta')) {
          expect(jsonDecode(request.body), {'pagina': 0, 'tamanio': 20});
          return ok({
            'contenido': [
              {'numeroCuenta': number, 'saldoDisponible': 90},
            ],
            'pagina': 0,
            'ultima': true,
            'totalElementos': 1,
          });
        }
        expect(jsonDecode(request.body), {'numeroCuenta': number});
        return ok({
          'numeroCuenta': number,
          'activa': true,
          'saldo': 100,
          'saldoDisponible': 90,
          'saldoRetenido': 10,
        });
      });
      addTearDown(client.close);
      await tester.pumpWidget(
        MaterialApp(
          theme: gestopagoTheme(),
          home: BankAccountsPage(api: admin(client)),
        ),
      );
      await tester.pumpAndSettle();
      await enter(tester, 'account-number', 'incorrecta');
      await tap(tester, find.text('Consultar cuenta'));
      expect(calls.length, 1);
      await enter(tester, 'account-number', number);
      await tap(tester, find.text('Consultar cuenta'));
      expect(calls, [
        '/api/cuentas/activas/consulta',
        '/api/cuentas/consulta',
        '/api/cuentas/saldo/consulta',
      ]);
      expect(find.text(r'Retenido: $10.00 MXN'), findsOneWidget);
      await tap(tester, find.text('Actualizar saldo'));
      expect(calls.last, '/api/cuentas/saldo/consulta');
    },
  );

  testWidgets(
    'filtros de fecha rechazan fechas inválidas y envían rango sin hora',
    (tester) async {
      final bodies = <Map<String, dynamic>>[];
      final client = MockClient((request) async {
        bodies.add(jsonDecode(request.body) as Map<String, dynamic>);
        return http.Response('{"codigo":0}', 200);
      });
      addTearDown(client.close);
      await tester.pumpWidget(
        MaterialApp(
          theme: gestopagoTheme(),
          home: AdminSearchPage(
            section: 'clientes',
            api: admin(client),
            authApi: AuthApiService(client),
          ),
        ),
      );
      await tester.pumpAndSettle();
      await enter(tester, 'filter-desde', '2026-02-30');
      await tap(tester, find.text('Buscar'));
      expect(bodies.length, 1);
      await enter(tester, 'filter-desde', '2026-01-02');
      await enter(tester, 'filter-hasta', '2026-01-01');
      await tap(tester, find.text('Buscar'));
      expect(bodies.length, 1);
      await enter(tester, 'filter-hasta', '2026-02-01');
      await tap(tester, find.text('Buscar'));
      expect(bodies.last, {
        'desde': '2026-01-02',
        'hasta': '2026-02-01',
        'pagina': 0,
        'tamanio': 20,
      });
      expect(tester.takeException(), isNull);
    },
  );
}
