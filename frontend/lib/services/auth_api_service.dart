import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:http/http.dart' as http;

import '../config/api_config.dart';
import '../models/login_challenge.dart';
import '../models/login_session.dart';
import 'auth_headers.dart';

/// Opciones de los campos de lista del registro.
typedef RegistrationCatalogs = ({
  List<String> countries,
  List<String> nationalities,
  List<String> occupations,
});

class AuthApiService {
  AuthApiService(
    this._client, {
    this.requestTimeout = const Duration(seconds: 10),
    this.registrationTimeout = const Duration(seconds: 20),
    bool? cookieSession,
  }) : cookieSession = cookieSession ?? ApiConfig.useCookieSession;

  final http.Client _client;
  final Duration requestTimeout;
  final Duration registrationTimeout;

  /// Web: el backend entrega la sesión en una cookie HttpOnly.
  final bool cookieSession;

  /// Sin [session] es el registro público: la persona crea su propia cuenta.
  Future<String> registerClient({
    required LoginSession? session,
    required Map<String, dynamic> payload,
  }) => _post(
    path: session == null ? '/api/clientes/registro' : '/api/clientes',
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
      if (session == null) {
        return 'Cuenta creada. Inicia sesión con tu correo y contraseña.';
      }
      return number == null
          ? 'Cliente registrado correctamente.'
          : 'Cliente registrado. Cuenta: $number';
    },
  );

  /// Ruta pública. Null si no se pudo cargar: el formulario usa texto libre
  /// y el servidor sigue validando contra el catálogo.
  Future<RegistrationCatalogs?> registrationCatalogs() async {
    try {
      final response = await _client
          .get(Uri.parse('${ApiConfig.baseUrl}/api/catalogos/registro'))
          .timeout(requestTimeout);
      if (response.statusCode != 200) return null;
      // Acentos: Spring no siempre declara charset en application/json.
      final data =
          _decodeBody(utf8.decode(response.bodyBytes))['datos']
              as Map<String, dynamic>;
      List<String> list(String key) => (data[key] as List).cast<String>();
      return (
        countries: list('paises'),
        nationalities: list('nacionalidades'),
        occupations: list('ocupaciones'),
      );
    } on Object {
      return null;
    }
  }

  /// Paso 1: verifica la contraseña. No crea sesión; devuelve el desafío del
  /// segundo factor (y la configuración del autenticador si aún no existe).
  Future<LoginChallenge> login({
    required String email,
    required String password,
  }) => _post(
    path: '/api/auth/login',
    payload: {'email': email.trim(), 'password': password},
    timeout: requestTimeout,
    successStatuses: const {200},
    parse: LoginChallenge.fromJson,
  );

  /// Paso 2: verifica el código del autenticador (o uno de recuperación) y
  /// crea la sesión. En web la sesión llega en una cookie HttpOnly.
  ///
  /// Si la cuenta ya tiene una sesión abierta en otro dispositivo lanza
  /// [ActiveSessionException] sin gastar el código; se repite con
  /// [closePrevious] para cerrarla y entrar.
  Future<VerifiedLogin> verifyLogin({
    required LoginChallenge challenge,
    String? code,
    String? recoveryCode,
    bool closePrevious = false,
  }) async {
    final result = await _post(
      path: '/api/auth/login/verificar',
      payload: {
        'desafio': challenge.token,
        'codigo': ?code,
        'codigoRecuperacion': ?recoveryCode,
        if (closePrevious) 'cerrarSesionAnterior': true,
      },
      timeout: requestTimeout,
      successStatuses: const {200},
      extraHeaders: cookieSession ? const {'X-Auth-Mode': 'cookie'} : const {},
      parse: (data) {
        final codes = data['codigosRecuperacion'];
        return VerifiedLogin(
          session: LoginSession.fromJson(data, cookieSession: cookieSession),
          recoveryCodes: codes is List
              ? codes.whereType<String>().toList(growable: false)
              : const [],
        );
      },
    );
    if (cookieSession) await refreshCsrfToken();
    return result;
  }

  /// Pide el token CSRF que la sesión web envía en cada operación que
  /// modifica datos. Se guarda solo en memoria.
  Future<void> refreshCsrfToken() async {
    try {
      final response = await _client
          .get(Uri.parse('${ApiConfig.baseUrl}/api/auth/csrf'))
          .timeout(requestTimeout);
      if (response.statusCode != 200) {
        throw ApiException(
          statusCode: response.statusCode,
          code: 'CSRF_UNAVAILABLE',
          message: response.statusCode == 401
              ? 'Tu sesion expiro. Inicia sesion nuevamente.'
              : 'No fue posible preparar la sesion segura. Intenta nuevamente.',
        );
      }
      final data = _decodeBody(response.body)['datos'];
      if (data is! Map ||
          data['token'] is! String ||
          data['headerName'] is! String) {
        throw _invalidResponse;
      }
      AuthHeaders.csrfToken = data['token'] as String;
      AuthHeaders.csrfHeader = data['headerName'] as String;
    } on TimeoutException {
      throw const ApiException(
        code: 'CONNECTION_TIMEOUT',
        message: 'No fue posible preparar la sesion segura.',
      );
    } on http.ClientException {
      throw const ApiException(
        code: 'NETWORK_ERROR',
        message: 'No fue posible conectarse con el servidor.',
      );
    } on FormatException {
      throw _invalidResponse;
    }
  }

  /// Web: la cookie HttpOnly la envía el navegador; solo se consulta el perfil.
  Future<LoginSession> restoreCookieSession(DateTime expiresAt) async {
    final session = await _restore(const {}, '', expiresAt);
    await refreshCsrfToken();
    return session;
  }

  /// Revisa que la sesión siga vigente. Devuelve el aviso para el usuario si
  /// terminó (otro dispositivo la reemplazó o expiró) y `null` si sigue activa
  /// o no se pudo comprobar (sin red no se cierra nada).
  Future<String?> endedSessionMessage(LoginSession session) async {
    try {
      final response = await _client
          .get(
            Uri.parse('${ApiConfig.baseUrl}/api/auth/sesion'),
            headers: AuthHeaders.of(session),
          )
          .timeout(requestTimeout);
      if (response.statusCode != 401) return null;
      Object? data;
      try {
        data = _decodeBody(response.body)['datos'];
      } on FormatException {
        data = null;
      }
      final platform = data is Map ? data['plataforma'] : null;
      return platform is String
          ? 'Tu sesión se cerró porque iniciaste sesión en '
                '${sessionPlatformLabel(platform)}.'
          : 'Tu sesión expiró. Inicia sesión nuevamente.';
    } on Exception {
      return null;
    }
  }

  Future<LoginSession> restoreSession(String token, DateTime expiresAt) =>
      _restore({'Authorization': 'Bearer $token'}, token, expiresAt);

  Future<LoginSession> _restore(
    Map<String, String> headers,
    String token,
    DateTime expiresAt,
  ) async {
    try {
      final response = await _client
          .get(
            Uri.parse('${ApiConfig.baseUrl}/api/auth/sesion'),
            headers: headers,
          )
          .timeout(requestTimeout);
      if (response.statusCode != 200) {
        throw ApiException(
          statusCode: response.statusCode,
          code: 'SESSION_RESTORE_FAILED',
          message: response.statusCode == 401
              ? 'Tu sesion expiro. Inicia sesion nuevamente.'
              : 'No fue posible recuperar la sesion. Intenta nuevamente.',
        );
      }
      final body = _decodeBody(response.body);
      final user = body['datos'];
      if (body['codigo'] != 1 || user is! Map<String, dynamic>) {
        throw _invalidResponse;
      }
      return LoginSession.fromJson({
        'accessToken': token,
        'tokenType': 'Bearer',
        'expiresAt': expiresAt.toUtc().toIso8601String(),
        'user': user,
      }, cookieSession: token.isEmpty);
    } on TimeoutException {
      throw const ApiException(
        code: 'CONNECTION_TIMEOUT',
        message: 'No fue posible recuperar la sesion.',
      );
    } on SocketException {
      throw const ApiException(
        code: 'NO_CONNECTION',
        message: 'No fue posible conectarse con el servidor.',
      );
    } on http.ClientException {
      throw const ApiException(
        code: 'NETWORK_ERROR',
        message: 'No fue posible conectarse con el servidor.',
      );
    } on FormatException {
      throw _invalidResponse;
    } on TypeError {
      throw _invalidResponse;
    }
  }

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
    Map<String, String> extraHeaders = const {},
    bool emptySuccess = false,
  }) async {
    try {
      final response = await _client
          .post(
            Uri.parse('${ApiConfig.baseUrl}$path'),
            headers: {
              'Content-Type': 'application/json',
              ...extraHeaders,
              if (session != null) ...AuthHeaders.of(session),
            },
            body: payload == null ? null : jsonEncode(payload),
          )
          .timeout(timeout);

      if (successStatuses.contains(response.statusCode)) {
        if (emptySuccess) return parse(const {});
        final body = _decodeBody(response.body);
        if (body.containsKey('codigo')) {
          final data = body['datos'];
          if (body['codigo'] != 1 || data is! Map<String, dynamic>) {
            throw _invalidResponse;
          }
          return parse(data);
        }
        // Compatibilidad con la version publicada antes de la envoltura codigo/mensaje/datos.
        return parse(body);
      }
      // Un proxy puede responder HTML: conservamos el estado HTTP real.
      Map<String, dynamic> body;
      try {
        body = _decodeBody(response.body);
      } on FormatException {
        body = const {};
      }
      final data = body['datos'];
      if (response.statusCode == 409 &&
          data is Map &&
          data['plataforma'] is String) {
        final startedAt = data['iniciadaEn'];
        throw ActiveSessionException(
          platform: data['plataforma'] as String,
          startedAt: startedAt is String
              ? DateTime.tryParse(startedAt)?.toLocal()
              : null,
          message: _friendlyMessage(response.statusCode, body),
        );
      }
      throw ApiException(
        statusCode: response.statusCode,
        code: body['code'] is String
            ? body['code'] as String
            : body['codigo'] is int
            ? body['codigo'].toString()
            : 'UNKNOWN_ERROR',
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
    final publicMessage = body['mensaje'];
    if (body['codigo'] is int &&
        publicMessage is String &&
        publicMessage.trim().isNotEmpty) {
      return statusCode >= 500
          ? 'El servicio no esta disponible por el momento.'
          : publicMessage;
    }
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

/// La cuenta ya tiene una sesión abierta en otro dispositivo (HTTP 409).
class ActiveSessionException extends ApiException {
  const ActiveSessionException({
    required this.platform,
    required super.message,
    this.startedAt,
  }) : super(code: 'ACTIVE_SESSION', statusCode: 409);

  /// `WEB` o `MOVIL`.
  final String platform;
  final DateTime? startedAt;
}

/// Nombre legible de la plataforma de una sesión (`WEB` o `MOVIL`).
String sessionPlatformLabel(String platform) =>
    platform == 'WEB' ? 'la plataforma web' : 'la app móvil';
