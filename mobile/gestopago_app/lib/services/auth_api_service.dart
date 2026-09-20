import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:http/http.dart' as http;

import '../config/api_config.dart';
import '../models/login_session.dart';

class AuthApiService {
  AuthApiService(this._client);

  final http.Client _client;

  Future<String> register({
    required String email,
    required String identifier,
    required String password,
    required String fullName,
  }) async {
    try {
      final response = await _client
          .post(
            Uri.parse('${ApiConfig.baseUrl}/api/auth/register'),
            headers: const {'Content-Type': 'application/json'},
            body: jsonEncode({
              'email': email.trim(),
              'identifier': identifier.trim(),
              'password': password,
              'fullName': fullName.trim(),
            }),
          )
          .timeout(const Duration(seconds: 10));

      final body = _decodeBody(response.body);
      if (response.statusCode == 201) {
        return body['message'] as String? ?? 'Usuario creado correctamente.';
      }
      throw ApiException(
        statusCode: response.statusCode,
        code: body['code'] as String? ?? 'UNKNOWN_ERROR',
        message: _friendlyMessage(response.statusCode, body),
      );
    } on TimeoutException {
      throw const ApiException(
        code: 'CONNECTION_TIMEOUT',
        message: 'El servidor tardo demasiado en responder. Intenta nuevamente.',
      );
    } on SocketException {
      throw const ApiException(
        code: 'NO_CONNECTION',
        message: 'No fue posible conectarse con el servidor.',
      );
    } on FormatException {
      throw const ApiException(
        code: 'INVALID_RESPONSE',
        message: 'El servidor envio una respuesta que no se pudo procesar.',
      );
    } on http.ClientException {
      throw const ApiException(
        code: 'NETWORK_ERROR',
        message: 'Ocurrio un problema de red. Revisa tu conexion.',
      );
    }
  }

  Future<LoginSession> login({
    required String email,
    required String password,
  }) async {
    try {
      final response = await _client
          .post(
            Uri.parse('${ApiConfig.baseUrl}/api/auth/login'),
            headers: const {'Content-Type': 'application/json'},
            body: jsonEncode({'email': email.trim(), 'password': password}),
          )
          .timeout(const Duration(seconds: 10));

      final body = _decodeBody(response.body);
      if (response.statusCode == 200) {
        return LoginSession.fromJson(body);
      }
      throw ApiException(
        statusCode: response.statusCode,
        code: body['code'] as String? ?? 'UNKNOWN_ERROR',
        message: _friendlyMessage(response.statusCode, body),
      );
    } on TimeoutException {
      throw const ApiException(
        code: 'CONNECTION_TIMEOUT',
        message: 'El servidor tardo demasiado en responder. Intenta nuevamente.',
      );
    } on SocketException {
      throw const ApiException(
        code: 'NO_CONNECTION',
        message: 'No fue posible conectarse con el servidor.',
      );
    } on FormatException {
      throw const ApiException(
        code: 'INVALID_RESPONSE',
        message: 'El servidor envio una respuesta que no se pudo procesar.',
      );
    } on http.ClientException {
      throw const ApiException(
        code: 'NETWORK_ERROR',
        message: 'Ocurrio un problema de red. Revisa tu conexion.',
      );
    }
  }

  Map<String, dynamic> _decodeBody(String body) {
    final decoded = jsonDecode(body);
    if (decoded is! Map<String, dynamic>) {
      throw const FormatException('Se esperaba un objeto JSON');
    }
    return decoded;
  }

  String _friendlyMessage(int statusCode, Map<String, dynamic> body) {
    final fieldErrors = body['fieldErrors'];
    if (statusCode == 400 && fieldErrors is Map && fieldErrors.isNotEmpty) {
      return fieldErrors.values.first.toString();
    }
    return switch (statusCode) {
      400 => 'Revisa los datos ingresados.',
      401 => 'Correo o contrasena incorrectos.',
      409 => 'El usuario ya se encuentra registrado.',
      >= 500 => 'El servicio no esta disponible por el momento.',
      _ => body['message'] as String? ?? 'No fue posible iniciar sesion.',
    };
  }
}

class ApiException implements Exception {
  const ApiException({required this.code, required this.message, this.statusCode});

  final int? statusCode;
  final String code;
  final String message;

  @override
  String toString() => message;
}
