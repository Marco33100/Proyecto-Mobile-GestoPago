import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gestopago_app/theme.dart';
import 'package:gestopago_app/widgets/catalog_product_picker.dart';

void main() {
  for (final size in [const Size(390, 844), const Size(844, 390)]) {
    testWidgets('catálogo filtra con teclado en pantalla de $size', (
      tester,
    ) async {
      tester.view.physicalSize = size;
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.view.resetViewInsets);
      var selections = 0;
      await tester.pumpWidget(
        MaterialApp(
          theme: gestopagoTheme(),
          home: Scaffold(
            body: Padding(
              padding: const EdgeInsets.all(24),
              child: CatalogProductPicker(
                products: const [
                  {'servicio': 'Telcel', 'producto': 'Recarga 10'},
                  {'servicio': 'Agua', 'producto': 'Recibo de agua'},
                ],
                selected: null,
                initialSearch: 'Agua',
                onChanged: (_) => selections++,
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.byKey(const ValueKey('product-selector')));
      await tester.pumpAndSettle();
      expect(find.text('Recarga 10'), findsNothing);
      tester.view.viewInsets = FakeViewPadding(
        bottom: size.height > 500 ? 300 : 200,
      );
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const ValueKey('catalog-search')),
        'TELCEL',
      );
      await tester.pumpAndSettle();
      expect(find.text('Recarga 10'), findsOneWidget);
      expect(find.text('Recibo de agua'), findsNothing);
      expect(selections, 0);
      expect(tester.takeException(), isNull);
    });
  }
}
