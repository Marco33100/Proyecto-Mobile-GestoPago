import 'dart:async';
import 'dart:convert';
import 'dart:math';
import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:http/http.dart' as http;
import '../config/api_config.dart';
import '../models/login_session.dart';

class PaymentFailure implements Exception {
  const PaymentFailure(this.message, {this.status});
  final String message;
  final int? status;
  @override
  String toString() => message;
}

/// Never retries a payment automatically, even after a timeout.
class PaymentApiService {
  PaymentApiService(this.client, this.session, {String? baseUrl})
    : base = baseUrl ?? ApiConfig.baseUrl;
  final http.Client client;
  final LoginSession session;
  final String base;
  Future<Map<String, dynamic>> request(
    String path, [
    Map<String, dynamic>? body,
  ]) => _send(path, body, body == null ? 'GET' : 'POST');

  Future<Map<String, dynamic>> update(
    String path,
    Map<String, dynamic> body, {
    String method = 'PUT',
  }) => _send(path, body, method);

  Future<Map<String, dynamic>> _send(
    String path,
    Map<String, dynamic>? body,
    String method,
  ) async {
    final financial =
        path.startsWith('/api/pagos/') &&
        !path.startsWith('/api/pagos/segundo-factor/');
    final uri = Uri.parse('$base$path');
    if (kReleaseMode && uri.scheme != 'https') {
      throw const PaymentFailure('Los pagos requieren una conexion HTTPS.');
    }
    try {
      final headers = {
        'Authorization': '${session.tokenType} ${session.accessToken}',
        'Content-Type': 'application/json',
        'Accept': 'application/json',
      };
      final request = http.Request(method, uri)..headers.addAll(headers);
      if (body != null) request.body = jsonEncode(body);
      final response = await client
          .send(request)
          .then(http.Response.fromStream)
          .timeout(const Duration(seconds: 85));
      if (response.statusCode == 204) return {};
      final dynamic decoded;
      try {
        decoded = jsonDecode(response.body);
      } on FormatException {
        throw PaymentFailure(
          financial
              ? 'Respuesta no disponible. Consulta el estado del pago antes de repetirlo.'
              : 'El servidor no devolvió una respuesta válida. Actualiza la información antes de repetir la operación.',
          status: response.statusCode,
        );
      }
      if (response.statusCode < 200 || response.statusCode >= 300) {
        final message = decoded is Map ? decoded['mensaje'] : null;
        final code = decoded is Map ? decoded['codigo'] : null;
        final publicMessage =
            const ['0', '1', '2', '01', '02'].contains('$code') &&
            message is String &&
            message.trim().isNotEmpty;
        throw PaymentFailure(
          response.statusCode == 401
              ? 'Tu sesión venció. Inicia sesión nuevamente.'
              : response.statusCode >= 500
              ? financial
                    ? 'El servicio no está disponible temporalmente. Si ya autorizaste un pago, consulta su estado antes de repetirlo.'
                    : 'El servicio no está disponible temporalmente. Inténtalo más tarde.'
              : publicMessage
              ? message
              : financial
              ? 'No se pudo completar la solicitud. Consulta el estado antes de repetir el pago.'
              : 'No se pudo completar la solicitud. Revisa los datos y tus permisos.',
          status: response.statusCode,
        );
      }
      if (decoded is! Map<String, dynamic>) throw const FormatException();
      if (decoded['datos'] is Map<String, dynamic>) {
        return decoded['datos'] as Map<String, dynamic>;
      }
      if (decoded['codigo'] == 0) return {};
      throw const PaymentFailure(
        'No se pudo confirmar el resultado de la operación.',
      );
    } on TimeoutException {
      throw PaymentFailure(
        financial
            ? 'La respuesta tardo demasiado. No repitas el pago: consulta su estado.'
            : 'El servidor tardó demasiado. Actualiza la información antes de repetir la operación.',
      );
    } on http.ClientException {
      throw PaymentFailure(
        financial
            ? 'Se perdio la conexion. Consulta el estado antes de repetir el pago.'
            : 'Se perdió la conexión. Revisa tu red y actualiza la información.',
      );
    } on FormatException {
      throw PaymentFailure(
        financial
            ? 'Respuesta no reconocida. Consulta el estado del pago.'
            : 'El servidor envió una respuesta no reconocida.',
      );
    }
  }
}

/// Separate from login storage: logout must not erase an uncertain payment.
class PendingPaymentStore {
  const PendingPaymentStore();
  static const storage = FlutterSecureStorage(
    iOptions: IOSOptions(
      accessibility: KeychainAccessibility.unlocked_this_device,
    ),
  );
  String key(String userId) => 'gestopago.pending-payment.v1.$userId';
  Future<String?> read(String userId) => storage.read(key: key(userId));
  Future<void> write(String userId, String id) =>
      storage.write(key: key(userId), value: id);
  Future<void> clear(String userId) => storage.delete(key: key(userId));
  static String newId() {
    final random = Random.secure();
    final bytes = List.generate(16, (_) => random.nextInt(256));
    bytes[6] = (bytes[6] & 15) | 64;
    bytes[8] = (bytes[8] & 63) | 128;
    final hex = bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).join();
    return '${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}';
  }
}
