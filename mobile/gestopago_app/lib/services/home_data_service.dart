import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:http/http.dart' as http;

import '../config/api_config.dart';
import '../models/login_session.dart';

class HomeDataException implements Exception {
  const HomeDataException(this.message);
  final String message;

  @override
  String toString() => message;
}

class HomeDataService {
  HomeDataService(this.client, this.session);

  final http.Client client;
  final LoginSession session;

  Future<List<Map<String, dynamic>>> myAccounts() async {
    final page = await _request(
      '/api/cuentas/mias/consulta',
      body: const {'pagina': 0, 'tamanio': 100},
    );
    final content = page['contenido'];
    if (content is! List) return const [];
    return content
        .whereType<Map<String, dynamic>>()
        .where((account) => account['activa'] == true)
        .toList(growable: false);
  }

  Future<List<Map<String, dynamic>>> products() async {
    final response = await _request('/api/products');
    final container = response['productos'];
    final items = container is Map ? container['productos'] : null;
    if (items is! List) return const [];
    return items
        .whereType<Map<String, dynamic>>()
        .where((product) => const [1, 2, 4].contains(product['tipoFront']))
        .where((product) => _text(product['servicio']).isNotEmpty)
        .toList(growable: false);
  }

  Future<Map<String, dynamic>> _request(
    String path, {
    Map<String, dynamic>? body,
  }) async {
    try {
      final uri = Uri.parse('${ApiConfig.baseUrl}$path');
      final response =
          await (body == null
                  ? client.get(uri, headers: _headers)
                  : client.post(uri, headers: _headers, body: jsonEncode(body)))
              .timeout(const Duration(seconds: 12));
      if (response.statusCode == 401) {
        throw const HomeDataException(
          'Tu sesión expiró. Inicia sesión de nuevo.',
        );
      }
      if (response.statusCode == 403) {
        throw const HomeDataException(
          'No tienes permiso para consultar estos datos.',
        );
      }
      if (response.statusCode < 200 || response.statusCode >= 300) {
        throw const HomeDataException(
          'No pudimos cargar esta información. Inténtalo de nuevo.',
        );
      }

      final decoded = jsonDecode(response.body);
      if (decoded is! Map<String, dynamic>) {
        throw const HomeDataException(
          'El servidor devolvió una respuesta no reconocida.',
        );
      }
      final code = decoded['codigo'];
      if (code == 0) return const {};
      final data = decoded['datos'];
      if (code != 1 || data is! Map<String, dynamic>) {
        throw const HomeDataException(
          'No pudimos cargar esta información. Inténtalo de nuevo.',
        );
      }
      return data;
    } on HomeDataException {
      rethrow;
    } on TimeoutException {
      throw const HomeDataException(
        'La solicitud tardó demasiado. Inténtalo de nuevo.',
      );
    } on SocketException {
      throw const HomeDataException(
        'No hay conexión con el servidor. Revisa tu red.',
      );
    } on http.ClientException {
      throw const HomeDataException(
        'No hay conexión con el servidor. Revisa tu red.',
      );
    } on FormatException {
      throw const HomeDataException(
        'El servidor devolvió una respuesta no reconocida.',
      );
    }
  }

  Map<String, String> get _headers => {
    'Authorization': '${session.tokenType} ${session.accessToken}',
    'Content-Type': 'application/json',
    'Accept': 'application/json',
  };

  static String _text(Object? value) => value?.toString().trim() ?? '';
}
