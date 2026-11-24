import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gestopago_app/theme.dart';
import 'package:gestopago_app/widgets/debit_card.dart';

void main() {
  Future<void> pumpCard(WidgetTester tester, double width) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: gestopagoTheme(),
        home: Scaffold(
          body: Center(
            child: SizedBox(
              width: width,
              child: const DebitCard(
                account: {
                  'numeroCuenta': 'CTA-0123456789ABCDEF0123',
                  'saldoDisponible': 5000,
                },
              ),
            ),
          ),
        ),
      ),
    );
  }

  testWidgets('oculta el número y el ojo lo muestra y lo vuelve a ocultar', (
    tester,
  ) async {
    await pumpCard(tester, 420);
    expect(find.text('CTA •••• •••• •••• •••• 0123'), findsOneWidget);
    expect(find.text(r'$5,000.00 MXN'), findsOneWidget);

    await tester.tap(find.byTooltip('Mostrar número de cuenta'));
    await tester.pump();
    expect(find.text('CTA 0123 4567 89AB CDEF 0123'), findsOneWidget);

    await tester.tap(find.byTooltip('Ocultar número de cuenta'));
    await tester.pump();
    expect(find.text('CTA •••• •••• •••• •••• 0123'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('muestra lo apartado por un pago pendiente', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: gestopagoTheme(),
        home: const Scaffold(
          body: SizedBox(
            width: 420,
            child: DebitCard(
              account: {
                'numeroCuenta': 'CTA-0123456789ABCDEF0123',
                'saldoDisponible': 4701,
                'saldoRetenido': 299,
              },
            ),
          ),
        ),
      ),
    );
    expect(find.text(r'$4,701.00 MXN'), findsOneWidget);
    expect(
      find.text(r'$299.00 MXN apartado por un pago pendiente'),
      findsOneWidget,
    );
  });

  testWidgets('cabe en un teléfono angosto sin desbordarse', (tester) async {
    await pumpCard(tester, 300);
    await tester.tap(find.byTooltip('Mostrar número de cuenta'));
    await tester.pump();
    expect(tester.takeException(), isNull);
  });
}
