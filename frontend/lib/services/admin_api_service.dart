import '../models/login_session.dart';
import 'payment_api_service.dart';

class AdminPageResult {
  const AdminPageResult(this.items, this.page, this.last, this.total);
  final List<Map<String, dynamic>> items;
  final int page;
  final bool last;
  final int total;

  factory AdminPageResult.fromJson(Map<String, dynamic> json, int page) {
    final content = json['contenido'];
    return AdminPageResult(
      content is List ? content.whereType<Map<String, dynamic>>().toList() : [],
      json['pagina'] as int? ?? page,
      json['ultima'] as bool? ?? true,
      json['totalElementos'] as int? ?? 0,
    );
  }
}

/// Los permisos efectivos y la doble aprobación siempre se validan en el servidor.
class AdminApiService {
  AdminApiService(this.api, this.session);
  final PaymentApiService api;
  final LoginSession session;

  void _requireExecutive() {
    if (!session.isExecutive) {
      throw const PaymentFailure(
        'Solo un ejecutivo puede usar esta sección.',
        status: 403,
      );
    }
  }

  Future<AdminPageResult> search(
    String section,
    Map<String, dynamic> filters,
    int page,
  ) async {
    _requireExecutive();
    final path = switch (section) {
      'clientes' => '/api/clientes/consulta',
      'usuarios' => '/api/usuarios/busqueda',
      'cuentas' => '/api/cuentas/activas/consulta',
      'conciliacion' => '/api/pagos/conciliacion/pendientes',
      _ => throw ArgumentError.value(section),
    };
    final data = await api.request(path, {
      ...filters,
      'pagina': page,
      'tamanio': 20,
    });
    return AdminPageResult.fromJson(data, page);
  }

  Future<Map<String, dynamic>> updateClient(int id, Map<String, dynamic> data) {
    _requireExecutive();
    return api.update('/api/clientes', {'id': id, 'datos': data});
  }

  Future<Map<String, dynamic>> deactivateClient(int id) {
    _requireExecutive();
    return api.update('/api/clientes', {'id': id}, method: 'DELETE');
  }

  Future<Map<String, dynamic>> deactivateAccount(String account) {
    _requireExecutive();
    return api.update('/api/cuentas', {
      'numeroCuenta': account,
      'cambios': {'activa': false},
    }, method: 'PATCH');
  }

  Future<Map<String, dynamic>> proposal(String id) {
    _requireExecutive();
    return api.request('/api/pagos/conciliacion/propuestas/consulta', {
      'propuestaId': id,
    });
  }

  Future<Map<String, dynamic>> account(String number) {
    _requireExecutive();
    return api.request('/api/cuentas/consulta', {'numeroCuenta': number});
  }

  Future<Map<String, dynamic>> balance(String number) {
    _requireExecutive();
    return api.request('/api/cuentas/saldo/consulta', {'numeroCuenta': number});
  }

  Future<Map<String, dynamic>> propose(Map<String, dynamic> data) {
    _requireExecutive();
    return api.request('/api/pagos/conciliacion/propuestas', data);
  }

  Future<Map<String, dynamic>> decide(
    String id,
    String observation, {
    required bool approve,
  }) {
    _requireExecutive();
    return api.request(
      '/api/pagos/conciliacion/propuestas/${approve ? 'aprobar' : 'rechazar'}',
      {'propuestaId': id, 'observacion': observation},
    );
  }
}
