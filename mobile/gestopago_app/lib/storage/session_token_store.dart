import 'dart:convert';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

abstract interface class SessionTokenStore {
  Future<String?> read(String reference);
  Future<void> write(String reference, String token);
  Future<void> clear();
}

class SecureSessionTokenStore implements SessionTokenStore {
  SecureSessionTokenStore({FlutterSecureStorage? storage})
    : _storage =
          storage ??
          const FlutterSecureStorage(
            iOptions: IOSOptions(
              accessibility: KeychainAccessibility.unlocked_this_device,
            ),
          );
  static const _key = 'gestopago.session.secret.v1';
  final FlutterSecureStorage _storage;

  @override
  Future<void> write(String reference, String token) => _storage.write(
    key: _key,
    value: jsonEncode({'reference': reference, 'token': token}),
  );

  @override
  Future<String?> read(String reference) async {
    final value = await _storage.read(key: _key);
    if (value == null) return null;
    try {
      final decoded = jsonDecode(value);
      if (decoded is Map<String, dynamic> &&
          decoded['reference'] == reference &&
          decoded['token'] is String &&
          (decoded['token'] as String).isNotEmpty) {
        return decoded['token'] as String;
      }
    } on FormatException {
      // Un secreto corrupto no se interpreta como una sesion valida.
    }
    return null;
  }

  @override
  Future<void> clear() => _storage.delete(key: _key);
}
