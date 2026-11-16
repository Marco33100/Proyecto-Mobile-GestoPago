import 'package:flutter/foundation.dart';

abstract final class ApiConfig {
  // El entorno local puede seleccionarse con --dart-define=API_BASE_URL=...
  static const String baseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'https://gestopago-backend-vhlt.onrender.com',
  );

  /// En web la sesión viaja en una cookie HttpOnly emitida por el backend:
  /// el JWT nunca queda al alcance de JavaScript ni del almacenamiento del
  /// navegador. En móvil se conserva el JWT en almacenamiento seguro.
  static const bool useCookieSession = kIsWeb;
}
