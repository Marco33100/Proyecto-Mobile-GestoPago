import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;

import '../models/login_session.dart';
import '../services/auth_api_service.dart';
import '../services/home_data_service.dart';
import '../theme.dart';
import '../utils/money_format.dart';
import '../widgets/brand_wordmark.dart';
import '../widgets/paper_background.dart';
import 'account_page.dart';
import 'payment_page.dart';
import 'register_page.dart';
import 'management_page.dart';

class HomePage extends StatefulWidget {
  const HomePage({
    super.key,
    required this.session,
    required this.onLogout,
    required this.authApi,
    this.isLoggingOut = false,
  });

  final LoginSession session;
  final Future<void> Function() onLogout;
  final AuthApiService authApi;
  final bool isLoggingOut;

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  late final http.Client _client;
  late final HomeDataService _data;
  late Future<List<Map<String, dynamic>>> _accounts;
  late Future<List<Map<String, dynamic>>> _products;
  int _selectedDestination = 0;

  @override
  void initState() {
    super.initState();
    _client = http.Client();
    _data = HomeDataService(_client, widget.session);
    _refresh();
  }

  @override
  void dispose() {
    _client.close();
    super.dispose();
  }

  void _refresh() {
    if (widget.session.isExecutive) {
      _accounts = Future.value(const []);
      _products = Future.value(const []);
      return;
    }
    _accounts = _data.myAccounts();
    _products = _data.products();
  }

  Future<void> _reload() async {
    setState(_refresh);
    try {
      await Future.wait([_accounts, _products]);
    } catch (_) {
      // FutureBuilders show the individual account and catalog error states.
    }
  }

  String get _firstName {
    final parts = widget.session.fullName.trim().split(RegExp(r'\s+'));
    return parts.isEmpty || parts.first.isEmpty ? 'bienvenido' : parts.first;
  }

  Future<void> _openPayment({String? service}) async {
    setState(() => _selectedDestination = 1);
    await Navigator.of(context).push<void>(
      MaterialPageRoute(
        builder: (_) =>
            PaymentPage(session: widget.session, initialService: service),
      ),
    );
    if (mounted) {
      setState(() {
        _selectedDestination = 0;
        _refresh();
      });
    }
  }

