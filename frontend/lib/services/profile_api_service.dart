import 'payment_api_service.dart';

class ProfileApiService {
  ProfileApiService(this.api);
  final PaymentApiService api;

  Map<String, dynamic> get _identity => {
    'nombre': api.session.fullName,
    'correo': api.session.email,
  };

  Future<Map<String, dynamic>> profile() =>
      api.request('/api/usuarios/consulta', _identity);

  // El servidor comprueba la contraseña actual y revoca la sesión al cambiarla.
  Future<void> changePassword(String current, String replacement) async {
    await api.update('/api/usuarios/password', {
      ..._identity,
      'credenciales': {
        'contrasenaActual': current,
        'nuevaContrasena': replacement,
      },
    });
  }
}
