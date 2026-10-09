import 'package:flutter_test/flutter_test.dart';
import 'package:gestopago_app/utils/date_only.dart';
import 'package:gestopago_app/validation/cliente_validators.dart';

void main() {
  test('quita la hora al formatear y conserva el dia sin convertir zonas', () {
    expect(DateOnly.format(DateTime(1990, 1, 1, 23, 59, 59)), '1990-01-01');
    expect(DateOnly.format(DateTime.utc(2000, 2, 29, 0, 1)), '2000-02-29');
    expect(DateOnly.format(DateTime(1, 1, 1)), '0001-01-01');
  });

  test('fechas imposibles y timestamps no se corrigen por suposicion', () {
    expect(DateOnly.tryParse('2000-02-29'), DateTime(2000, 2, 29));
    for (final text in [
      '',
      '2000-02-30',
      '1900-02-29',
      '1990-13-01',
      '1990-1-1',
      '1990-01-01T23:00:00Z',
      '1990-01-01 23:00:00',
      '01/01/1990',
    ]) {
      expect(DateOnly.tryParse(text), isNull, reason: text);
    }
  });

  test('limite del selector coincide con la edad y no lleva hora', () {
    final today = DateTime(2026, 10, 5, 23, 59);
    final cutoff = ClienteValidators.latestBirthDate(today: today);
    expect(cutoff, DateTime(2008, 10, 5));
    expect(ClienteValidators.birthDate(DateOnly.format(cutoff), today: today),
        isNull);
    expect(
        ClienteValidators.birthDate(
            DateOnly.format(cutoff.add(const Duration(days: 1))),
            today: today),
        isNotNull);
    expect(ClienteValidators.latestBirthDate(today: DateTime(2024, 2, 29)),
        DateTime(2006, 2, 28));
  });
}
