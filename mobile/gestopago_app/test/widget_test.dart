import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gestopago_app/models/login_session.dart';
import 'package:gestopago_app/screens/home_page.dart';
import 'package:gestopago_app/screens/login_page.dart';
import 'package:gestopago_app/screens/register_page.dart';
import 'package:gestopago_app/services/auth_api_service.dart';
import 'package:gestopago_app/validation/cliente_validators.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

LoginSession session({String rol = 'EJECUTIVO'}) => LoginSession(
  accessToken: 'token-prueba',
  tokenType: 'Bearer',
  expiresAt: DateTime.now().add(const Duration(hours: 1)),
  userId: 'id-ejecutivo',
  email: 'ejecutivo@example.com',
  identifier: 'ejecutivo@example.com',
  fullName: 'Ejecutivo',
  rol: rol,
);

Future<void> fillForm(
  WidgetTester tester, {
  String password = 'Segura123!',
}) async {
  final values = {
    'nombre': '  Marco     Antonio  ',
    'apellidoPaterno': 'Martinez',
    'apellidoMaterno': 'Lopez',
    'fechaNacimiento': '1990-01-01',
    'curp': 'MALM900101HDFRPR01',
    'rfc': 'MALM900101AB1',
    'nacionalidad': 'Mexicana',
    'correo': '  MARCO@example.com ',
    'telefonoMovil': '5512345678',
    'calle': 'Reforma',
    'numeroExterior': '12',
    'colonia': 'Centro',
    'municipio': 'Cuauhtemoc',
    'estado': 'CDMX',
    'codigoPostal': '01234',
    'pais': 'Mexico',
    'ocupacion': 'Ingeniero',
    'empresa': 'Empresa',
    'ingresoMensual': '15000.50',
    'contrasena': password,
    'confirmarContrasena': password,
  };
  for (final field in values.entries) {
    final finder = find.byKey(ValueKey(field.key));
    await tester.ensureVisible(finder);
    if (field.key == 'fechaNacimiento') {
      await chooseBirthDate(tester);
    } else {
      await tester.enterText(finder, field.value);
    }
  }
  FocusManager.instance.primaryFocus?.unfocus();
  await tester.pumpAndSettle();
  await tester.ensureVisible(find.text('Registrar cliente').last);
  await tester.pumpAndSettle();
}

Future<void> chooseBirthDate(WidgetTester tester) async {
  final field = find.byKey(const ValueKey('fechaNacimiento'));
  await tester.ensureVisible(field);
  await tester.tap(field);
  await tester.pumpAndSettle();
  final dialog = tester.widget<DatePickerDialog>(find.byType(DatePickerDialog));
  final years = find.descendant(
    of: find.byType(YearPicker),
    matching: find.byType(Scrollable),
  );
  await tester.scrollUntilVisible(
    find.text('1990'),
    -300,
    scrollable: years.first,
    maxScrolls: 30,
  );
  await tester.tap(find.text('1990'));
  await tester.pumpAndSettle();
  for (var month = dialog.initialDate!.month; month > 1; month--) {
    await tester.tap(find.byTooltip('Previous month'));
    await tester.pumpAndSettle();
  }
  await tester.tap(find.text('1'));
  await tester.tap(find.text('Seleccionar'));
  await tester.pumpAndSettle();
}

