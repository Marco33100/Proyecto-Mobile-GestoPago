import '../models/login_session.dart';

abstract interface class SessionStore {
  Future<LoginSession?> current();
  Future<void> save(LoginSession session);
  Future<void> clear();
}
