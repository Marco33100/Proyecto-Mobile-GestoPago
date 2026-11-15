class LoginSession {
  const LoginSession({
    required this.accessToken,
    required this.tokenType,
    required this.expiresAt,
    required this.userId,
    required this.email,
    required this.identifier,
    required this.fullName,
    this.rol = 'CLIENTE',
  });

  final String accessToken;
  final String tokenType;
  final DateTime expiresAt;
  final String userId;
  final String email;
  final String identifier;
  final String fullName;
  final String rol;
  bool get isExecutive => rol == 'EJECUTIVO';

  factory LoginSession.fromJson(Map<String, dynamic> json) {
    final user = json['user'];
    if (user is! Map<String, dynamic>) {
      throw const FormatException(
        'La respuesta no contiene el perfil del usuario',
      );
    }
    return LoginSession(
      accessToken: json['accessToken'] as String,
      tokenType: json['tokenType'] as String? ?? 'Bearer',
      expiresAt: DateTime.parse(json['expiresAt'] as String),
      userId: user['id'] as String,
      email: user['email'] as String,
      identifier: user['identifier'] as String,
      fullName: user['fullName'] as String,
      rol: user['rol'] as String? ?? 'CLIENTE',
    );
  }
}
