import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:gestopago_app/services/auth_api_service.dart';

void main() {
  test(
    'muestra codigo y mensaje del validador sin perder el HTTP 400',
    () async {
      const message = 'Datos no validos: {email=El correo no es valido}';
      final client = MockClient(
        (_) async => http.Response(
          jsonEncode({'codigo': 2, 'mensaje': message}),
          400,
          headers: {'content-type': 'application/json; charset=utf-8'},
        ),
      );
      addTearDown(client.close);
      await expectLater(
        AuthApiService(client).login(email: 'invalido', password: 'Prueba123!'),
        throwsA(
          isA<ApiException>()
              .having((error) => error.statusCode, 'statusCode', 400)
              .having((error) => error.code, 'code', '2')
              .having((error) => error.message, 'message', message),
        ),
      );
    },
  );

  test(
    'mantiene compatibilidad con validaciones de la version anterior',
    () async {
      final client = MockClient(
        (_) async => http.Response(
          jsonEncode({
            'code': 'AUTH_INVALID_PAYLOAD',
            'fieldErrors': {'email': 'El correo no es valido'},
          }),
          400,
        ),
      );
      addTearDown(client.close);
      await expectLater(
        AuthApiService(client).login(email: 'invalido', password: 'Prueba123!'),
        throwsA(
          isA<ApiException>()
              .having((error) => error.code, 'code', 'AUTH_INVALID_PAYLOAD')
              .having(
                (error) => error.message,
                'message',
                'El correo no es valido',
              ),
        ),
      );
    },
  );

  test('mensaje vacio conserva respuesta amigable para error 400', () async {
    final client = MockClient(
      (_) async =>
          http.Response(jsonEncode({'codigo': 2, 'mensaje': ' '}), 400),
    );
    addTearDown(client.close);
    await expectLater(
      AuthApiService(client).login(email: 'invalido', password: 'Prueba123!'),
      throwsA(
        isA<ApiException>().having(
          (error) => error.message,
          'message',
          'Revisa los datos ingresados.',
        ),
      ),
    );
  });

  for (final status in [401, 403, 404, 409, 429]) {
    test('muestra codigo y mensaje del backend con HTTP $status', () async {
      const message = 'Mensaje publico seguro';
      final client = MockClient(
        (_) async => http.Response(
          jsonEncode({'codigo': 2, 'mensaje': message}),
          status,
        ),
      );
      addTearDown(client.close);
      await expectLater(
        AuthApiService(
          client,
        ).login(email: 'uno@example.com', password: 'Prueba123!'),
        throwsA(
          isA<ApiException>()
              .having((error) => error.statusCode, 'statusCode', status)
              .having((error) => error.code, 'code', '2')
              .having((error) => error.message, 'message', message),
        ),
      );
    });
  }

  test('login lee la sesion de datos con codigo 1', () async {
    final client = MockClient(
      (_) async => http.Response(
        jsonEncode({
          'codigo': 1,
          'mensaje': 'Inicio de sesion correcto',
          'datos': {
            'accessToken': 'token-fixture',
            'tokenType': 'Bearer',
            'expiresAt': '2030-01-01T00:00:00Z',
            'user': {
              'id': 'usuario-fixture',
              'email': 'uno@example.com',
              'identifier': 'uno@example.com',
              'fullName': 'Usuario Prueba',
              'rol': 'CLIENTE',
            },
          },
        }),
        200,
      ),
    );
    addTearDown(client.close);
    final session = await AuthApiService(
      client,
    ).login(email: 'uno@example.com', password: 'Prueba123!');
    expect(session.accessToken, 'token-fixture');
    expect(session.userId, 'usuario-fixture');
    expect(session.rol, 'CLIENTE');
  });

  test(
    'HTTP exitoso con codigo de error no se interpreta como login',
    () async {
      final client = MockClient(
        (_) async => http.Response(
          jsonEncode({'codigo': 2, 'mensaje': 'Respuesta inconsistente'}),
          200,
        ),
      );
      addTearDown(client.close);
      await expectLater(
        AuthApiService(
          client,
        ).login(email: 'uno@example.com', password: 'Prueba123!'),
        throwsA(
          isA<ApiException>().having(
            (error) => error.code,
            'code',
            'INVALID_RESPONSE',
          ),
        ),
      );
    },
  );
}