void main() {
  testWidgets(
    'calendario sin hora, solo adultos y cancelar conserva la fecha',
    (tester) async {
      tester.view.physicalSize = const Size(390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      var requests = 0;
      final client = MockClient((_) async {
        requests++;
        return http.Response('{}', 500);
      });
      addTearDown(client.close);
      await tester.pumpWidget(
        MaterialApp(
          home: RegisterPage(
            session: session(),
            authApi: AuthApiService(client),
          ),
        ),
      );
      final finder = find.byKey(const ValueKey('fechaNacimiento'));
      var field = tester.widget<TextFormField>(finder);
      final input = tester.widget<TextField>(
        find.descendant(of: finder, matching: find.byType(TextField)),
      );
      expect(input.readOnly, isTrue);
      expect(input.enableInteractiveSelection, isFalse);
      await tester.ensureVisible(finder);
      await tester.tap(find.byTooltip('Elegir fecha de nacimiento'));
      await tester.pumpAndSettle();
      var dialog = tester.widget<DatePickerDialog>(
        find.byType(DatePickerDialog),
      );
      expect(dialog.initialEntryMode, DatePickerEntryMode.calendarOnly);
      expect(dialog.lastDate, ClienteValidators.latestBirthDate());
      expect(tester.testTextInput.isVisible, isFalse);
      await tester.tap(find.text('Cancelar'));
      await tester.pumpAndSettle();
      expect(field.controller!.text, isEmpty);

      await chooseBirthDate(tester);
      field = tester.widget<TextFormField>(finder);
      expect(field.controller!.text, '1990-01-01');
      await tester.tap(finder);
      await tester.pumpAndSettle();
      dialog = tester.widget<DatePickerDialog>(find.byType(DatePickerDialog));
      expect(dialog.initialDate, DateTime(1990, 1, 1));
      await tester.tap(find.text('Cancelar'));
      await tester.pumpAndSettle();
      expect(field.controller!.text, '1990-01-01');
      expect(requests, 0);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('calendario funciona en horizontal sin abrir dos dialogos', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(844, 390);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final client = MockClient((_) async => http.Response('{}', 500));
    addTearDown(client.close);
    await tester.pumpWidget(
      MaterialApp(
        home: RegisterPage(session: session(), authApi: AuthApiService(client)),
      ),
    );
    final field = find.byKey(const ValueKey('fechaNacimiento'));
    await tester.ensureVisible(field);
    await tester.tap(field);
    // Segunda activacion antes de que la primera apertura termine.
    tester
        .widget<TextField>(
          find.descendant(of: field, matching: find.byType(TextField)),
        )
        .onTap!();
    await tester.pumpAndSettle();
    expect(find.byType(DatePickerDialog), findsOneWidget);
    await tester.tap(find.text('Cancelar'));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });

  testWidgets('login rechaza datos largos antes de llamar API', (tester) async {
    var requests = 0;
    final client = MockClient((_) async {
      requests++;
      return http.Response('{}', 500);
    });
    addTearDown(client.close);
    await tester.pumpWidget(
      MaterialApp(
        home: LoginPage(
          authApi: AuthApiService(client),
          onAuthenticated: (_) {},
        ),
      ),
    );
    await tester.enterText(
      find.byType(TextFormField).at(0),
      '${'a' * 255}@b.com',
    );
    await tester.enterText(find.byType(TextFormField).at(1), 'A' * 73);
    await tester.tap(find.text('Iniciar sesion'));
    await tester.pump();
    expect(requests, 0);
    expect(
      find.text('El correo no puede superar 254 caracteres'),
      findsOneWidget,
    );
    expect(
      find.text('La contrasena no puede superar 72 caracteres'),
      findsOneWidget,
    );
  });
  testWidgets('login no ofrece el registro basico independiente', (
    tester,
  ) async {
    final client = MockClient((_) async => http.Response('{}', 500));
    addTearDown(client.close);
    await tester.pumpWidget(
      MaterialApp(
        home: LoginPage(
          authApi: AuthApiService(client),
          onAuthenticated: (_) {},
        ),
      ),
    );
    expect(find.byType(TextFormField), findsNWidgets(2));
    expect(find.text('Iniciar sesion'), findsOneWidget);
    expect(find.text('Crear una cuenta'), findsNothing);
    expect(find.textContaining('ejecutivo'), findsOneWidget);
  });

  testWidgets(
    'solo ejecutivo ve accion de alta, cliente no puede abrir formulario',
    (tester) async {
      final client = MockClient((_) async => http.Response('{}', 500));
      addTearDown(client.close);
      final api = AuthApiService(client);
      await tester.pumpWidget(
        MaterialApp(
          home: HomePage(
            session: session(rol: 'CLIENTE'),
            authApi: api,
            onLogout: () async {},
          ),
        ),
      );
      expect(find.text('Registrar cliente'), findsNothing);
      await tester.pumpWidget(
        MaterialApp(
          home: RegisterPage(
            session: session(rol: 'CLIENTE'),
            authApi: api,
          ),
        ),
      );
      expect(find.byType(TextFormField), findsNothing);
      expect(find.textContaining('Solo un ejecutivo'), findsOneWidget);
    },
  );

  testWidgets('contrasena debil no llama API; nombres se normalizan', (
    tester,
  ) async {
    var requests = 0;
    final client = MockClient((_) async {
      requests++;
      return http.Response('{}', 500);
    });
    addTearDown(client.close);
    await tester.pumpWidget(
      MaterialApp(
        home: RegisterPage(session: session(), authApi: AuthApiService(client)),
      ),
    );
    await fillForm(tester, password: 'Segura123');
    await tester.tap(find.text('Registrar cliente'));
    await tester.pump();
    expect(requests, 0);
    expect(find.text('Incluye un caracter especial'), findsOneWidget);
    expect(
      tester
          .widget<TextFormField>(find.byKey(const ValueKey('nombre')))
          .controller!
          .text,
      'Marco Antonio',
    );
  });

  testWidgets(
    'alta completa usa clientes, token ejecutivo y no reemplaza su sesion',
    (tester) async {
      final response = Completer<http.Response>();
      var requests = 0;
      Map<String, dynamic>? payload;
      final client = MockClient((request) async {
        requests++;
        expect(request.url.path, '/api/clientes');
        expect(request.headers['Authorization'], 'Bearer token-prueba');
        payload = jsonDecode(request.body) as Map<String, dynamic>;
        return response.future;
      });
      addTearDown(client.close);
      final api = AuthApiService(client);
      await tester.pumpWidget(
        MaterialApp(
          home: HomePage(
            session: session(),
            authApi: api,
            onLogout: () async {},
          ),
        ),
      );
      await tester.tap(find.text('Registrar cliente'));
      await tester.pumpAndSettle();
      await fillForm(tester);
      await tester.tap(find.text('Registrar cliente').last);
      await tester.pump();
      expect(find.byType(CircularProgressIndicator), findsOneWidget);
      expect(requests, 1);
      expect(payload!['nombre'], 'Marco Antonio');
      expect(payload!['correo'], 'marco@example.com');
      expect(payload!['fechaNacimiento'], '1990-01-01');
      expect(payload!['ingresoMensual'], '15000.50');
      expect(payload!['segundoNombre'], isNull);
      expect(payload!['telefonoAlternativo'], isNull);
      expect(payload!['domicilio']['codigoPostal'], '01234');
      expect(payload!['domicilio']['numeroInterior'], isNull);
      expect(payload!.containsKey('rol'), isFalse);
      expect(payload!.containsKey('numeroCuenta'), isFalse);
      expect(payload!.containsKey('saldo'), isFalse);
      response.complete(
        http.Response('{"cuentas":[{"numeroCuenta":"CTA-123"}]}', 201),
      );
      await tester.pumpAndSettle();
      expect(find.text('Bienvenido, Ejecutivo'), findsOneWidget);
      expect(find.text('Cliente registrado. Cuenta: CTA-123'), findsOneWidget);
    },
  );

  testWidgets(
    'conflicto conserva formulario y permite corregir sin duplicar peticiones',
    (tester) async {
      final client = MockClient(
        (_) async => http.Response(
          '{"code":"DUPLICATE_CURP","message":"La CURP ya esta registrada"}',
          409,
        ),
      );
      addTearDown(client.close);
      await tester.pumpWidget(
        MaterialApp(
          home: RegisterPage(
            session: session(),
            authApi: AuthApiService(client),
          ),
        ),
      );
      await fillForm(tester);
      await tester.tap(find.text('Registrar cliente'));
      await tester.pumpAndSettle();
      expect(find.text('La CURP ya esta registrada'), findsOneWidget);
      expect(
        tester
            .widget<TextFormField>(find.byKey(const ValueKey('correo')))
            .enabled,
        isTrue,
      );
      expect(find.byType(CircularProgressIndicator), findsNothing);
    },
  );

  test('API traduce sesion invalida y fallos sin exponer detalles', () async {
    for (final status in [401, 403, 500]) {
      final client = MockClient(
        (_) async => http.Response(
          '{"code":"AUTH_INVALID_SESSION","message":"detalle interno"}',
          status,
        ),
      );
      final api = AuthApiService(client);
      await expectLater(
        api.registerClient(session: session(), payload: {}),
        throwsA(
          isA<ApiException>()
              .having((e) => e.statusCode, 'status', status)
              .having(
                (e) => e.message,
                'mensaje seguro',
                isNot(contains('detalle interno')),
              ),
        ),
      );
      client.close();
    }
  });

  test(
    'perfil y rol sobreviven ida y vuelta SQLite; perfiles anteriores no son ejecutivos',
    () {
      final original = session();
      expect(
        LoginSession.fromDatabaseMap(
          original.toDatabaseMap(sessionReference: 'fixture'),
          accessToken: original.accessToken,
        ).isExecutive,
        isTrue,
      );
      final legacy = original.toDatabaseMap(sessionReference: 'fixture')
        ..remove('rol');
      expect(
        LoginSession.fromDatabaseMap(
          legacy,
          accessToken: original.accessToken,
        ).isExecutive,
        isFalse,
      );
    },
  );
}
