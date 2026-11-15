import 'package:flutter_test/flutter_test.dart';
import 'package:gestopago_app/validation/cliente_validators.dart';

void main() {
  test('nombres: letras unicode, espacios normalizados, limites y opcionales',
      () {
    expect(ClienteValidators.name('  María    José  '), isNull);
    expect(ClienteValidators.name('李明'), isNull);
    for (final value in [' ', 'A', 'Marco2', 'Marco@', 'A' * 51]) {
      expect(ClienteValidators.name(value), isNotNull);
    }
    expect(ClienteValidators.name('', optional: true), isNull);
  });
  test('telefonos y CP conservan ceros, formatos estrictos', () {
    expect(ClienteValidators.phone('0123456789'), isNull);
    expect(ClienteValidators.phone('12345'), isNotNull);
    expect(ClienteValidators.phone('', optional: true), isNull);
    expect(ClienteValidators.postalCode('01234'), isNull);
    expect(ClienteValidators.postalCode('1234'), isNotNull);
    expect(ClienteValidators.email('a @example.com'), isNotNull);
  });
  test('fecha real, adulto exacto, menor y futuro', () {
    final today = DateTime(2026, 10, 3);
    expect(ClienteValidators.birthDate('2008-10-03', today: today), isNull);
    expect(ClienteValidators.birthDate('2008-10-04', today: today), isNotNull);
    expect(ClienteValidators.birthDate('2027-01-01', today: today), isNotNull);
    expect(ClienteValidators.birthDate('2000-02-30', today: today), isNotNull);
    expect(ClienteValidators.birthDate('1990-01-01T12:30:00Z', today: today),
        isNotNull);
    expect(ClienteValidators.birthDate('1990-01-01 00:00:00', today: today),
        isNotNull);
    expect(
        ClienteValidators.birthDate('2008-02-29', today: DateTime(2026, 2, 28)),
        isNotNull);
    expect(
        ClienteValidators.birthDate('2008-02-29', today: DateTime(2026, 3, 1)),
        isNull);
  });
  test('longitudes de alta: acepta el limite y rechaza el siguiente', () {
    expect(ClienteValidators.name('A' * 50), isNull);
    expect(ClienteValidators.name('A' * 51), isNotNull);
    for (final limit in [20, 60, 100, 150]) {
      expect(ClienteValidators.requiredText('A' * limit, limit), isNull);
      expect(
          ClienteValidators.requiredText('A' * (limit + 1), limit), isNotNull);
    }
    final email = '${'a' * 64}@${'b' * 31}.com';
    expect(email.length, 100);
    expect(ClienteValidators.email(email), isNull);
    expect(ClienteValidators.email('${email}x'), isNotNull);
  });
  test('CURP, RFC e importes con precision decimal', () {
    expect(ClienteValidators.curp('MALM900101HDFRPR01'), isNull);
    expect(ClienteValidators.curp('MALM900101'), isNotNull);
    expect(ClienteValidators.rfc('MALM900101AB1'), isNull);
    expect(ClienteValidators.income('0.01'), isNull);
    expect(ClienteValidators.income('99999999999999999.99'), isNull);
    for (final value in ['0.00', '-1', '1.001', '100000000000000000', '1e8']) {
      expect(ClienteValidators.income(value), isNotNull);
    }
  });
  test('contrasena completa y limite BCrypt UTF8 sin normalizacion', () {
    expect(ClienteValidators.password('Segura123!'), isNull);
    for (final value in [
      'Segura123',
      'segura123!',
      'SEGURA123!',
      'SeguraABC!',
      'Aa1!NaN'
    ]) {
      expect(ClienteValidators.password(value), isNotNull);
    }
  });
}