  Future<void> _openRegister() async {
    setState(() => _selectedDestination = 1);
    final result = await Navigator.of(context).push<String>(
      MaterialPageRoute(
        builder: (_) => RegisterPage(
          authApi: widget.authApi,
          session: widget.session,
          onSessionInvalid: widget.onLogout,
        ),
      ),
    );
    if (!mounted) return;
    setState(() => _selectedDestination = 0);
    if (result != null) {
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text(result)));
    }
  }

  Future<void> _openManagement() async {
    setState(() => _selectedDestination = 1);
    await Navigator.of(context).push<void>(
      MaterialPageRoute(
        builder: (_) =>
            ManagementPage(session: widget.session, authApi: widget.authApi),
      ),
    );
    if (mounted) setState(() => _selectedDestination = 0);
  }

  Future<void> _openAccount() async {
    setState(() => _selectedDestination = 2);
    await Navigator.of(context).push<void>(
      MaterialPageRoute(
        builder: (_) => AccountPage(
          session: widget.session,
          onLogout: () async {
            Navigator.of(context).pop();
            await widget.onLogout();
          },
          isLoggingOut: widget.isLoggingOut,
        ),
      ),
    );
    if (mounted) setState(() => _selectedDestination = 0);
  }

  @override
  Widget build(BuildContext context) {
    final isExecutive = widget.session.isExecutive;
    return Scaffold(
      body: PaperBackground(
        child: SafeArea(
          child: RefreshIndicator(
            color: terracotta,
            onRefresh: _reload,
            child: CustomScrollView(
              physics: const AlwaysScrollableScrollPhysics(),
              slivers: [
                SliverPadding(
                  padding: const EdgeInsets.fromLTRB(24, 20, 24, 30),
                  sliver: SliverToBoxAdapter(
                    child: Center(
                      child: ConstrainedBox(
                        constraints: const BoxConstraints(maxWidth: 620),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: [
                            const BrandWordmark(compact: true),
                            const SizedBox(height: 36),
                            Text(
                              'Hola, $_firstName',
                              style: Theme.of(context).textTheme.bodyLarge,
                            ),
                            const SizedBox(height: 8),
                            Text(
                              isExecutive
                                  ? 'Atención a clientes'
                                  : 'Tus servicios,\nen un solo lugar',
                              style: Theme.of(context).textTheme.headlineLarge,
                            ),
                            const SizedBox(height: 12),
                            Container(
                              width: 48,
                              height: 3,
                              decoration: BoxDecoration(
                                color: terracotta,
                                borderRadius: BorderRadius.circular(3),
                              ),
                            ),
                            const SizedBox(height: 24),
                            Text(
                              isExecutive
                                  ? 'Registra los datos del cliente para crear su acceso.'
                                  : 'Consulta tus cuentas y prepara tus pagos con claridad.',
                              style: Theme.of(context).textTheme.bodyLarge,
                            ),
                            const SizedBox(height: 24),
                            FilledButton.icon(
                              onPressed: isExecutive
                                  ? _openRegister
                                  : () => _openPayment(),
                              icon: Icon(
                                isExecutive
                                    ? Icons.person_add_alt_1_rounded
                                    : Icons.receipt_long_rounded,
                              ),
                              label: Text(
                                isExecutive
                                    ? 'Registrar cliente'
                                    : 'Pagar un servicio',
                              ),
                              iconAlignment: IconAlignment.start,
                            ),
                            if (!isExecutive) ...[
                              const SizedBox(height: 32),
                              _SectionHeading(
                                title: 'Saldo disponible para pagos',
                                trailing: IconButton(
                                  tooltip: 'Actualizar cuentas',
                                  onPressed: _reload,
                                  icon: const Icon(Icons.refresh_rounded),
                                ),
                              ),
                              const SizedBox(height: 12),
                              _AccountsSummary(
                                future: _accounts,
                                onRetry: _reload,
                              ),
                              const SizedBox(height: 32),
                              _SectionHeading(
                                title: 'Servicios',
                                trailing: TextButton(
                                  onPressed: () => _openPayment(),
                                  child: const Text('Ver catálogo'),
                                ),
                              ),
                              const SizedBox(height: 12),
                              _ServiceShortcuts(
                                future: _products,
                                onRetry: _reload,
                                onSelected: (service) =>
                                    _openPayment(service: service),
                              ),
                            ],
                            if (isExecutive) ...[
                              const SizedBox(height: 12),
                              OutlinedButton.icon(
                                onPressed: _openManagement,
                                icon: const Icon(
                                  Icons.manage_accounts_outlined,
                                ),
                                label: const Text(
                                  'Gestionar clientes y operaciones',
                                ),
                              ),
                              const SizedBox(height: 28),
                              const _ExecutiveNote(),
                            ],
                          ],
                        ),
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _selectedDestination,
        onDestinationSelected: (index) {
          if (index == 0) {
            setState(() => _selectedDestination = 0);
          } else if (index == 1) {
            isExecutive ? _openManagement() : _openPayment();
          } else {
            _openAccount();
          }
        },
        destinations: [
          const NavigationDestination(
            icon: Icon(Icons.home_outlined),
            selectedIcon: Icon(Icons.home_rounded),
            label: 'Inicio',
          ),
          NavigationDestination(
            icon: Icon(
              isExecutive
                  ? Icons.person_add_alt_1_outlined
                  : Icons.grid_view_rounded,
            ),
            selectedIcon: Icon(
              isExecutive
                  ? Icons.person_add_alt_1_rounded
                  : Icons.grid_view_rounded,
            ),
            label: isExecutive ? 'Gestión' : 'Servicios',
          ),
          const NavigationDestination(
            icon: Icon(Icons.person_outline_rounded),
            selectedIcon: Icon(Icons.person_rounded),
            label: 'Cuenta',
          ),
        ],
      ),
    );
  }
}

class _AccountsSummary extends StatelessWidget {
  const _AccountsSummary({required this.future, required this.onRetry});

  final Future<List<Map<String, dynamic>>> future;
  final Future<void> Function() onRetry;

  @override
  Widget build(BuildContext context) =>
      FutureBuilder<List<Map<String, dynamic>>>(
        future: future,
        builder: (context, snapshot) {
          if (snapshot.connectionState == ConnectionState.waiting) {
            return const _LoadingSurface(label: 'Cargando cuentas');
          }
          if (snapshot.hasError) {
            return _InlineState(
              icon: Icons.wifi_off_rounded,
              message: snapshot.error.toString(),
              actionLabel: 'Reintentar',
              onPressed: onRetry,
            );
          }
          final accounts = snapshot.data ?? const [];
          if (accounts.isEmpty) {
            return const _InlineState(
              icon: Icons.account_balance_wallet_outlined,
              message: 'No hay cuentas activas disponibles para pagar.',
            );
          }

          return Container(
            padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 6),
            decoration: BoxDecoration(
              color: warmSurface,
              borderRadius: BorderRadius.circular(22),
              border: Border.all(color: outlineColor),
            ),
            child: Column(
              children: [
                for (var index = 0; index < accounts.length; index++) ...[
                  if (index > 0) const Divider(height: 1),
                  _AccountBalanceRow(account: accounts[index]),
                ],
              ],
            ),
          );
        },
      );
}

class _AccountBalanceRow extends StatelessWidget {
  const _AccountBalanceRow({required this.account});

  final Map<String, dynamic> account;

  @override
  Widget build(BuildContext context) {
    final number = account['numeroCuenta']?.toString() ?? '';
    final masked = number.length > 4
        ? '•••• ${number.substring(number.length - 4)}'
        : number;
    final available = account['saldoDisponible'];
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 15),
      child: Row(
        children: [
          Container(
            width: 42,
            height: 42,
            decoration: BoxDecoration(
              color: sage,
              borderRadius: BorderRadius.circular(14),
            ),
            child: const Icon(
              Icons.account_balance_wallet_outlined,
              color: ink,
            ),
          ),
          const SizedBox(width: 13),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(masked, style: Theme.of(context).textTheme.titleMedium),
                const SizedBox(height: 3),
                Text(
                  'Disponible en Gestopago',
                  style: Theme.of(context).textTheme.bodySmall,
                ),
              ],
            ),
          ),
          const SizedBox(width: 8),
          Flexible(
            child: Text(
              MoneyFormat.mxn(available),
              textAlign: TextAlign.end,
              style: Theme.of(context).textTheme.titleMedium,
            ),
          ),
        ],
      ),
    );
  }
}

