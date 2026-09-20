import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gestopago_app/screens/login_page.dart';
import 'package:gestopago_app/services/auth_api_service.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  testWidgets('muestra las acciones de login y registro', (tester) async {
    final client = MockClient((_) async => http.Response('{}', 500));
    final authApi = AuthApiService(client);

    await tester.pumpWidget(
      MaterialApp(
        home: LoginPage(
          authApi: authApi,
          onAuthenticated: (_) {},
        ),
      ),
    );

    expect(find.byType(TextFormField), findsNWidgets(2));
    expect(find.text('Iniciar sesion'), findsOneWidget);
    expect(find.text('Crear una cuenta'), findsOneWidget);

    client.close();
  });

  testWidgets('normaliza espacios consecutivos en el nombre', (tester) async {
    final client = MockClient((_) async => http.Response('{}', 500));
    final authApi = AuthApiService(client);

    await tester.pumpWidget(
      MaterialApp(
        home: LoginPage(
          authApi: authApi,
          onAuthenticated: (_) {},
        ),
      ),
    );

    await tester.tap(find.text('Crear una cuenta'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextFormField).first, 'Marco  Martinez');
    await tester.ensureVisible(find.text('Registrarme'));
    await tester.tap(find.text('Registrarme'));
    await tester.pump();

    final nameField = tester.widget<TextFormField>(find.byType(TextFormField).first);
    expect(nameField.controller?.text, 'Marco Martinez');
    expect(find.text('Usa solamente un espacio entre nombres'), findsNothing);
    client.close();
  });
}
