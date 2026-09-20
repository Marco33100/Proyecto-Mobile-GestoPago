abstract final class ApiConfig {
  // 10.0.2.2 permite que el emulador Android llegue al localhost de Windows.
  static const String baseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://10.0.2.2:8080',
  );
}
