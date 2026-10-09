import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gestopago_app/main.dart';
import 'package:gestopago_app/models/login_session.dart';
import 'package:gestopago_app/screens/home_page.dart';
import 'package:gestopago_app/screens/login_page.dart';
import 'package:gestopago_app/services/auth_api_service.dart';
import 'package:gestopago_app/storage/session_store.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

LoginSession testSession() => LoginSession(
      accessToken: 'token-prueba',
      tokenType: 'Bearer',
      expiresAt: DateTime.now().add(const Duration(hours: 1)),
      userId: 'usuario',
      email: 'uno@example.com',
      identifier: 'uno@example.com',
      fullName: 'Marco Martinez',
    );

class FakeSessionStore implements SessionStore {
  LoginSession? session;
  bool failRead = false;
  bool failClear = false;
  int clears = 0;
  @override
  Future<LoginSession?> current() async {
    if (failRead) throw StateError('SQLite no disponible');
    return session;
  }

  @override
  Future<void> save(LoginSession value) async => session = value;
  @override
  Future<void> clear() async {
    clears++;
    if (failClear) throw StateError('SQLite no disponible');
    session = null;
  }
}

void main() {
  testWidgets('fallo al restaurar SQLite siempre termina la carga',
      (tester) async {
    final store = FakeSessionStore()..failRead = true;
    final client = MockClient((_) async => http.Response('', 204));
    addTearDown(client.close);
    await tester.pumpWidget(MaterialApp(
        home: SessionGate(
      authApi: AuthApiService(client),
      sessionStore: store,
    )));
    await tester.pumpAndSettle();
    expect(find.byType(CircularProgressIndicator), findsNothing);
    expect(find.byType(LoginPage), findsOneWidget);
    expect(find.textContaining('No fue posible recuperar'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('logout revoca en API antes de borrar SQLite y evita doble envio',
      (tester) async {
    final store = FakeSessionStore()..session = testSession();
    final response = Completer<http.Response>();
    var requests = 0;
    final client = MockClient((request) async {
      requests++;
      expect(request.url.path, '/api/auth/logout');
      expect(request.headers['Authorization'], 'Bearer token-prueba');
      expect(store.clears, 0);
      return response.future;
    });
    addTearDown(client.close);
    await tester.pumpWidget(MaterialApp(
        home: SessionGate(
      authApi: AuthApiService(client),
      sessionStore: store,
    )));
    await tester.pumpAndSettle();
    expect(find.byType(HomePage), findsOneWidget);
    await tester.tap(find.byTooltip('Cerrar sesion'));
    await tester.pump();
    await tester.tap(find.byTooltip('Cerrar sesion'));
    expect(requests, 1);
    response.complete(http.Response('', 204));
    await tester.pumpAndSettle();
    expect(find.byType(LoginPage), findsOneWidget);
    expect(store.session, isNull);
    expect(store.clears, 1);
  });

  testWidgets('sin red limpia local y avisa que no confirmo revocacion remota',
      (tester) async {
    final store = FakeSessionStore()..session = testSession();
    final client =
        MockClient((_) async => throw const SocketException('offline'));
    addTearDown(client.close);
    await tester.pumpWidget(MaterialApp(
        home: SessionGate(
      authApi: AuthApiService(client),
      sessionStore: store,
    )));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Cerrar sesion'));
    await tester.pumpAndSettle();
    expect(store.session, isNull);
    expect(find.byType(LoginPage), findsOneWidget);
    expect(find.textContaining('La sesion remota podria seguir activa'),
        findsOneWidget);
  });

  testWidgets('fallo al borrar SQLite no deja al usuario atrapado en inicio',
      (tester) async {
    final store = FakeSessionStore()
      ..session = testSession()
      ..failClear = true;
    final client = MockClient((_) async => http.Response('', 204));
    addTearDown(client.close);
    await tester.pumpWidget(MaterialApp(
        home: SessionGate(
      authApi: AuthApiService(client),
      sessionStore: store,
    )));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Cerrar sesion'));
    await tester.pumpAndSettle();
    expect(find.byType(LoginPage), findsOneWidget);
    expect(find.textContaining('No fue posible borrar'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  test('logout ya revocado acepta 401, pero error servidor no se oculta',
      () async {
    final expired = MockClient((_) async => http.Response('', 401));
    final offline =
        MockClient((_) async => http.Response('<html>proxy</html>', 503));
    addTearDown(expired.close);
    addTearDown(offline.close);
    await AuthApiService(expired).logout(testSession());
    await expectLater(
        AuthApiService(offline).logout(testSession()),
        throwsA(
          isA<ApiException>().having((e) => e.statusCode, 'status', 503),
        ));
  });

  test('JSON con tipos incorrectos se convierte en error controlado', () async {
    final client = MockClient((_) async => http.Response(
          '{"accessToken":42,"expiresAt":"2026-10-03","user":{}}',
          200,
        ));
    addTearDown(client.close);
    await expectLater(
        AuthApiService(client)
            .login(email: 'uno@example.com', password: 'Segura123!'),
        throwsA(isA<ApiException>()
            .having((e) => e.code, 'code', 'INVALID_RESPONSE')));
  });

  test('timeout aplica tambien al cierre de sesion', () async {
    final response = Completer<http.Response>();
    final client = MockClient((_) => response.future);
    addTearDown(client.close);
    await expectLater(
        AuthApiService(client, requestTimeout: const Duration(milliseconds: 5))
            .logout(testSession()),
        throwsA(isA<ApiException>()
            .having((e) => e.code, 'code', 'CONNECTION_TIMEOUT')));
    response.complete(http.Response('', 204));
  });
}
