import 'package:flutter_test/flutter_test.dart';
import 'package:gestopago_app/validation/login_validators.dart';

void main() {
  test('login: limite de correo 254, no 100 como el alta de cliente', () {
    final atLimit = '${'a' * 64}@${'b' * 63}.${'c' * 63}.${'d' * 59}.x';
    expect(atLimit.length, 254);
    expect(LoginValidators.email(atLimit), isNull);
    expect(LoginValidators.email('${atLimit}x'), isNotNull);
    for (final value in ['', ' ', 'a @b.com', 'a@@b.com', ' a@b.com ']) {
      expect(LoginValidators.email(value), isNotNull);
    }
  });

  test('login: maximo 72 sin recortar ni exigir fuerza de alta', () {
    expect(LoginValidators.password('A' * 72), isNull);
    expect(LoginValidators.password('A' * 73), isNotNull);
    expect(LoginValidators.password(' '), isNotNull);
    expect(LoginValidators.password(' antiguo '), isNull);
    expect(LoginValidators.password('abc'), isNull);
  });
}
