import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gestopago_app/validation/input_filters.dart';

/// Aplica los filtros de un campo como si se escribiera [typed] sobre [before].
String type(String key, String typed, {String before = ''}) {
  var value = TextEditingValue(text: before);
  for (final formatter in InputFilters.forField(key)) {
    value = formatter.formatEditUpdate(
      TextEditingValue(text: before),
      TextEditingValue(
        text: value.text == before ? typed : value.text,
        selection: TextSelection.collapsed(offset: typed.length),
      ),
    );
  }
  return value.text;
}

void main() {
  test('nombres: solo letras (con acentos y ñ) y espacios', () {
    expect(type('nombre', 'José María'), 'José María');
    expect(type('apellidoPaterno', 'Muñoz2#-'), 'Muñoz');
    expect(type('segundoNombre', "O'Brien"), 'OBrien');
  });

  test('teléfonos y código postal: solo dígitos', () {
    expect(type('telefonoMovil', '55-12 34a5678'), '5512345678');
    expect(type('codigoPostal', '06 000'), '06000');
  });

  test('CURP y RFC: letras, números, Ñ y &', () {
    expect(type('rfc', 'ÑAB&900101-AB1'), 'ÑAB&900101AB1');
    expect(type('curp', 'mamr 900101#'), 'mamr900101');
  });

  test('correo sin espacios', () {
    expect(type('correo', ' ana @mail.com '), 'ana@mail.com');
  });

  test('ingreso: números con un punto y hasta 2 decimales', () {
    expect(type('ingresoMensual', '1500.5'), '1500.5');
    expect(type('ingresoMensual', '1500.555', before: '1500.55'), '1500.55');
    expect(type('ingresoMensual', '15a', before: '15'), '15');
    expect(type('ingresoMensual', '1.2.3', before: '1.2'), '1.2');
  });

  test('direcciones y empresa quedan libres', () {
    expect(InputFilters.forField('calle'), isEmpty);
    expect(InputFilters.forField('empresa'), isEmpty);
  });
}
