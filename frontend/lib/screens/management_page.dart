import 'package:flutter/material.dart';
import '../config/http_client_factory.dart';
import '../models/login_session.dart';
import '../services/admin_api_service.dart';
import '../services/auth_api_service.dart';
import '../services/payment_api_service.dart';
import '../theme.dart';
import '../widgets/management_layout.dart';
import '../widgets/responsive.dart';
import 'admin_search_page.dart';
import 'reconciliation_page.dart';
import 'bank_accounts_page.dart';

class ManagementPage extends StatefulWidget {
  const ManagementPage({
    super.key,
    required this.session,
    required this.authApi,
    this.api,
  });
  final LoginSession session;
  final AuthApiService authApi;
  final AdminApiService? api;
  @override
  State<ManagementPage> createState() => _ManagementPageState();
}

class _ManagementPageState extends State<ManagementPage> {
  final _client = createHttpClient();
  late final _api =
      widget.api ??
      AdminApiService(
        PaymentApiService(_client, widget.session),
        widget.session,
      );
  @override
  void dispose() {
    _client.close();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ManagementLayout(
    title: 'Gestión',
    maxWidth: ContentWidth.form + 280,
    children: [
      if (!widget.session.isExecutive)
        const Text('Solo un ejecutivo puede usar esta sección.')
      else ...[
        const Text(
          'Consulta información y atiende operaciones con tu acceso de ejecutivo.',
        ),
        const SizedBox(height: 22),
        ResponsiveGrid(
          minItemWidth: 320,
          maxColumns: 2,
          children: [
            for (final item in [
              (
                'Clientes',
                'Buscar, consultar y actualizar información.',
                Icons.people_outline,
                'clientes',
              ),
              (
                'Usuarios',
                'Buscar usuarios con filtros y paginación.',
                Icons.manage_accounts_outlined,
                'usuarios',
              ),
              (
                'Cuentas bancarias',
                'Consultar cuentas activas y actualizar saldos.',
                Icons.account_balance_outlined,
                'cuentas',
              ),
              (
                'Conciliación',
                'Revisar pagos y propuestas con doble aprobación.',
                Icons.fact_check_outlined,
                'conciliacion',
              ),
            ])
              ManagementCard(
                children: [
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: Icon(item.$3),
                    title: Text(item.$1),
                    subtitle: Text(item.$2),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: () => Navigator.push(
                      context,
                      MaterialPageRoute<void>(
                        builder: (_) => item.$4 == 'conciliacion'
                            ? ReconciliationPage(api: _api)
                            : item.$4 == 'cuentas'
                            ? BankAccountsPage(api: _api)
                            : AdminSearchPage(
                                section: item.$4,
                                api: _api,
                                authApi: widget.authApi,
                              ),
                      ),
                    ),
                  ),
                ],
              ),
          ],
        ),
      ],
    ],
  );
}
