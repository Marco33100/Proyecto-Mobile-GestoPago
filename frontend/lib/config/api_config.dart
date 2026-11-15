abstract final class ApiConfig {
  // El entorno local puede seleccionarse con --dart-define=API_BASE_URL=...
  static const String baseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'https://gestopago-backend-vhlt.onrender.com',
  );
}
