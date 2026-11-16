import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gestopago_app/services/auth_api_service.dart';
import 'package:gestopago_app/services/auth_headers.dart';
import 'package:gestopago_app/services/payment_api_service.dart';
import 'package:gestopago_app/storage/web_session_store.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

const _user = {
  'id': 'id',
  'email': 'web@example.com',
  'identifier': 'web',
  'fullName': 'Cliente Web',
  'rol': 'CLIENTE',
};

http.Response _ok(Object datos) =>
    http.Response(jsonEncode({'codigo': 1, 'datos': datos}), 200);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() {
    FlutterSecureStorage.setMockInitialValues({});
    AuthHeaders.clearCsrf();
  });

  test(
    'login web pide cookie, no guarda JWT y envía CSRF sin Authorization',
    () async {
      final requests = <http.BaseRequest>[];
      final client = MockClient((request) async {
        requests.add(request);
        return switch (request.url.path) {
          '/api/auth/login' => _ok({
            'accessToken': null,
            'tokenType': 'Bearer',
            'expiresAt': DateTime.now()
                .toUtc()
                .add(const Duration(hours: 8))
                .toIso8601String(),
            'user': _user,
          }),
          '/api/auth/csrf' => _ok({
            'headerName': 'X-XSRF-TOKEN',
            'token': 'csrf-123',
          }),
          '/api/cuentas/mias/consulta' => _ok({'contenido': []}),
          _ => http.Response('', 204),
        };
      });
      final api = AuthApiService(client, cookieSession: true);

      final session = await api.login(
        email: 'web@example.com',
        password: 'Segura123!',
      );
      expect(session.usesCookie, isTrue);
      expect(session.accessToken, isEmpty);
      expect(requests.first.headers['X-Auth-Mode'], 'cookie');
      expect(AuthHeaders.csrfToken, 'csrf-123');

      await PaymentApiService(
        client,
        session,
        baseUrl: 'https://api.example.com',
      ).request('/api/cuentas/mias/consulta', {'pagina': 0});
      await api.logout(session);

      for (final request in requests.skip(2)) {
        expect(request.headers.containsKey('Authorization'), isFalse);
        expect(request.headers['X-XSRF-TOKEN'], 'csrf-123');
      }
    },
  );

  test('login móvil conserva Bearer y rechaza respuesta sin token', () async {
    final client = MockClient((request) async {
      expect(request.headers.containsKey('X-Auth-Mode'), isFalse);
      return _ok({
        'accessToken': null,
        'tokenType': 'Bearer',
        'expiresAt': '2030-01-01T00:00:00Z',
        'user': _user,
      });
    });
    await expectLater(
      AuthApiService(
        client,
        cookieSession: false,
      ).login(email: 'a@example.com', password: 'x'),
      throwsA(
        isA<ApiException>().having((e) => e.code, 'code', 'INVALID_RESPONSE'),
      ),
    );
  });

  group('WebSessionStore', () {
    test('sin sesión guardada no consulta la red', () async {
      final client = MockClient(
        (_) async => throw StateError('No debe consultar la red'),
      );
      final store = WebSessionStore(
        AuthApiService(client, cookieSession: true),
      );
      expect(await store.current(), isNull);
    });

    test('restaura con la cookie y obtiene un token CSRF nuevo', () async {
      final client = MockClient((request) async {
        expect(request.headers.containsKey('Authorization'), isFalse);
        return request.url.path == '/api/auth/csrf'
            ? _ok({'headerName': 'X-XSRF-TOKEN', 'token': 'csrf-nuevo'})
            : _ok(_user);
      });
      final store = WebSessionStore(
        AuthApiService(client, cookieSession: true),
      );
      FlutterSecureStorage.setMockInitialValues({
        'gestopago.web-session.expires-at.v1': DateTime.now()
            .toUtc()
            .add(const Duration(hours: 1))
            .toIso8601String(),
      });
      final session = await store.current();
      expect(session?.fullName, 'Cliente Web');
      expect(session?.usesCookie, isTrue);
      expect(AuthHeaders.csrfToken, 'csrf-nuevo');
    });

    test('cookie rechazada limpia la sesión local', () async {
      final client = MockClient((_) async => http.Response('{}', 401));
      final store = WebSessionStore(
        AuthApiService(client, cookieSession: true),
      );
      FlutterSecureStorage.setMockInitialValues({
        'gestopago.web-session.expires-at.v1': DateTime.now()
            .toUtc()
            .add(const Duration(hours: 1))
            .toIso8601String(),
      });
      AuthHeaders.csrfToken = 'viejo';
      expect(await store.current(), isNull);
      expect(AuthHeaders.csrfToken, isNull);
      expect(
        await const FlutterSecureStorage().read(
          key: 'gestopago.web-session.expires-at.v1',
        ),
        isNull,
      );
    });
  });
}
