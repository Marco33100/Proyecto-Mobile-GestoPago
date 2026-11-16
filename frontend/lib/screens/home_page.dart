import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import '../config/http_client_factory.dart';

import '../models/login_session.dart';
import '../services/auth_api_service.dart';
import '../services/home_data_service.dart';
import '../theme.dart';
import '../utils/money_format.dart';
import '../widgets/brand_wordmark.dart';
import '../widgets/paper_background.dart';
import '../widgets/responsive.dart';
import '../widgets/status_message.dart';
import 'account_page.dart';
import 'payment_page.dart';
import 'register_page.dart';
import 'management_page.dart';

/// Contenedor principal de la sesión: navegación adaptable con pestañas.
///
/// - Teléfono (< 600 px): barra de navegación inferior.
/// - Tablet y escritorio: barra lateral, con etiquetas completas desde 1200 px.
///
/// Cada pestaña se construye la primera vez que se visita y conserva su estado
/// al cambiar de pestaña.
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
  static const _homeTab = 0;
  static const _operationsTab = 1;
  static const _accountTab = 2;

  late final http.Client _client;
  late final HomeDataService _data;
  late Future<List<Map<String, dynamic>>> _accounts;
  late Future<List<Map<String, dynamic>>> _products;
  int _selectedTab = _homeTab;
  final Set<int> _visitedTabs = {_homeTab};
  int _paymentGeneration = 0;
  String? _paymentService;

  bool get _isExecutive => widget.session.isExecutive;

  @override
  void initState() {
    super.initState();
    _client = createHttpClient();
    _data = HomeDataService(_client, widget.session);
    _refresh();
  }

  @override
  void dispose() {
    _client.close();
    super.dispose();
  }

  void _refresh() {
    if (_isExecutive) {
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

  void _selectTab(int index) {
    if (index == _selectedTab) return;
    final leavingOperations = _selectedTab == _operationsTab;
    setState(() {
      _selectedTab = index;
      _visitedTabs.add(index);
      // Al volver de pagar, los saldos pudieron cambiar.
      if (index == _homeTab && leavingOperations && !_isExecutive) _refresh();
    });
  }

  /// Abre la pestaña de pagos; con [service] reinicia el formulario filtrado
  /// por ese servicio. Un pago pendiente se recupera de su almacenamiento.
  void _openPayment({String? service}) {
    if (service != null) {
      _paymentService = service;
      _paymentGeneration++;
    }
    _selectTab(_operationsTab);
  }

  Future<void> _openRegister() async {
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
    if (result != null) {
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text(result)));
    }
  }

  List<_Destination> get _destinations => [
    const _Destination(
      label: 'Inicio',
      icon: Icons.home_outlined,
      selectedIcon: Icons.home_rounded,
    ),
    _isExecutive
        ? const _Destination(
            label: 'Gestión',
            icon: Icons.manage_accounts_outlined,
            selectedIcon: Icons.manage_accounts_rounded,
          )
        : const _Destination(
            label: 'Servicios',
            icon: Icons.grid_view_outlined,
            selectedIcon: Icons.grid_view_rounded,
          ),
    const _Destination(
      label: 'Cuenta',
      icon: Icons.person_outline_rounded,
      selectedIcon: Icons.person_rounded,
    ),
  ];

  Widget _tab(int index, {required bool showBrand}) {
    if (!_visitedTabs.contains(index)) return const SizedBox.shrink();
    return switch (index) {
      _homeTab => _HomeDashboard(
        session: widget.session,
        firstName: _firstName,
        showBrand: showBrand,
        accounts: _accounts,
        products: _products,
        onReload: _reload,
        onPay: _openPayment,
        onRegister: _openRegister,
        onManage: () => _selectTab(_operationsTab),
      ),
      _operationsTab =>
        _isExecutive
            ? ManagementPage(session: widget.session, authApi: widget.authApi)
            : PaymentPage(
                key: ValueKey('payment-$_paymentGeneration'),
                session: widget.session,
                initialService: _paymentService,
              ),
      _accountTab => AccountPage(
        session: widget.session,
        onLogout: widget.onLogout,
        isLoggingOut: widget.isLoggingOut,
      ),
      _ => const SizedBox.shrink(),
    };
  }

  String get _firstName {
    final parts = widget.session.fullName.trim().split(RegExp(r'\s+'));
    return parts.isEmpty || parts.first.isEmpty ? 'bienvenido' : parts.first;
  }

  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, constraints) {
      final useRail = constraints.maxWidth >= Breakpoints.medium;
      final extendedRail = constraints.maxWidth >= Breakpoints.large;
      final tabs = IndexedStack(
        index: _selectedTab,
        children: [
          for (var index = 0; index < 3; index++)
            _tab(index, showBrand: !useRail),
        ],
      );
      final destinations = _destinations;

      if (!useRail) {
        return Scaffold(
          body: tabs,
          bottomNavigationBar: NavigationBar(
            selectedIndex: _selectedTab,
            onDestinationSelected: _selectTab,
            destinations: [
              for (final destination in destinations)
                NavigationDestination(
                  icon: Icon(destination.icon),
                  selectedIcon: Icon(destination.selectedIcon),
                  label: destination.label,
                ),
            ],
          ),
        );
      }

      return Scaffold(
        body: Row(
          children: [
            SafeArea(
              right: false,
              child: NavigationRail(
                extended: extendedRail,
                minExtendedWidth: 232,
                labelType: extendedRail
                    ? NavigationRailLabelType.none
                    : NavigationRailLabelType.all,
                selectedIndex: _selectedTab,
                onDestinationSelected: _selectTab,
                leading: Padding(
                  padding: const EdgeInsets.fromLTRB(8, 20, 8, 28),
                  child: extendedRail
                      ? const SizedBox(
                          width: 184,
                          child: BrandWordmark(compact: true),
                        )
                      : Semantics(
                          label: 'Gestopago',
                          child: const ExcludeSemantics(
                            child: Text(
                              'G',
                              style: TextStyle(
                                color: ink,
                                fontSize: 26,
                                fontWeight: FontWeight.w800,
                              ),
                            ),
                          ),
                        ),
                ),
                destinations: [
                  for (final destination in destinations)
                    NavigationRailDestination(
                      icon: Icon(destination.icon),
                      selectedIcon: Icon(destination.selectedIcon),
                      label: Text(destination.label),
                    ),
                ],
              ),
            ),
            const VerticalDivider(width: 1, color: outlineColor),
            Expanded(child: tabs),
          ],
        ),
      );
    },
  );
}

