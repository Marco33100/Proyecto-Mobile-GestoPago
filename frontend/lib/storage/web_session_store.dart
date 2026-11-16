import 'dart:async';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../models/login_session.dart';
import '../services/auth_api_service.dart';
import '../services/auth_headers.dart';
import 'session_store.dart';

/// Sesión del cliente web.
///
/// El JWT vive en una cookie HttpOnly que administra el navegador, así que
/// aquí solo se guarda la fecha de vencimiento (un dato no sensible) para
/// saber si vale la pena consultar `/api/auth/sesion` al recargar la página.
class WebSessionStore implements SessionStore {
  WebSessionStore(this.authApi, {FlutterSecureStorage? storage})
    : _storage = storage ?? const FlutterSecureStorage();

  static const _key = 'gestopago.web-session.expires-at.v1';
  static const _rejectedStatuses = {401, 403, 404};
  final FlutterSecureStorage _storage;
  final AuthApiService authApi;

  @override
  Future<void> save(LoginSession session) => _storage.write(
    key: _key,
    value: session.expiresAt.toUtc().toIso8601String(),
  );

  @override
  Future<LoginSession?> current() async {
    final raw = await _storage.read(key: _key);
    final expiresAt = raw == null ? null : DateTime.tryParse(raw);
    if (expiresAt == null || !expiresAt.isAfter(DateTime.now().toUtc())) {
      await clear();
      return null;
    }
    try {
      return await authApi.restoreCookieSession(expiresAt);
    } on ApiException catch (error) {
      // La cookie ya no es válida: no hay sesión que restaurar.
      if (_rejectedStatuses.contains(error.statusCode)) {
        await clear();
        return null;
      }
      rethrow;
    }
  }

  @override
  Future<void> clear() async {
    AuthHeaders.clearCsrf();
    await _storage.delete(key: _key);
  }
}