class _ServiceShortcuts extends StatelessWidget {
  const _ServiceShortcuts({
    required this.future,
    required this.onRetry,
    required this.onSelected,
  });

  final Future<List<Map<String, dynamic>>> future;
  final Future<void> Function() onRetry;
  final ValueChanged<String> onSelected;

  @override
  Widget build(
    BuildContext context,
  ) => FutureBuilder<List<Map<String, dynamic>>>(
    future: future,
    builder: (context, snapshot) {
      if (snapshot.connectionState == ConnectionState.waiting) {
        return const _LoadingSurface(label: 'Cargando catálogo');
      }
      if (snapshot.hasError) {
        return _InlineState(
          icon: Icons.search_off_rounded,
          message: snapshot.error.toString(),
          actionLabel: 'Reintentar',
          onPressed: onRetry,
        );
      }
      final groups = <String, int>{};
      for (final product in snapshot.data ?? const []) {
        final service = product['servicio']?.toString().trim() ?? '';
        if (service.isNotEmpty) {
          groups.update(service, (count) => count + 1, ifAbsent: () => 1);
        }
      }
      if (groups.isEmpty) {
        return const _InlineState(
          icon: Icons.receipt_long_outlined,
          message:
              'El catálogo no tiene servicios disponibles en este momento.',
        );
      }
      return Column(
        children: [
          for (final entry in groups.entries.take(4))
            Padding(
              padding: const EdgeInsets.only(bottom: 10),
              child: Material(
                color: warmSurface,
                borderRadius: BorderRadius.circular(19),
                child: InkWell(
                  borderRadius: BorderRadius.circular(19),
                  onTap: () => onSelected(entry.key),
                  child: Container(
                    constraints: const BoxConstraints(minHeight: 68),
                    padding: const EdgeInsets.symmetric(
                      horizontal: 17,
                      vertical: 12,
                    ),
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(19),
                      border: Border.all(color: outlineColor),
                    ),
                    child: Row(
                      children: [
                        Icon(
                          _serviceIcon(entry.key),
                          color: terracotta,
                          size: 25,
                        ),
                        const SizedBox(width: 15),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              Text(
                                entry.key,
                                style: Theme.of(context).textTheme.titleMedium,
                              ),
                              Text(
                                '${entry.value} ${entry.value == 1 ? 'opción' : 'opciones'}',
                                style: Theme.of(context).textTheme.bodySmall,
                              ),
                            ],
                          ),
                        ),
                        const Icon(Icons.chevron_right_rounded, color: ink),
                      ],
                    ),
                  ),
                ),
              ),
            ),
        ],
      );
    },
  );
}