class _Destination {
  const _Destination({
    required this.label,
    required this.icon,
    required this.selectedIcon,
  });

  final String label;
  final IconData icon;
  final IconData selectedIcon;
}

class _HomeDashboard extends StatelessWidget {
  const _HomeDashboard({
    required this.session,
    required this.firstName,
    required this.showBrand,
    required this.accounts,
    required this.products,
    required this.onReload,
    required this.onPay,
    required this.onRegister,
    required this.onManage,
  });

  final LoginSession session;
  final String firstName;
  final bool showBrand;
  final Future<List<Map<String, dynamic>>> accounts;
  final Future<List<Map<String, dynamic>>> products;
  final Future<void> Function() onReload;
  final void Function({String? service}) onPay;
  final Future<void> Function() onRegister;
  final VoidCallback onManage;

  @override
  Widget build(BuildContext context) {
    final isExecutive = session.isExecutive;
    final textTheme = Theme.of(context).textTheme;
    return Scaffold(
      body: PaperBackground(
        child: SafeArea(
          child: RefreshIndicator(
            color: terracotta,
            onRefresh: onReload,
            child: CustomScrollView(
              physics: const AlwaysScrollableScrollPhysics(),
              slivers: [
                SliverPadding(
                  padding: const EdgeInsets.fromLTRB(24, 20, 24, 30),
                  sliver: SliverToBoxAdapter(
                    child: Center(
                      child: ConstrainedBox(
                        constraints: BoxConstraints(
                          maxWidth: isExecutive
                              ? ContentWidth.page
                              : ContentWidth.wide,
                        ),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: [
                            if (showBrand) ...[
                              const BrandWordmark(compact: true),
                              const SizedBox(height: 36),
                            ] else
                              const SizedBox(height: 16),
                            Text(
                              'Hola, $firstName',
                              style: textTheme.bodyLarge,
                            ),
                            const SizedBox(height: 8),
                            Text(
                              isExecutive
                                  ? 'Atención a clientes'
                                  : 'Tus servicios,\nen un solo lugar',
                              style: textTheme.headlineLarge,
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
                              style: textTheme.bodyLarge,
                            ),
                            const SizedBox(height: 24),
                            Align(
                              alignment: Alignment.centerLeft,
                              child: ConstrainedBox(
                                constraints: const BoxConstraints(
                                  maxWidth: 420,
                                ),
                                child: FilledButton.icon(
                                  onPressed: isExecutive
                                      ? onRegister
                                      : () => onPay(),
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
                              ),
                            ),
                            if (!isExecutive) ...[
                              const SizedBox(height: 32),
                              ResponsiveGrid(
                                minItemWidth: 380,
                                maxColumns: 2,
                                spacing: 32,
                                children: [
                                  Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.stretch,
                                    children: [
                                      _SectionHeading(
                                        title: 'Saldo disponible para pagos',
                                        trailing: IconButton(
                                          tooltip: 'Actualizar cuentas',
                                          onPressed: onReload,
                                          icon: const Icon(
                                            Icons.refresh_rounded,
                                          ),
                                        ),
                                      ),
                                      const SizedBox(height: 12),
                                      _AccountsSummary(
                                        future: accounts,
                                        onRetry: onReload,
                                      ),
                                      const SizedBox(height: 32),
                                    ],
                                  ),
                                  Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.stretch,
                                    children: [
                                      _SectionHeading(
                                        title: 'Servicios',
                                        trailing: TextButton(
                                          onPressed: () => onPay(),
                                          child: const Text('Ver catálogo'),
                                        ),
                                      ),
                                      const SizedBox(height: 12),
                                      _ServiceShortcuts(
                                        future: products,
                                        onRetry: onReload,
                                        onSelected: (service) =>
                                            onPay(service: service),
                                      ),
                                    ],
                                  ),
                                ],
                              ),
                            ],
                            if (isExecutive) ...[
                              const SizedBox(height: 12),
                              Align(
                                alignment: Alignment.centerLeft,
                                child: ConstrainedBox(
                                  constraints: const BoxConstraints(
                                    maxWidth: 420,
                                  ),
                                  child: OutlinedButton.icon(
                                    onPressed: onManage,
                                    icon: const Icon(
                                      Icons.manage_accounts_outlined,
                                    ),
                                    label: const Text(
                                      'Gestionar clientes y operaciones',
                                    ),
                                  ),
                                ),
                              ),
                              const SizedBox(height: 28),
                              const StatusMessage(
                                icon: Icons.info_outline_rounded,
                                message:
                                    'El registro crea el acceso y la cuenta del cliente en una sola operación.',
                              ),
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
    );
  }
}

/// Mensaje para el usuario a partir de un error de carga del inicio.
String _friendlyError(Object? error) => error is HomeDataException
    ? error.message
    : 'No fue posible cargar la información. Inténtalo de nuevo.';

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
            return const StatusMessage.loading(message: 'Cargando cuentas');
          }
          if (snapshot.hasError) {
            return StatusMessage(
              icon: Icons.wifi_off_rounded,
              tone: StatusTone.neutral,
              message: _friendlyError(snapshot.error),
              action: TextButton(
                onPressed: onRetry,
                child: const Text('Reintentar'),
              ),
            );
          }
          final accounts = snapshot.data ?? const [];
          if (accounts.isEmpty) {
            return const StatusMessage(
              icon: Icons.account_balance_wallet_outlined,
              tone: StatusTone.neutral,
              message: 'No hay cuentas activas disponibles para pagar.',
            );
          }

          return Container(
            padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 6),
            decoration: BoxDecoration(
              color: warmSurface,
              borderRadius: BorderRadius.circular(AppRadius.lg),
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
              borderRadius: BorderRadius.circular(AppRadius.sm),
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
        return const StatusMessage.loading(message: 'Cargando catálogo');
      }
      if (snapshot.hasError) {
        return StatusMessage(
          icon: Icons.search_off_rounded,
          tone: StatusTone.neutral,
          message: _friendlyError(snapshot.error),
          action: TextButton(
            onPressed: onRetry,
            child: const Text('Reintentar'),
          ),
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
        return const StatusMessage(
          icon: Icons.receipt_long_outlined,
          tone: StatusTone.neutral,
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
                borderRadius: BorderRadius.circular(AppRadius.md),
                child: InkWell(
                  borderRadius: BorderRadius.circular(AppRadius.md),
                  onTap: () => onSelected(entry.key),
                  child: Container(
                    constraints: const BoxConstraints(minHeight: 68),
                    padding: const EdgeInsets.symmetric(
                      horizontal: 17,
                      vertical: 12,
                    ),
                    decoration: BoxDecoration(
                      borderRadius: BorderRadius.circular(AppRadius.md),
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
      ?trailing,
    ],
  );
}
