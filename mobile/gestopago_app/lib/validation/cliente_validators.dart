import 'dart:convert';

import '../utils/date_only.dart';

/// Validaciones de UX. El backend vuelve a validar todo antes de guardar.
abstract final class ClienteValidators {
  static String normalizeText(String value) =>
      value.trim().replaceAll(RegExp(r'\s+'), ' ');

  static String? requiredText(String? value, int maxLength) {
    final text = normalizeText(value ?? '');
    if (text.isEmpty) return 'Campo obligatorio';
    if (text.length > maxLength) return 'Maximo $maxLength caracteres';
    return null;
  }

  static String? name(String? value, {bool optional = false}) {
    final text = normalizeText(value ?? '');
    if (optional && text.isEmpty) return null;
    if (text.length < 2 || text.length > 50) {
      return 'Usa entre 2 y 50 caracteres';
    }
    if (!RegExp(r'^\p{L}+(?: \p{L}+)*$', unicode: true).hasMatch(text)) {
      return 'Usa solamente letras y espacios';
    }
    return null;
  }

  static String? email(String? value) {
    final text = (value ?? '').trim();
    if (text.length > 100 ||
        !RegExp(r'^[^@\s]+@[^@\s]+\.[^@\s]+$').hasMatch(text)) {
      return 'Ingresa un correo valido (maximo 100 caracteres)';
    }
    return null;
  }

  static String? phone(String? value, {bool optional = false}) {
    final text = (value ?? '').trim();
    if (optional && text.isEmpty) return null;
    return RegExp(r'^[0-9]{10}$').hasMatch(text)
        ? null
        : 'Ingresa exactamente 10 digitos';
  }

  static String? postalCode(String? value) =>
      RegExp(r'^[0-9]{5}$').hasMatch((value ?? '').trim())
          ? null
          : 'Ingresa exactamente 5 digitos';

  static String? curp(String? value) => RegExp(
        r'^[A-Z][AEIOUX][A-Z]{2}[0-9]{2}(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])[HM][A-Z]{2}[B-DF-HJ-NP-TV-Z]{3}[A-Z0-9][0-9]$',
      ).hasMatch((value ?? '').trim().toUpperCase())
          ? null
          : 'CURP invalida (18 caracteres)';

  static String? rfc(String? value) => RegExp(
        r'^[A-ZÑ&]{3,4}[0-9]{2}(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])[A-Z0-9]{3}$',
      ).hasMatch((value ?? '').trim().toUpperCase())
          ? null
          : 'RFC invalido (12 o 13 caracteres)';

  static String? birthDate(String? value, {DateTime? today}) {
    final text = (value ?? '').trim();
    final date = DateOnly.tryParse(text);
    if (date == null) {
      return 'Usa una fecha real: AAAA-MM-DD';
    }
    if (date.isAfter(latestBirthDate(today: today))) {
      return 'El cliente debe tener al menos 18 anos';
    }
    return null;
  }

  /// Limite compartido por el calendario y la validacion de mayoria de edad.
  static DateTime latestBirthDate({DateTime? today}) {
    final now = today ?? DateTime.now();
    // Misma regla que el backend: nacimiento <= hoy.minusYears(18).
    final cutoffYear = now.year - 18;
    final lastDay = DateTime(cutoffYear, now.month + 1, 0).day;
    return DateTime(
        cutoffYear, now.month, now.day > lastDay ? lastDay : now.day);
  }

  static String? income(String? value) {
    final text = (value ?? '').trim();
    if (!RegExp(r'^[0-9]{1,17}(\.[0-9]{1,2})?$').hasMatch(text) ||
        !RegExp(r'[1-9]').hasMatch(text)) {
      return 'Importe mayor a cero; hasta 17 enteros y 2 decimales';
    }
    return null;
  }

  static String? password(String? value) {
    final text = value ?? '';
    if (text.length < 8 || text.length > 72 || utf8.encode(text).length > 72) {
      return 'Usa de 8 a 72 caracteres (maximo 72 bytes)';
    }
    if (!RegExp(r'\p{Lu}', unicode: true).hasMatch(text) ||
        !RegExp(r'\p{Ll}', unicode: true).hasMatch(text) ||
        !RegExp(r'\p{Nd}', unicode: true).hasMatch(text)) {
      return 'Incluye mayuscula, minuscula y numero';
    }
    if (!RegExp(r'[^\p{L}\p{N}\p{Z}\p{C}\s]', unicode: true).hasMatch(text)) {
      return 'Incluye un caracter especial';
    }
    if (RegExp(r'[\x00-\x1F\x7F-\x9F]').hasMatch(text)) {
      return 'La contrasena contiene caracteres no permitidos';
    }
    return null;
  }
}
