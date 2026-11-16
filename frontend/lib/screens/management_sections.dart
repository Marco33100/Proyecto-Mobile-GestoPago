import 'package:flutter/material.dart';

import '../services/admin_api_service.dart';
import '../services/auth_api_service.dart';
import 'admin_search_page.dart';
import 'bank_accounts_page.dart';
import 'reconciliation_page.dart';

/// Secciones de trabajo del ejecutivo. Las comparten el menú de Gestión y el
/// inicio del ejecutivo para que nombres y destinos sean siempre los mismos.
enum ManagementSection {
  clientes(
    'Clientes',
    'Busca, consulta y actualiza datos de clientes.',
    Icons.people_alt_outlined,
  ),
  usuarios(
    'Usuarios',
    'Encuentra accesos con filtros y paginación.',
    Icons.manage_accounts_outlined,
  ),
  cuentas(
    'Cuentas bancarias',
    'Consulta cuentas activas y sus saldos.',
    Icons.account_balance_outlined,
  ),
  conciliacion(
    'Conciliación',
    'Revisa pagos en revisión con doble aprobación.',
    Icons.fact_check_outlined,
  );

  const ManagementSection(this.title, this.description, this.icon);

  final String title;
  final String description;
  final IconData icon;

  Widget page(AdminApiService api, AuthApiService authApi) => switch (this) {
    clientes => AdminSearchPage(
      section: 'clientes',
      api: api,
      authApi: authApi,
    ),
    usuarios => AdminSearchPage(
      section: 'usuarios',
      api: api,
      authApi: authApi,
    ),
    cuentas => BankAccountsPage(api: api),
    conciliacion => ReconciliationPage(api: api),
  };

  Future<void> open(
    BuildContext context,
    AdminApiService api,
    AuthApiService authApi,
  ) => Navigator.push<void>(
    context,
    MaterialPageRoute(builder: (_) => page(api, authApi)),
  );
}
