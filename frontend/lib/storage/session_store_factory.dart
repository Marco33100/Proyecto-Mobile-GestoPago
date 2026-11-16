import '../services/auth_api_service.dart';
import 'secure_session_store.dart';
import 'session_store.dart';
import 'web_session_store.dart';

/// Web: sesión en cookie HttpOnly. Móvil: JWT en almacenamiento seguro.
SessionStore createSessionStore(AuthApiService authApi) => authApi.cookieSession
    ? WebSessionStore(authApi)
    : SecureSessionStore(authApi);
