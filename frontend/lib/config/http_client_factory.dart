// Crea el cliente HTTP adecuado para cada plataforma.
//
// En web se usa BrowserClient con `withCredentials` para que el navegador
// envíe la cookie de sesión al backend; en móvil, el cliente estándar.
export 'http_client_io.dart'
    if (dart.library.js_interop) 'http_client_web.dart';
