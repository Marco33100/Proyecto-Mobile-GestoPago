import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import '../config/http_client_factory.dart';

import '../models/login_session.dart';
import '../services/admin_api_service.dart';
import '../services/auth_api_service.dart';
import '../services/home_data_service.dart';
import '../services/payment_api_service.dart';
import '../theme.dart';
import '../utils/money_format.dart';
import '../widgets/brand_wordmark.dart';
import '../widgets/paper_background.dart';
import '../widgets/responsive.dart';
import '../widgets/status_message.dart';
import '../widgets/surfaces.dart';
import 'account_page.dart';
import 'management_sections.dart';
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
  late final AdminApiService _adminApi = AdminApiService(
    PaymentApiService(_client, widget.session),
    widget.session,
  );
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
        onProfile: () => _selectTab(_accountTab),
        onSection: (section) =>
            section.open(context, _adminApi, widget.authApi),
      ),
      _operationsTab =>
        _isExecutive
            ? ManagementPage(
                session: widget.session,
                authApi: widget.authApi,
                api: _adminApi,
              )
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

  String? get _firstName {
    final parts = widget.session.fullName.trim().split(RegExp(r'\s+'));
    return parts.isEmpty || parts.first.isEmpty ? null : parts.first;
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
          bottomNavigationBar: _UnderlineNavBar(
            key: const ValueKey('bottom-nav'),
            selected: _selectedTab,
            onSelected: _selectTab,
            destinations: destinations,
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
                minExtendedWidth: 248,
                labelType: extendedRail
                    ? NavigationRailLabelType.none
                    : NavigationRailLabelType.all,
                selectedIndex: _selectedTab,
                onDestinationSelected: _selectTab,
                leading: Padding(
                  padding: const EdgeInsets.fromLTRB(8, 24, 8, 32),
                  child: extendedRail
                      ? const SizedBox(
                          width: 200,
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
                trailing: Expanded(
                  child: Align(
                    alignment: Alignment.bottomCenter,
                    child: Padding(
                      padding: const EdgeInsets.only(bottom: AppSpace.xl),
                      child: _UserChip(
                        session: widget.session,
                        extended: extendedRail,
                        onTap: () => _selectTab(_accountTab),
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

/// Barra inferior del teléfono: ícono de línea, etiqueta y un subrayado
/// terracota bajo la pestaña activa.
class _UnderlineNavBar extends StatelessWidget {
  const _UnderlineNavBar({
    super.key,
    required this.selected,
    required this.onSelected,
    required this.destinations,
  });

  final int selected;
  final ValueChanged<int> onSelected;
  final List<_Destination> destinations;

  @override
  Widget build(BuildContext context) {
    final duration = AppMotion.of(context, AppMotion.hover);
    return Material(
      color: warmSurface,
      child: DecoratedBox(
        decoration: const BoxDecoration(
          border: Border(top: BorderSide(color: outlineColor)),
        ),
        child: SafeArea(
          top: false,
          child: SizedBox(
            height: 74,
            child: Row(
              children: [
                for (var index = 0; index < destinations.length; index++)
                  Expanded(
                    child: Semantics(
                      selected: index == selected,
                      button: true,
                      child: InkWell(
                        onTap: () => onSelected(index),
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Icon(
                              destinations[index].icon,
                              size: 26,
                              color: index == selected ? ink : mutedInk,
                            ),
                            const SizedBox(height: AppSpace.xs),
                            Text(
                              destinations[index].label,
                              style: TextStyle(
                                fontSize: 13,
                                color: index == selected ? ink : mutedInk,
                                fontWeight: index == selected
                                    ? FontWeight.w700
                                    : FontWeight.w500,
                              ),
                            ),
                            const SizedBox(height: AppSpace.xs + 2),
                            AnimatedContainer(
                              duration: duration,
                              curve: AppMotion.curve,
                              width: index == selected ? 40 : 0,
                              height: 3,
                              decoration: BoxDecoration(
                                color: terracotta,
                                borderRadius: BorderRadius.circular(3),
                              ),
                            ),
                          ],
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

/// Ficha del usuario al pie de la barra lateral. Lleva a la pestaña Cuenta.
class _UserChip extends StatelessWidget {
  const _UserChip({
    required this.session,
    required this.extended,
    required this.onTap,
  });

  final LoginSession session;
  final bool extended;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final role = session.isExecutive ? 'Ejecutivo' : 'Cliente';
    final avatar = CircleAvatar(
      radius: 20,
      backgroundColor: sage,
      foregroundColor: ink,
      child: Text(
        initialsOf(session.fullName),
        style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 14),
      ),
    );
    if (!extended) {
      return Tooltip(
        message: '${session.fullName} · $role',
        child: Semantics(
          button: true,
          label: 'Tu perfil: ${session.fullName}, $role',
          child: ExcludeSemantics(
            child: InkWell(
              customBorder: const CircleBorder(),
              onTap: onTap,
              child: Padding(padding: const EdgeInsets.all(4), child: avatar),
            ),
          ),
        ),
      );
    }
    final textTheme = Theme.of(context).textTheme;
    return SizedBox(
      width: 216,
      child: Material(
        color: paper,
        borderRadius: BorderRadius.circular(AppRadius.md),
        child: InkWell(
          borderRadius: BorderRadius.circular(AppRadius.md),
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.all(AppSpace.md),
            child: Row(
              children: [
                avatar,
                const SizedBox(width: AppSpace.md),
                Expanded(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        session.fullName,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: textTheme.titleMedium?.copyWith(fontSize: 14),
                      ),
                      Text(role, style: textTheme.bodySmall),
                    ],
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
    required this.onProfile,
    required this.onSection,
  });

  final LoginSession session;
  final String? firstName;
  final bool showBrand;
  final Future<List<Map<String, dynamic>>> accounts;
  final Future<List<Map<String, dynamic>>> products;
  final Future<void> Function() onReload;
  final void Function({String? service}) onPay;
  final Future<void> Function() onRegister;
  final VoidCallback onProfile;
  final void Function(ManagementSection section) onSection;

  Widget _header(bool isExecutive, {bool accent = false}) => PageHeader(
    eyebrow: firstName == null ? 'Hola' : 'Hola, $firstName',
    title: isExecutive
        ? 'Atención a clientes'
        : 'Tus servicios, en un solo lugar',
    accent: accent,
  );

  @override
  Widget build(BuildContext context) {
    final isExecutive = session.isExecutive;
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
                  padding: const EdgeInsets.fromLTRB(24, 20, 24, 40),
                  sliver: SliverToBoxAdapter(
                    child: Center(
                      child: ConstrainedBox(
                        constraints: const BoxConstraints(
                          maxWidth: ContentWidth.wide,
                        ),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: [
                            if (showBrand)
                              // Teléfono: marca, saludo y arcos decorativos.
                              Stack(
                                clipBehavior: Clip.none,
                                children: [
                                  const Positioned(
                                    right: -24,
                                    top: -20,
                                    child: ArchesDecoration(),
                                  ),
                                  Padding(
                                    padding: const EdgeInsets.only(right: 96),
                                    child: Column(
                                      crossAxisAlignment:
                                          CrossAxisAlignment.start,
                                      children: [
                                        const BrandWordmark(compact: true),
                                        const SizedBox(height: AppSpace.xxl),
                                        _header(isExecutive, accent: true),
                                      ],
                                    ),
                                  ),
                                ],
                              )
                            else ...[
                              const SizedBox(height: AppSpace.xl),
                              _header(isExecutive),
                            ],
                            const SizedBox(height: AppSpace.xxl),
                            if (isExecutive)
                              _ExecutiveDashboard(
                                onRegister: onRegister,
                                onSection: onSection,
                              )
                            else
                              _ClientDashboard(
                                accounts: accounts,
                                products: products,
                                onReload: onReload,
                                onPay: onPay,
                                onProfile: onProfile,
                              ),
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

/// Dos columnas en pantallas anchas (proporción [leftFlex]:[rightFlex]);
/// apiladas en teléfono.
class _SplitRow extends StatelessWidget {
  const _SplitRow({
    required this.left,
    required this.right,
    this.leftFlex = 3,
    this.rightFlex = 2,
    this.equalHeight = false,
  });

  final Widget left;
  final Widget right;
  final int leftFlex;
  final int rightFlex;

  /// Iguala la altura de ambas columnas. No usarlo si algún hijo contiene un
  /// LayoutBuilder (no admite medidas intrínsecas).
  final bool equalHeight;

  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, constraints) {
      if (constraints.maxWidth < 760) {
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            left,
            const SizedBox(height: AppSpace.xl),
            right,
          ],
        );
      }
      final row = Row(
        crossAxisAlignment: equalHeight
            ? CrossAxisAlignment.stretch
            : CrossAxisAlignment.start,
        children: [
          Expanded(flex: leftFlex, child: left),
          const SizedBox(width: AppSpace.xl),
          Expanded(flex: rightFlex, child: right),
        ],
      );
      return equalHeight ? IntrinsicHeight(child: row) : row;
    },
  );
}

ButtonStyle _onInkButton() => FilledButton.styleFrom(
  backgroundColor: warmSurface,
  foregroundColor: ink,
  minimumSize: const Size(0, 52),
  padding: const EdgeInsets.symmetric(horizontal: AppSpace.xl),
);

// ───────────────────────────── Cliente ─────────────────────────────

class _ClientDashboard extends StatelessWidget {
  const _ClientDashboard({
    required this.accounts,
    required this.products,
    required this.onReload,
    required this.onPay,
    required this.onProfile,
  });

  final Future<List<Map<String, dynamic>>> accounts;
  final Future<List<Map<String, dynamic>>> products;
  final Future<void> Function() onReload;
  final void Function({String? service}) onPay;
  final VoidCallback onProfile;

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      _SplitRow(
        equalHeight: true,
        left: _BalanceHero(future: accounts, onPay: onPay, onRetry: onReload),
        right: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            ActionCard(
              icon: Icons.grid_view_rounded,
              title: 'Explorar el catálogo',
              description: 'Luz, agua, telefonía, recargas y más.',
              onTap: () => onPay(),
              accent: true,
            ),
            const SizedBox(height: AppSpace.md),
            ActionCard(
              icon: Icons.verified_user_outlined,
              title: 'Seguridad de pagos',
              description: 'Configura o cambia tu autenticador.',
              onTap: () => onPay(),
            ),
            const SizedBox(height: AppSpace.md),
            ActionCard(
              icon: Icons.person_outline_rounded,
              title: 'Mi perfil',
              description: 'Tus datos y tu contraseña.',
              onTap: onProfile,
            ),
          ],
        ),
      ),
      const SizedBox(height: AppSpace.xxl),
      _SplitRow(
        leftFlex: 1,
        rightFlex: 1,
        left: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            SectionTitle(
              title: 'Tus cuentas',
              trailing: IconButton(
                tooltip: 'Actualizar cuentas',
                onPressed: onReload,
                icon: const Icon(Icons.refresh_rounded),
              ),
            ),
            _AccountsList(future: accounts, onRetry: onReload),
          ],
        ),
        right: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            SectionTitle(
              title: 'Servicios',
              trailing: TextButton(
                onPressed: () => onPay(),
                child: const Text('Ver catálogo'),
              ),
            ),
            _ServiceShortcuts(
              future: products,
              onRetry: onReload,
              onSelected: (service) => onPay(service: service),
            ),
          ],
        ),
      ),
    ],
  );
}

/// Tarjeta principal del cliente: saldo total disponible para pagar.
class _BalanceHero extends StatelessWidget {
  const _BalanceHero({
    required this.future,
    required this.onPay,
    required this.onRetry,
  });

  final Future<List<Map<String, dynamic>>> future;
  final void Function({String? service}) onPay;
  final Future<void> Function() onRetry;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return InkPanel(
      child: FutureBuilder<List<Map<String, dynamic>>>(
        future: future,
        builder: (context, snapshot) {
          final loading = snapshot.connectionState == ConnectionState.waiting;
          final accounts = snapshot.data ?? const [];
          final String amount;
          final String detail;
          if (loading) {
            amount = '—';
            detail = 'Consultando tus cuentas…';
          } else if (snapshot.hasError) {
            amount = '—';
            detail = _friendlyError(snapshot.error);
          } else if (accounts.isEmpty) {
            amount = MoneyFormat.mxn(0);
            detail = 'No tienes cuentas activas para pagar.';
          } else {
            amount = MoneyFormat.sumMxn(
              accounts.map((account) => account['saldoDisponible']),
            );
            detail = accounts.length == 1
                ? 'En 1 cuenta activa'
                : 'En ${accounts.length} cuentas activas';
          }
          return Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                'SALDO DISPONIBLE PARA PAGOS',
                style: textTheme.labelMedium?.copyWith(color: onInkMuted),
              ),
              const SizedBox(height: AppSpace.md),
              Semantics(
                liveRegion: true,
                label: loading ? 'Cargando saldo' : 'Saldo disponible $amount',
                child: ExcludeSemantics(
                  child: FittedBox(
                    fit: BoxFit.scaleDown,
                    alignment: Alignment.centerLeft,
                    child: Text(
                      amount,
                      style: textTheme.displaySmall?.copyWith(
                        color: warmSurface,
                      ),
                    ),
                  ),
                ),
              ),
              const SizedBox(height: AppSpace.sm),
              Text(
                detail,
                style: textTheme.bodyMedium?.copyWith(color: onInkMuted),
              ),
              const SizedBox(height: AppSpace.xxl),
              Wrap(
                spacing: AppSpace.md,
                runSpacing: AppSpace.md,
                crossAxisAlignment: WrapCrossAlignment.center,
                children: [
                  FilledButton.icon(
                    style: _onInkButton(),
                    onPressed: () => onPay(),
                    icon: const Icon(Icons.receipt_long_rounded),
                    label: const Text('Pagar un servicio'),
                  ),
                  if (snapshot.hasError)
                    TextButton(
                      style: TextButton.styleFrom(
                        foregroundColor: terracottaOnInk,
                      ),
                      onPressed: onRetry,
                      child: const Text('Reintentar'),
                    ),
                ],
              ),
            ],
          );
        },
      ),
    );
  }
}

/// Mensaje para el usuario a partir de un error de carga del inicio.
String _friendlyError(Object? error) => error is HomeDataException
    ? error.message
    : 'No fue posible cargar la información. Inténtalo de nuevo.';

class _AccountsList extends StatelessWidget {
  const _AccountsList({required this.future, required this.onRetry});

  final Future<List<Map<String, dynamic>>> future;
  final Future<void> Function() onRetry;

  @override
  Widget build(
    BuildContext context,
  ) => FutureBuilder<List<Map<String, dynamic>>>(
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
          message:
              'Aún no tienes cuentas activas. Un ejecutivo puede activarlas por ti.',
        );
      }
      return AppCard(
        padding: const EdgeInsets.symmetric(
          horizontal: AppSpace.lg + 2,
          vertical: AppSpace.xs,
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
          const IconBadge(
            icon: Icons.account_balance_wallet_outlined,
            size: 42,
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
            child: FittedBox(
              fit: BoxFit.scaleDown,
              alignment: Alignment.centerRight,
              child: Text(
                MoneyFormat.mxn(available),
                maxLines: 1,
                style: Theme.of(context).textTheme.titleMedium,
              ),
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
              'El catálogo no tiene servicios disponibles en este momento. Vuelve a intentarlo más tarde.',
        );
      }
      final textTheme = Theme.of(context).textTheme;
      // Teléfono: ícono de línea y flecha, como el diseño móvil.
      final phone = MediaQuery.sizeOf(context).width < Breakpoints.medium;
      return ResponsiveGrid(
        minItemWidth: 200,
        maxColumns: 2,
        spacing: AppSpace.md,
        children: [
          for (final entry in groups.entries.take(6))
            Padding(
              padding: const EdgeInsets.only(bottom: AppSpace.md),
              child: AppCard(
                onTap: () => onSelected(entry.key),
                semanticLabel:
                    'Pagar ${entry.key}, ${entry.value} ${entry.value == 1 ? 'opción' : 'opciones'}',
                padding: EdgeInsets.symmetric(
                  horizontal: phone ? AppSpace.xl : AppSpace.lg,
                  vertical: phone ? AppSpace.lg + 2 : AppSpace.lg,
                ),
                child: ExcludeSemantics(
                  child: Row(
                    children: [
                      if (phone)
                        Icon(_serviceIcon(entry.key), size: 28, color: ink)
                      else
                        IconBadge(
                          icon: _serviceIcon(entry.key),
                          size: 40,
                          background: terracottaSoft,
                          foreground: terracotta,
                        ),
                      SizedBox(width: phone ? AppSpace.xl : AppSpace.md),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              entry.key,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: textTheme.titleMedium,
                            ),
                            Text(
                              '${entry.value} ${entry.value == 1 ? 'opción' : 'opciones'}',
                              style: textTheme.bodySmall,
                            ),
                          ],
                        ),
                      ),
                      if (phone)
                        const Icon(Icons.chevron_right_rounded, color: ink),
                    ],
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
  if (value.contains('luz') || value.contains('energ') || value == 'cfe') {
    return Icons.bolt_outlined;
  }
  if (value.contains('agua')) return Icons.water_drop_outlined;
  if (value.contains('gas')) return Icons.local_fire_department_outlined;
  if (value.contains('internet') || value.contains('cable')) {
    return Icons.wifi_rounded;
  }
  if (value.contains('tel') ||
      value.contains('celular') ||
      value.contains('recarga')) {
    return Icons.phone_iphone_rounded;
  }
  if (value.contains('tarjeta') || value.contains('regalo')) {
    return Icons.card_giftcard_outlined;
  }
  return Icons.receipt_long_outlined;
}

// ───────────────────────────── Ejecutivo ─────────────────────────────

class _ExecutiveDashboard extends StatelessWidget {
  const _ExecutiveDashboard({
    required this.onRegister,
    required this.onSection,
  });

  final Future<void> Function() onRegister;
  final void Function(ManagementSection section) onSection;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _SplitRow(
          equalHeight: true,
          left: InkPanel(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'ALTA DE CLIENTES',
                  style: textTheme.labelMedium?.copyWith(color: onInkMuted),
                ),
                const SizedBox(height: AppSpace.md),
                Text(
                  'Da de alta a un cliente en tres pasos.',
                  style: textTheme.headlineMedium?.copyWith(color: warmSurface),
                ),
                const SizedBox(height: AppSpace.md),
                Text(
                  'Se crean su acceso y su cuenta interna en una sola operación. '
                  'Su correo será su usuario.',
                  style: textTheme.bodyLarge?.copyWith(color: onInkMuted),
                ),
                const SizedBox(height: AppSpace.xxl),
                FilledButton.icon(
                  style: _onInkButton(),
                  onPressed: onRegister,
                  icon: const Icon(Icons.person_add_alt_1_rounded),
                  label: const Text('Registrar cliente'),
                ),
              ],
            ),
          ),
          right: const AppCard(child: _RegistrationSteps()),
        ),
        const SizedBox(height: AppSpace.xxl),
        const SectionTitle(title: 'Atajos de gestión'),
        ResponsiveGrid(
          minItemWidth: 320,
          maxColumns: 2,
          spacing: AppSpace.lg,
          children: [
            for (final section in ManagementSection.values)
              Padding(
                padding: const EdgeInsets.only(bottom: AppSpace.lg),
                child: ActionCard(
                  icon: section.icon,
                  title: section.title,
                  description: section.description,
                  onTap: () => onSection(section),
                ),
              ),
          ],
        ),
      ],
    );
  }
}

class _RegistrationSteps extends StatelessWidget {
  const _RegistrationSteps();

  static const _steps = [
    ('Datos personales', 'Nombre, CURP, RFC y fecha de nacimiento.'),
    ('Contacto y domicilio', 'Teléfono, correo y dirección.'),
    ('Trabajo y acceso', 'Ocupación, ingreso y contraseña inicial.'),
  ];

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('Cómo funciona el registro', style: textTheme.titleLarge),
        const SizedBox(height: AppSpace.lg),
        for (var index = 0; index < _steps.length; index++)
          Padding(
            padding: const EdgeInsets.only(bottom: AppSpace.lg),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                CircleAvatar(
                  radius: 14,
                  backgroundColor: terracottaSoft,
                  foregroundColor: terracotta,
                  child: Text(
                    '${index + 1}',
                    style: const TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.w800,
                    ),
                  ),
                ),
                const SizedBox(width: AppSpace.md),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(_steps[index].$1, style: textTheme.titleMedium),
                      Text(_steps[index].$2, style: textTheme.bodyMedium),
                    ],
                  ),
                ),
              ],
            ),
          ),
      ],
    );
  }
}
