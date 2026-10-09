import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:http/http.dart' as http;

import '../config/api_config.dart';
import '../models/login_session.dart';

class AuthApiService {
  AuthApiService(
    this._client, {
    this.requestTimeout = const Duration(seconds: 10),
    this.registrationTimeout = const Duration(seconds: 20),
  });

  final http.Client _client;
  final Duration requestTimeout;
  final Duration registrationTimeout;

  Future<String> registerClient({
    required LoginSession session,
    required Map<String, dynamic> payload,
  }) => _post(
    path: '/api/clientes',
    session: session,
    payload: payload,
    timeout: registrationTimeout,
    successStatuses: const {201},
    parse: (body) {
      final accounts = body['cuentas'];
      final first = accounts is List && accounts.isNotEmpty
          ? accounts.first
          : null;
      final number = first is Map ? first['numeroCuenta'] : null;
      return number == null
          ? 'Cliente registrado correctamente.'
          : 'Cliente registrado. Cuenta: $number';
    },
  );

  Future<LoginSession> login({
    required String email,
    required String password,
  }) => _post(
    path: '/api/auth/login',
    payload: {'email': email.trim(), 'password': password},
    timeout: requestTimeout,
    successStatuses: const {200},
    parse: LoginSession.fromJson,
  );

  // Una sesion ya expirada/revocada (401) no necesita revocarse otra vez.
  Future<void> logout(LoginSession session) => _post<void>(
    path: '/api/auth/logout',
    session: session,
    timeout: requestTimeout,
    successStatuses: const {204, 401},
    parse: (_) {},
    emptySuccess: true,
  );

  Future<T> _post<T>({
    required String path,
    required Duration timeout,
    required Set<int> successStatuses,
    required T Function(Map<String, dynamic>) parse,
    Map<String, dynamic>? payload,
    LoginSession? session,
    bool emptySuccess = false,
  }) async {
    try {
      final response = await _client
          .post(
            Uri.parse('${ApiConfig.baseUrl}$path'),
            headers: {
              'Content-Type': 'application/json',
              if (session != null)
                'Authorization': '${session.tokenType} ${session.accessToken}',
            },
            body: payload == null ? null : jsonEncode(payload),
          )
          .timeout(timeout);

      if (successStatuses.contains(response.statusCode)) {
        return parse(emptySuccess ? const {} : _decodeBody(response.body));
      }
      // Un proxy puede responder HTML: conservamos el estado HTTP real.
      Map<String, dynamic> body;
      try {
        body = _decodeBody(response.body);
      } on FormatException {
        body = const {};
      }
      throw ApiException(
        statusCode: response.statusCode,
        code: body['code'] is String ? body['code'] as String : 'UNKNOWN_ERROR',
        message: _friendlyMessage(response.statusCode, body),
      );
    } on TimeoutException {
      throw const ApiException(
        code: 'CONNECTION_TIMEOUT',
        message:
            'El servidor tardo demasiado en responder. Intenta nuevamente.',
      );
    } on SocketException {
      throw const ApiException(
        code: 'NO_CONNECTION',
        message: 'No fue posible conectarse con el servidor.',
      );
    } on http.ClientException {
      throw const ApiException(
        code: 'NETWORK_ERROR',
        message: 'Ocurrio un problema de red. Revisa tu conexion.',
      );
    } on FormatException {
      throw _invalidResponse;
    } on TypeError {
      throw _invalidResponse;
    }
  }

  static const _invalidResponse = ApiException(
    code: 'INVALID_RESPONSE',
    message: 'El servidor envio una respuesta que no se pudo procesar.',
  );

  Map<String, dynamic> _decodeBody(String body) {
    final decoded = jsonDecode(body);
    if (decoded is! Map<String, dynamic>) {
      throw const FormatException('Se esperaba un objeto JSON');
    }
    return decoded;
  }

  String _friendlyMessage(int statusCode, Map<String, dynamic> body) {
    final fieldErrors = body['fieldErrors'] ?? body['validationErrors'];
    if (statusCode == 400 && fieldErrors is Map && fieldErrors.isNotEmpty) {
      return fieldErrors.values.first.toString();
    }
    final message = body['message'];
    return switch (statusCode) {
      400 => 'Revisa los datos ingresados.',
      401 =>
        body['code'] == 'AUTH_INVALID_SESSION'
            ? 'Tu sesion expiro o fue reemplazada. Inicia sesion nuevamente.'
            : 'Correo o contrasena incorrectos.',
      403 => 'No tienes permiso para realizar esta operacion.',
      409 =>
        message is String ? message : 'Los datos ya se encuentran registrados.',
      429 =>
        'Demasiados intentos de inicio de sesion. Espera unos minutos antes de intentar nuevamente.',
      >= 500 => 'El servicio no esta disponible por el momento.',
      _ =>
        message is String ? message : 'No fue posible completar la operacion.',
    };
  }
}

class ApiException implements Exception {
  const ApiException({
    required this.code,
    required this.message,
    this.statusCode,
  });

  final int? statusCode;
  final String code;
  final String message;

  @override
  String toString() => message;
}
