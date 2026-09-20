class LoginSession {
  const LoginSession({
    required this.accessToken,
    required this.tokenType,
    required this.expiresAt,
    required this.userId,
    required this.email,
    required this.identifier,
    required this.fullName,
  });

  final String accessToken;
  final String tokenType;
  final DateTime expiresAt;
  final String userId;
  final String email;
  final String identifier;
  final String fullName;

  factory LoginSession.fromJson(Map<String, dynamic> json) {
    final user = json['user'];
    if (user is! Map<String, dynamic>) {
      throw const FormatException('La respuesta no contiene el perfil del usuario');
    }
    return LoginSession(
      accessToken: json['accessToken'] as String,
      tokenType: json['tokenType'] as String? ?? 'Bearer',
      expiresAt: DateTime.parse(json['expiresAt'] as String),
      userId: user['id'] as String,
      email: user['email'] as String,
      identifier: user['identifier'] as String,
      fullName: user['fullName'] as String,
    );
  }

  Map<String, Object?> toDatabaseMap() => {
        'id': 1,
        'access_token': accessToken,
        'token_type': tokenType,
        'expires_at': expiresAt.toUtc().toIso8601String(),
        'user_id': userId,
        'email': email,
        'identifier': identifier,
        'full_name': fullName,
      };

  factory LoginSession.fromDatabaseMap(Map<String, Object?> row) {
    return LoginSession(
      accessToken: row['access_token']! as String,
      tokenType: row['token_type']! as String,
      expiresAt: DateTime.parse(row['expires_at']! as String),
      userId: row['user_id']! as String,
      email: row['email']! as String,
      identifier: row['identifier']! as String,
      fullName: row['full_name']! as String,
    );
  }
}
