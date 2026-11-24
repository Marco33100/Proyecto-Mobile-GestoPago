import 'payment_api_service.dart';

/// Países y ocupaciones para editar los datos propios.
typedef ProfileCatalogs = ({List<String> countries, List<String> occupations});

class ProfileApiService {
  ProfileApiService(this.api);
  final PaymentApiService api;

  Map<String, dynamic> get _identity => {
    'nombre': api.session.fullName,
    'correo': api.session.email,
  };

  Future<Map<String, dynamic>> profile() =>
      api.request('/api/usuarios/consulta', _identity);

  /// Datos completos del cliente con sesión (contacto, domicilio y empleo).
  Future<Map<String, dynamic>> myData() =>
      api.request('/api/clientes/mis-datos');

  /// El servidor conserva identidad y correo aunque se enviaran.
  Future<Map<String, dynamic>> updateMyData(Map<String, dynamic> data) =>
      api.update('/api/clientes/mis-datos', data);

  /// Null si no cargan: el formulario usa texto y el servidor valida igual.
  Future<ProfileCatalogs?> catalogs() async {
    try {
      final data = await api.request('/api/catalogos/registro');
      List<String> list(String key) => (data[key] as List).cast<String>();
      return (countries: list('paises'), occupations: list('ocupaciones'));
    } on Object {
      return null;
    }
  }

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
