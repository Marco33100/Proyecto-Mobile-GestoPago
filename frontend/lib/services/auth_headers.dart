import '../models/login_session.dart';

/// Encabezados de autenticación para las llamadas a la API.
///
/// - Móvil: `Authorization: Bearer <jwt>`.
/// - Web: el navegador envía la cookie HttpOnly; aquí solo se agrega el token
///   CSRF, que el backend exige en las operaciones que modifican datos.
abstract final class AuthHeaders {
  static const defaultCsrfHeader = 'X-XSRF-TOKEN';

  /// Token CSRF de la sesión web. Vive solo en memoria: se vuelve a pedir al
  /// iniciar sesión o al recargar la página.
  static String? csrfToken;
  static String csrfHeader = defaultCsrfHeader;

  static Map<String, String> of(LoginSession session) {
    if (session.usesCookie) {
      final token = csrfToken;
      return {if (token != null) csrfHeader: token};
    }
    return {'Authorization': '${session.tokenType} ${session.accessToken}'};
  }

  static void clearCsrf() {
    csrfToken = null;
    csrfHeader = defaultCsrfHeader;
  }
}
