import 'dart:convert';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gestopago_app/models/login_session.dart';
import 'package:gestopago_app/services/auth_api_service.dart';
import 'package:gestopago_app/storage/secure_session_store.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const storage = FlutterSecureStorage();
  const key = 'gestopago.session.credential.v2';
  final session = LoginSession(
    accessToken: 'token-prueba',
    tokenType: 'Bearer',
    expiresAt: DateTime.now().toUtc().add(const Duration(hours: 1)),
    userId: 'id',
    email: 'prueba@example.com',
    identifier: 'prueba',
    fullName: 'Nombre anterior',
    rol: 'CLIENTE',
  );
  setUp(() => FlutterSecureStorage.setMockInitialValues({}));

  test(
    'guarda solo credencial y recupera perfil vigente del servidor',
    () async {
      final client = MockClient((request) async {
        expect(request.url.path, '/api/auth/sesion');
        expect(request.headers['Authorization'], 'Bearer token-prueba');
        return http.Response(
          jsonEncode({
            'codigo': 1,
            'datos': {
              'id': 'id',
              'email': 'nuevo@example.com',
              'identifier': 'prueba',
              'fullName': 'Nombre vigente',
              'rol': 'EJECUTIVO',
            },
          }),
          200,
        );
      });
      addTearDown(client.close);
      final store = SecureSessionStore(
        AuthApiService(client),
        storage: storage,
      );
      await store.save(session);
      final persisted = (await storage.read(key: key))!;
      expect(persisted, isNot(contains('Nombre anterior')));
      expect(persisted, isNot(contains('prueba@example.com')));
      final restored = await store.current();
      expect(restored?.fullName, 'Nombre vigente');
      expect(restored?.isExecutive, isTrue);
    },
  );

  test(
    'sesion rechazada se elimina; fallo temporal conserva credencial',
    () async {
      var status = 503;
      final client = MockClient((_) async => http.Response('{}', status));
      addTearDown(client.close);
      final store = SecureSessionStore(
        AuthApiService(client),
        storage: storage,
      );
      await store.save(session);
      await expectLater(store.current(), throwsA(isA<ApiException>()));
      expect(await storage.read(key: key), isNotNull);
      status = 401;
      expect(await store.current(), isNull);
      expect(await storage.read(key: key), isNull);
    },
  );

  for (final status in [403, 404]) {
    test(
      'usuario inactivo o inexistente ($status) elimina credencial',
      () async {
        final client = MockClient((_) async => http.Response('{}', status));
        addTearDown(client.close);
        final store = SecureSessionStore(
          AuthApiService(client),
          storage: storage,
        );
        await store.save(session);
        expect(await store.current(), isNull);
        expect(await storage.read(key: key), isNull);
      },
    );
  }

  test('credencial vencida y credencial heredada se eliminan', () async {
    FlutterSecureStorage.setMockInitialValues({
      'gestopago.session.secret.v1': 'antigua',
    });
    final client = MockClient(
      (_) async => throw StateError('No debe consultar la red'),
    );
    addTearDown(client.close);
    final store = SecureSessionStore(AuthApiService(client), storage: storage);
    expect(await store.current(), isNull);
    expect(await storage.read(key: 'gestopago.session.secret.v1'), isNull);
    await store.save(
      LoginSession(
        accessToken: 'expirada',
        tokenType: 'Bearer',
        expiresAt: DateTime.now().toUtc().subtract(const Duration(seconds: 1)),
        userId: 'id',
        email: 'x',
        identifier: 'x',
        fullName: 'x',
      ),
    );
    expect(await store.current(), isNull);
    expect(await storage.read(key: key), isNull);
  });
}
