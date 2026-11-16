import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gestopago_app/screens/payment_page.dart';
import 'package:gestopago_app/services/payment_api_service.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'adaptive_layout_test.dart' as layout;
import 'payment_flow_test.dart' show session;

Future<List<String>> pumpSecurity(WidgetTester tester, bool? active) async {
  await layout.setScreen(tester, const Size(1000, 1600));
  final paths = <String>[];
  final client = MockClient((request) async {
    paths.add(request.url.path);
    if (active == null) return http.Response('', 500);
    return http.Response(
      jsonEncode({
        'codigo': 1,
        'datos': {'activo': active},
      }),
      200,
    );
  });
  addTearDown(client.close);
  await tester.pumpWidget(
    MaterialApp(
      home: PaymentPage(
        session: session,
        api: PaymentApiService(client, session),
        securityOnly: true,
      ),
    ),
  );
  await tester.pumpAndSettle();
  return paths;
}

void main() {
  testWidgets('con autenticador activo solo ofrece cambiarlo', (tester) async {
    final paths = await pumpSecurity(tester, true);
    // Solo consulta el estado: no carga cuentas, productos ni pagos.
    expect(paths, ['/api/pagos/segundo-factor/estado']);
    expect(find.text('Tu autenticador está activo'), findsOneWidget);
    expect(find.text('Revisar pago'), findsNothing);

    await tester.tap(find.text('Cambiar autenticador'));
    await tester.pumpAndSettle();
    expect(find.text('Configura un nuevo autenticador'), findsOneWidget);

    await tester.ensureVisible(find.text('Conservar mi autenticador'));
    await tester.tap(find.text('Conservar mi autenticador'));
    await tester.pumpAndSettle();
    expect(find.text('Tu autenticador está activo'), findsOneWidget);
  });

  testWidgets('sin autenticador muestra la vinculación', (tester) async {
    await pumpSecurity(tester, false);
    expect(find.text('Protege tus pagos'), findsOneWidget);
    expect(find.text('Tu autenticador está activo'), findsNothing);
  });

  testWidgets('si no se pudo leer el estado no ofrece vincular', (
    tester,
  ) async {
    await pumpSecurity(tester, null);
    expect(find.text('Volver a cargar'), findsOneWidget);
    expect(find.text('Protege tus pagos'), findsNothing);
    expect(find.text('Tu autenticador está activo'), findsNothing);
  });

  testWidgets('Cuenta del cliente abre Seguridad de pagos', (tester) async {
    await layout.setScreen(tester, const Size(1000, 1600));
    await layout.pumpHome(tester, layout.client);
    await tester.tap(find.text('Cuenta'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Seguridad de pagos'));
    await tester.pumpAndSettle();
    expect(find.byType(PaymentPage), findsOneWidget);
  });
}
