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
  ]) async {
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
      final response =
          await (body == null
                  ? client.get(uri, headers: headers)
                  : client.post(uri, headers: headers, body: jsonEncode(body)))
              .timeout(const Duration(seconds: 85));
      final dynamic decoded;
      try {
        decoded = jsonDecode(response.body);
      } on FormatException {
        throw const PaymentFailure(
          'Respuesta no disponible. Consulta el estado del pago antes de repetirlo.',
        );
      }
      if (response.statusCode < 200 || response.statusCode >= 300) {
        throw PaymentFailure(
          response.statusCode == 401
              ? 'Tu sesion vencio. Inicia sesion para consultar el pago.'
              : 'No se pudo completar la solicitud. Consulta el estado antes de repetir el pago.',
          status: response.statusCode,
        );
      }
      if (decoded is! Map<String, dynamic>) throw const FormatException();
      if (decoded['datos'] is Map<String, dynamic>) {
        return decoded['datos'] as Map<String, dynamic>;
      }
      if (decoded['codigo'] == 0) return {};
      throw const PaymentFailure('Servicio no Disponible');
    } on TimeoutException {
      throw const PaymentFailure(
        'La respuesta tardo demasiado. No repitas el pago: consulta su estado.',
      );
    } on http.ClientException {
      throw const PaymentFailure(
        'Se perdio la conexion. Consulta el estado antes de repetir el pago.',
      );
    } on FormatException {
      throw const PaymentFailure(
        'Respuesta no reconocida. Consulta el estado del pago.',
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