IconData _serviceIcon(String name) {
  final value = name.toLowerCase();
  if (value.contains('luz') || value.contains('energ')) {
    return Icons.bolt_outlined;
  }
  if (value.contains('agua')) {
    return Icons.water_drop_outlined;
  }
  if (value.contains('internet')) {
    return Icons.wifi_rounded;
  }
  if (value.contains('tel') || value.contains('celular')) {
    return Icons.phone_iphone_rounded;
  }
  return Icons.receipt_long_outlined;
}

class _SectionHeading extends StatelessWidget {
  const _SectionHeading({required this.title, this.trailing});

  final String title;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) => Row(
    crossAxisAlignment: CrossAxisAlignment.center,
    children: [
      Expanded(
        child: Text(title, style: Theme.of(context).textTheme.titleLarge),
      ),
      if (trailing != null) trailing!,
    ],
  );
}

class _LoadingSurface extends StatelessWidget {
  const _LoadingSurface({required this.label});
  final String label;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(18),
    decoration: BoxDecoration(
      color: warmSurface,
      borderRadius: BorderRadius.circular(19),
      border: Border.all(color: outlineColor),
    ),
    child: Row(
      children: [
        const SizedBox.square(
          dimension: 18,
          child: CircularProgressIndicator(strokeWidth: 2),
        ),
        const SizedBox(width: 12),
        Text(label),
      ],
    ),
  );
}

class _InlineState extends StatelessWidget {
  const _InlineState({
    required this.icon,
    required this.message,
    this.actionLabel,
    this.onPressed,
  });

  final IconData icon;
  final String message;
  final String? actionLabel;
  final Future<void> Function()? onPressed;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(18),
    decoration: BoxDecoration(
      color: warmSurface,
      borderRadius: BorderRadius.circular(19),
      border: Border.all(color: outlineColor),
    ),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(icon, color: terracotta),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(message, style: Theme.of(context).textTheme.bodyMedium),
              if (actionLabel != null && onPressed != null) ...[
                const SizedBox(height: 6),
                TextButton(onPressed: onPressed, child: Text(actionLabel!)),
              ],
            ],
          ),
        ),
      ],
    ),
  );
}

class _ExecutiveNote extends StatelessWidget {
  const _ExecutiveNote();

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(18),
    decoration: BoxDecoration(
      color: sage,
      borderRadius: BorderRadius.circular(20),
    ),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Icon(Icons.info_outline_rounded, color: ink),
        const SizedBox(width: 12),
        Expanded(
          child: Text(
            'El registro crea el acceso y la cuenta del cliente en una sola operación.',
            style: Theme.of(context).textTheme.bodyMedium,
          ),
        ),
      ],
    ),
  );
}
