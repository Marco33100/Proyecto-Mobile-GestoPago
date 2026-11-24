import 'package:flutter/services.dart';

/// Bloquea al escribir los caracteres que un campo nunca acepta. Las reglas
/// completas (longitudes, formatos) siguen en [ClienteValidators] y en el
/// servidor. Direcciones y empresa quedan libres: llevan números y símbolos.
abstract final class InputFilters {
  static final _name = FilteringTextInputFormatter.allow(
    RegExp(r'[\p{L} ]', unicode: true),
  );
  static final _digits = FilteringTextInputFormatter.digitsOnly;
  static final _idCode = FilteringTextInputFormatter.allow(
    RegExp(r'[A-Za-z0-9Ññ&]'),
  );
  static final _noSpaces = FilteringTextInputFormatter.deny(RegExp(r'\s'));

  /// Hasta 17 enteros y 2 decimales; una tecla que rompe el formato se ignora.
  static final _money = TextInputFormatter.withFunction(
    (oldValue, newValue) =>
        RegExp(r'^\d{0,17}(\.\d{0,2})?$').hasMatch(newValue.text)
        ? newValue
        : oldValue,
  );

  static List<TextInputFormatter> forField(String key) => switch (key) {
    'nombre' ||
    'segundoNombre' ||
    'apellidoPaterno' ||
    'apellidoMaterno' => [_name],
    'telefonoMovil' || 'telefonoAlternativo' || 'codigoPostal' => [_digits],
    'curp' || 'rfc' => [_idCode],
    'correo' => [_noSpaces],
    'ingresoMensual' => [_money],
    _ => const [],
  };
}
