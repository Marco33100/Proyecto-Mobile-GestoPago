import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gestopago_app/models/login_session.dart';
import 'package:gestopago_app/screens/account_page.dart';
import 'package:gestopago_app/screens/home_page.dart';
import 'package:gestopago_app/screens/management_page.dart';
import 'package:gestopago_app/screens/payment_page.dart';
import 'package:gestopago_app/services/auth_api_service.dart';
import 'package:gestopago_app/services/payment_api_service.dart';
import 'package:gestopago_app/theme.dart';
import 'package:gestopago_app/widgets/status_message.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

LoginSession executive() => LoginSession(
  accessToken: 'token-prueba',
  tokenType: 'Bearer',
  expiresAt: DateTime.utc(2030),
  userId: 'id-ejecutivo',
  email: 'ejecutivo@example.com',
  identifier: 'ejecutivo@example.com',
  fullName: 'Ejecutivo',
  rol: 'EJECUTIVO',
);

final client = LoginSession(
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
  Future<void> write(String userId, String id) async => this.id = id;
  @override
  Future<void> clear(String userId) async => id = null;
}

Future<void> setScreen(WidgetTester tester, Size size) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
}

Future<void> pumpHome(WidgetTester tester, LoginSession session) async {
  final mock = MockClient((_) async => throw StateError('sin red en prueba'));
  addTearDown(mock.close);
  await tester.pumpWidget(
    MaterialApp(
      theme: gestopagoTheme(),
      home: HomePage(
        session: session,
        authApi: AuthApiService(mock),
        onLogout: () async {},
      ),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('en teléfono usa barra inferior y no barra lateral', (
    tester,
  ) async {
    await setScreen(tester, const Size(390, 844));
    await pumpHome(tester, executive());
    expect(find.byType(NavigationBar), findsOneWidget);
    expect(find.byType(NavigationRail), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('en escritorio usa barra lateral extendida con la marca', (
    tester,
  ) async {
    await setScreen(tester, const Size(1440, 900));
    await pumpHome(tester, executive());
    expect(find.byType(NavigationBar), findsNothing);
    final rail = tester.widget<NavigationRail>(find.byType(NavigationRail));
    expect(rail.extended, isTrue);
    expect(find.text('Gestopago'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'las pestañas se crean al visitarlas y conservan su estado al volver',
    (tester) async {
      await setScreen(tester, const Size(1024, 768));
      await pumpHome(tester, executive());
      expect(find.byType(ManagementPage, skipOffstage: false), findsNothing);
      expect(find.byType(AccountPage, skipOffstage: false), findsNothing);

      await tester.tap(find.text('Gestión'));
      await tester.pumpAndSettle();
      expect(find.byType(ManagementPage), findsOneWidget);
      final managementState = tester.state(find.byType(ManagementPage));

      await tester.tap(find.text('Inicio'));
      await tester.pumpAndSettle();
      expect(find.text('Atención a clientes'), findsOneWidget);
      expect(find.byType(ManagementPage), findsNothing);
      expect(find.byType(ManagementPage, skipOffstage: false), findsOneWidget);

      await tester.tap(find.text('Gestión'));
      await tester.pumpAndSettle();
      expect(tester.state(find.byType(ManagementPage)), same(managementState));
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('el error de carga del inicio no expone detalles técnicos', (
    tester,
  ) async {
    await setScreen(tester, const Size(390, 844));
    await pumpHome(tester, client);
    expect(find.textContaining('StateError'), findsNothing);
    expect(find.textContaining('sin red en prueba'), findsNothing);
    expect(find.byType(StatusMessage), findsWidgets);
    expect(tester.takeException(), isNull);
  });

  group('pago en pantalla ancha', () {
    Future<void> pumpPayment(WidgetTester tester, Size size) async {
      await setScreen(tester, size);
      final mock = MockClient((request) async {
        final path = request.url.path;
        final Map<String, dynamic> data;
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
                  'producto': 'Recarga 10',
                  'idServicio': 133,
                  'idProducto': 582,
                  'tipoFront': 1,
                  'precio': 10,
                },
              ],
            },
          };
        } else {
          throw StateError('Ruta inesperada');
        }
        return apiResponse(data);
      });
      addTearDown(mock.close);
      await tester.pumpWidget(
        MaterialApp(
          theme: gestopagoTheme(),
          home: PaymentPage(
            session: client,
            api: PaymentApiService(mock, client),
            pendingStore: MemoryPending(),
          ),
        ),
      );
      await tester.pumpAndSettle();
    }

    testWidgets('muestra el resumen junto al formulario', (tester) async {
      await pumpPayment(tester, const Size(1280, 900));
      expect(find.text('Resumen'), findsOneWidget);
      expect(find.text('Por capturar'), findsOneWidget);

      await tester.tap(find.byKey(const ValueKey('product-selector')));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Recarga 10'));
      await tester.pumpAndSettle();
      expect(find.text('Telcel · Recarga 10'), findsWidgets);
      expect(find.text(r'$10.00 MXN'), findsWidgets);
      expect(tester.takeException(), isNull);
    });

    testWidgets('en teléfono no muestra el resumen lateral', (tester) async {
      await pumpPayment(tester, const Size(390, 844));
      expect(find.text('Resumen'), findsNothing);
      expect(tester.takeException(), isNull);
    });
  });
}

http.Response apiResponse(Map<String, dynamic> data) =>
    http.Response(jsonEncode({'codigo': 1, 'datos': data}), 200);
