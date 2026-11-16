import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gestopago_app/models/login_session.dart';
import 'package:gestopago_app/screens/login_page.dart';
import 'package:gestopago_app/services/auth_api_service.dart';
import 'package:gestopago_app/storage/session_store.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:qr_flutter/qr_flutter.dart';

class MemoryStore implements SessionStore {
  LoginSession? saved;
  @override
  Future<void> save(LoginSession session) async => saved = session;
  @override
  Future<LoginSession?> current() async => saved;
  @override
  Future<void> clear() async => saved = null;
}

http.Response _ok(Object datos) =>
    http.Response(jsonEncode({'codigo': 1, 'datos': datos}), 200);

Map<String, dynamic> _session({List<String>? recovery}) => {
  'accessToken': 'jwt-sesion',
  'tokenType': 'Bearer',
  'expiresAt': '2030-01-01T00:00:00Z',
  'user': {
    'id': 'u',
    'email': 'ana@example.com',
    'identifier': 'ana',
    'fullName': 'Ana López',
    'rol': 'EJECUTIVO',
  },
  'codigosRecuperacion': ?recovery,
};

void main() {
  late MemoryStore store;
  late List<String> paths;
  LoginSession? authenticated;

  setUp(() {
    store = MemoryStore();
    paths = [];
    authenticated = null;
  });

  Future<void> pumpLogin(
    WidgetTester tester,
    Future<http.Response> Function(http.Request) handler,
  ) async {
    tester.view.physicalSize = const Size(800, 1400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final client = MockClient((request) {
      paths.add(request.url.path);
      return handler(request);
    });
    addTearDown(client.close);
    await tester.pumpWidget(
      MaterialApp(
        home: LoginPage(
          authApi: AuthApiService(client, cookieSession: false),
          sessionStore: store,
          onAuthenticated: (session) => authenticated = session,
        ),
      ),
    );
  }

  Future<void> enterCredentials(WidgetTester tester) async {
    await tester.enterText(find.byType(TextFormField).at(0), 'ana@example.com');
    await tester.enterText(find.byType(TextFormField).at(1), 'Segura123!');
    await tester.tap(find.text('Iniciar sesión'));
    await tester.pumpAndSettle();
  }

  Future<void> tapVisible(WidgetTester tester, Finder finder) async {
    await tester.ensureVisible(finder);
    await tester.pumpAndSettle();
    await tester.tap(finder);
    await tester.pumpAndSettle();
  }

  testWidgets('con autenticador pide el codigo y solo entonces entra', (
    tester,
  ) async {
    await pumpLogin(tester, (request) async {
      if (request.url.path == '/api/auth/login') {
        return _ok({
          'desafio': 'd',
          'segundoFactorConfigurado': true,
          'venceEn': '2030-01-01T00:05:00Z',
        });
      }
      expect(jsonDecode(request.body), {'desafio': 'd', 'codigo': '123456'});
      return _ok(_session());
    });
    await enterCredentials(tester);

    expect(find.text('Verificación en dos pasos'), findsOneWidget);
    expect(authenticated, isNull);
    expect(store.saved, isNull);

    await tester.enterText(find.byKey(const ValueKey('totp-code')), '123456');
    await tapVisible(tester, find.text('Verificar'));

    expect(paths, ['/api/auth/login', '/api/auth/login/verificar']);
    expect(authenticated?.accessToken, 'jwt-sesion');
    expect(store.saved?.accessToken, 'jwt-sesion');
    expect(tester.takeException(), isNull);
  });

  testWidgets('sin autenticador muestra el QR y los codigos antes de entrar', (
    tester,
  ) async {
    await pumpLogin(tester, (request) async {
      if (request.url.path == '/api/auth/login') {
        return _ok({
          'desafio': 'd',
          'segundoFactorConfigurado': false,
          'venceEn': '2030-01-01T00:05:00Z',
          'configuracion': {
            'secreto': 'ABCDEFGHIJKLMNOPQRSTUVWX',
            'uri':
                'otpauth://totp/Gestopago:ana?secret=ABCDEFGHIJKLMNOPQRSTUVWX',
          },
        });
      }
      return _ok(_session(recovery: ['AAAAAAAA-BBBBBBBB-CCCCCCCC-DDDDDDDD']));
    });
    await enterCredentials(tester);

    expect(find.text('Protege tu cuenta'), findsOneWidget);
    expect(find.byType(QrImageView), findsOneWidget);
    expect(find.text('ABCD EFGH IJKL MNOP QRST UVWX'), findsOneWidget);

    await tester.enterText(find.byKey(const ValueKey('totp-code')), '654321');
    await tapVisible(tester, find.text('Activar y entrar'));

    expect(find.text('Guarda tus códigos de recuperación'), findsOneWidget);
    expect(find.text('AAAAAAAA-BBBBBBBB-CCCCCCCC-DDDDDDDD'), findsOneWidget);
    expect(authenticated, isNull, reason: 'primero debe guardar los códigos');

    await tapVisible(tester, find.text('Ya los guardé, entrar'));
    expect(authenticated?.accessToken, 'jwt-sesion');
    expect(tester.takeException(), isNull);
  });

  testWidgets('codigo incorrecto muestra el error y no entra', (tester) async {
    await pumpLogin(tester, (request) async {
      if (request.url.path == '/api/auth/login') {
        return _ok({
          'desafio': 'd',
          'segundoFactorConfigurado': true,
          'venceEn': '2030-01-01T00:05:00Z',
        });
      }
      return http.Response(
        jsonEncode({
          'codigo': 2,
          'mensaje': 'El codigo no es correcto o la verificacion vencio.',
        }),
        401,
      );
    });
    await enterCredentials(tester);
    await tester.enterText(find.byKey(const ValueKey('totp-code')), '000000');
    await tapVisible(tester, find.text('Verificar'));

    expect(
      find.text('El codigo no es correcto o la verificacion vencio.'),
      findsOneWidget,
    );
    expect(find.text('Verificación en dos pasos'), findsOneWidget);
    expect(authenticated, isNull);
    expect(store.saved, isNull);
  });

  testWidgets('valida el formato del codigo antes de llamar al servidor', (
    tester,
  ) async {
    await pumpLogin(
      tester,
      (_) async => _ok({
        'desafio': 'd',
        'segundoFactorConfigurado': true,
        'venceEn': '2030-01-01T00:05:00Z',
      }),
    );
    await enterCredentials(tester);
    await tester.enterText(find.byKey(const ValueKey('totp-code')), '12');
    await tapVisible(tester, find.text('Verificar'));
    expect(find.text('Escribe los seis dígitos del código.'), findsOneWidget);
    expect(paths, ['/api/auth/login']);

    await tapVisible(
      tester,
      find.text('Perdí mi teléfono: usar un código de recuperación'),
    );
    await tester.enterText(
      find.byKey(const ValueKey('recovery-code')),
      'no-valido',
    );
    await tapVisible(tester, find.text('Verificar'));
    expect(
      find.text(
        'El código de recuperación tiene cuatro grupos de ocho caracteres.',
      ),
      findsOneWidget,
    );
    expect(paths, ['/api/auth/login']);
  });

  testWidgets('volver regresa al formulario de contraseña', (tester) async {
    await pumpLogin(
      tester,
      (_) async => _ok({
        'desafio': 'd',
        'segundoFactorConfigurado': true,
        'venceEn': '2030-01-01T00:05:00Z',
      }),
    );
    await enterCredentials(tester);
    await tapVisible(tester, find.text('Volver a iniciar sesión'));
    expect(find.text('Iniciar sesión'), findsOneWidget);
    expect(find.byType(TextFormField), findsNWidgets(2));
  });
}
