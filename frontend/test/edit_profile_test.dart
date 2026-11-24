import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gestopago_app/models/login_session.dart';
import 'package:gestopago_app/screens/edit_profile_page.dart';
import 'package:gestopago_app/services/payment_api_service.dart';
import 'package:gestopago_app/services/profile_api_service.dart';
import 'package:gestopago_app/theme.dart';
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

http.Response _ok(Map<String, dynamic> data) => http.Response.bytes(
  utf8.encode(jsonEncode({'codigo': 1, 'mensaje': 'ok', 'datos': data})),
  200,
  headers: {'content-type': 'application/json'},
);

void main() {
  testWidgets('precarga, envía solo lo editable y regresa al guardar', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(1280, 1800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    Map<String, dynamic>? sent;
    final client = MockClient((request) async {
      if (request.url.path == '/api/catalogos/registro') {
        return _ok({
          'paises': ['Argentina', 'México'],
          'nacionalidades': ['Mexicana'],
          'ocupaciones': ['Docente', 'Desempleado(a)'],
        });
      }
      if (request.method == 'PUT') {
        sent = jsonDecode(request.body) as Map<String, dynamic>;
        return _ok({'id': 1});
      }
      return _ok({
        'id': 1,
        'nombre': 'Ana',
        'correo': 'ana@example.com',
        'telefonoMovil': '5512345678',
        'estadoCivil': 'SOLTERO',
        'ocupacion': 'Docente',
        'empresa': 'Escuela',
        'ingresoMensual': 1500.5,
        'domicilio': {
          'calle': 'Reforma',
          'numeroExterior': '12',
          'colonia': 'Centro',
          'municipio': 'Cuauhtémoc',
          'estado': 'CDMX',
          'codigoPostal': '06000',
          'pais': 'México',
        },
      });
    });
    addTearDown(client.close);
    final api = ProfileApiService(
      PaymentApiService(client, _session, baseUrl: 'http://api'),
    );
    bool? result;
    await tester.pumpWidget(
      MaterialApp(
        theme: gestopagoTheme(),
        home: Builder(
          builder: (context) => TextButton(
            onPressed: () async =>
                result = await Navigator.of(context).push<bool>(
                  MaterialPageRoute(builder: (_) => EditProfilePage(api: api)),
                ),
            child: const Text('abrir'),
          ),
        ),
      ),
    );
    await tester.tap(find.text('abrir'));
    await tester.pumpAndSettle();

    expect(find.text('Cuauhtémoc'), findsOneWidget);
    await tester.enterText(
      find.byKey(const ValueKey('telefonoMovil')),
      '5599887766',
    );
    await tester.tap(find.text('Guardar cambios'));
    await tester.pumpAndSettle();

    expect(sent!['telefonoMovil'], '5599887766');
    expect(sent!['ingresoMensual'], '1500.5');
    expect((sent!['domicilio'] as Map)['pais'], 'México');
    expect(sent!.containsKey('nombre'), isFalse);
    expect(sent!.containsKey('correo'), isFalse);
    expect(result, isTrue);
    expect(tester.takeException(), isNull);
  });
}
