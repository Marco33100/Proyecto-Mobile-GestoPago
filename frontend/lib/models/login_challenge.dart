import 'login_session.dart';

/// Primer paso del inicio de sesión: la contraseña fue correcta y falta el
/// código del autenticador.
class LoginChallenge {
  const LoginChallenge({
    required this.token,
    required this.configured,
    required this.expiresAt,
    this.secret,
    this.otpauthUri,
  });

  /// Desafío firmado que se envía al verificar. No es una sesión.
  final String token;

  /// `false`: el usuario debe vincular su autenticador ahora.
  final bool configured;
  final DateTime expiresAt;

  /// Clave y URI para vincular el autenticador (solo si no está configurado).
  final String? secret;
  final String? otpauthUri;

  factory LoginChallenge.fromJson(Map<String, dynamic> json) {
    final token = json['desafio'];
    final configured = json['segundoFactorConfigurado'];
    final expiresAt = DateTime.tryParse('${json['venceEn']}');
    if (token is! String || token.isEmpty || configured is! bool) {
      throw const FormatException('La respuesta no contiene el desafío');
    }
    final setup = json['configuracion'];
    final secret = setup is Map ? setup['secreto'] : null;
    final uri = setup is Map ? setup['uri'] : null;
    if (!configured && (secret is! String || uri is! String)) {
      throw const FormatException('Falta la configuración del autenticador');
    }
    return LoginChallenge(
      token: token,
      configured: configured,
      expiresAt:
          expiresAt ?? DateTime.now().toUtc().add(const Duration(minutes: 5)),
      secret: secret as String?,
      otpauthUri: uri as String?,
    );
  }
}

/// Resultado de verificar el segundo factor: la sesión y, si el autenticador
/// se acaba de vincular, los códigos de recuperación (se muestran una vez).
class VerifiedLogin {
  const VerifiedLogin({required this.session, this.recoveryCodes = const []});

  final LoginSession session;
  final List<String> recoveryCodes;
}
