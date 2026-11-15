import 'dart:async';
import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../models/login_session.dart';
import '../services/auth_api_service.dart';
import 'session_store.dart';

class SecureSessionStore implements SessionStore {
  SecureSessionStore(this.authApi, {FlutterSecureStorage? storage})
    : _storage =
          storage ??
          const FlutterSecureStorage(
            iOptions: IOSOptions(
              accessibility: KeychainAccessibility.unlocked_this_device,
            ),
          );

  static const _key = 'gestopago.session.credential.v2';
  static const _legacyKey = 'gestopago.session.secret.v1';
  static const _rejectedStatuses = {401, 403, 404};
  final FlutterSecureStorage _storage;
  final AuthApiService authApi;
  Future<void> _pending = Future<void>.value();

  Future<T> _serialized<T>(Future<T> Function() action) {
    final result = _pending.then((_) => action());
    _pending = result.then<void>((_) {}, onError: (Object _, StackTrace __) {});
    return result;
  }

  @override
  Future<void> save(LoginSession session) => _serialized(
    () => _storage.write(
      key: _key,
      value: jsonEncode({
        'token': session.accessToken,
        'expiresAt': session.expiresAt.toUtc().toIso8601String(),
      }),
    ),
  );

  @override
  Future<LoginSession?> current() => _serialized(() async {
    final raw = await _storage.read(key: _key);
    if (raw == null) {
      await _storage.delete(key: _legacyKey);
      return null;
    }
    String token;
    DateTime? expiresAt;
    try {
      final data = jsonDecode(raw);
      if (data is! Map ||
          data['token'] is! String ||
          (data['token'] as String).isEmpty ||
          data['expiresAt'] is! String) {
        await _storage.delete(key: _key);
        return null;
      }
      token = data['token'] as String;
      expiresAt = DateTime.tryParse(data['expiresAt'] as String);
    } on FormatException {
      await _storage.delete(key: _key);
      return null;
    }
    if (expiresAt == null || !expiresAt.isAfter(DateTime.now().toUtc())) {
      await _storage.delete(key: _key);
      return null;
    }
    try {
      return await authApi.restoreSession(token, expiresAt);
    } on ApiException catch (error) {
      // 401 sesion invalida, 403 usuario inactivo, 404 usuario inexistente:
      // la credencial ya no sirve. Fallos temporales (5xx, red) la conservan.
      if (_rejectedStatuses.contains(error.statusCode)) {
        await _storage.delete(key: _key);
        return null;
      }
      rethrow;
    }
  });

  @override
  Future<void> clear() => _serialized(() async {
    await _storage.delete(key: _key);
    await _storage.delete(key: _legacyKey);
  });
}
