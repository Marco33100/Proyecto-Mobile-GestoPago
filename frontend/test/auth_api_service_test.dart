import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:gestopago_app/models/login_challenge.dart';
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

  test('login solo devuelve el desafio del segundo factor', () async {
    final client = MockClient((request) async {
      expect(request.url.path, '/api/auth/login');
      return http.Response(
        jsonEncode({
          'codigo': 1,
          'mensaje': 'Contrasena verificada. Falta el segundo factor',
          'datos': {
            'desafio': 'desafio-firmado',
            'segundoFactorConfigurado': false,
            'venceEn': '2030-01-01T00:05:00Z',
            'configuracion': {
              'secreto': 'ABCDEFGHIJKLMNOP',
              'uri': 'otpauth://totp/Gestopago:uno?secret=ABCDEFGHIJKLMNOP',
            },
          },
        }),
        200,
      );
    });
    addTearDown(client.close);
    final challenge = await AuthApiService(
      client,
    ).login(email: 'uno@example.com', password: 'Prueba123!');
    expect(challenge.token, 'desafio-firmado');
    expect(challenge.configured, isFalse);
    expect(challenge.secret, 'ABCDEFGHIJKLMNOP');
  });

  test('verificar envia el codigo y lee la sesion y los codigos', () async {
    Map<String, dynamic>? sent;
    final client = MockClient((request) async {
      expect(request.url.path, '/api/auth/login/verificar');
      expect(request.headers.containsKey('X-Auth-Mode'), isFalse);
      sent = jsonDecode(request.body) as Map<String, dynamic>;
      return http.Response(
        jsonEncode({
          'codigo': 1,
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
            'codigosRecuperacion': ['AAAAAAAA-BBBBBBBB-CCCCCCCC-DDDDDDDD'],
          },
        }),
        200,
      );
    });
    addTearDown(client.close);
    final verified = await AuthApiService(client, cookieSession: false)
        .verifyLogin(
          challenge: LoginChallenge(
            token: 'desafio-firmado',
            configured: true,
            expiresAt: DateTime.utc(2030),
          ),
          code: '123456',
        );
    expect(sent, {'desafio': 'desafio-firmado', 'codigo': '123456'});
    expect(verified.session.accessToken, 'token-fixture');
    expect(verified.session.rol, 'CLIENTE');
    expect(verified.recoveryCodes, hasLength(1));
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
