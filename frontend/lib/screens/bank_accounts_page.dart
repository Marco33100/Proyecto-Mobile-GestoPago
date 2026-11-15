import 'package:flutter/material.dart';
import '../services/admin_api_service.dart';
import '../services/payment_api_service.dart';
import '../utils/money_format.dart';
import '../widgets/management_layout.dart';

class BankAccountsPage extends StatefulWidget {
  const BankAccountsPage({super.key, required this.api});
  final AdminApiService api;
  @override
  State<BankAccountsPage> createState() => _BankAccountsPageState();
}

class _BankAccountsPageState extends State<BankAccountsPage> {
  final _number = TextEditingController();
  final _form = GlobalKey<FormState>();
  AdminPageResult? _page;
  Map<String, dynamic>? _account;
  Map<String, dynamic>? _balance;
  bool _busy = false;
  String? _error;
  @override
  void initState() {
    super.initState();
    _load(0);
  }

  @override
  void dispose() {
    _number.dispose();
    super.dispose();
  }

  Future<void> _run(Future<void> Function() action) async {
    if (_busy) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await action();
    } catch (e) {
      if (mounted) {
        setState(
          () => _error = e is PaymentFailure
              ? e.message
              : 'No se pudo consultar la cuenta.',
        );
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _load(int index) => _run(() async {
    final page = await widget.api.search('cuentas', {}, index);
    if (mounted) setState(() => _page = page);
  });

  Future<void> _lookup([String? number]) async {
    if (_busy) return;
    if (number == null && !_form.currentState!.validate()) return;
    final selected = (number ?? _number.text).trim().toUpperCase();
    _number.text = selected;
    setState(() {
      _account = null;
      _balance = null;
    });
    await _run(() async {
      final account = await widget.api.account(selected);
      if (mounted) setState(() => _account = account);
      final balance = await widget.api.balance(selected);
      if (mounted) setState(() => _balance = balance);
    });
  }

  Future<void> _refreshBalance() async {
    if (_busy) return;
    setState(() => _balance = null);
    await _run(() async {
      final balance = await widget.api.balance(
        _account!['numeroCuenta'] as String,
      );
      if (mounted) setState(() => _balance = balance);
    });
  }

  @override
  Widget build(BuildContext context) => ManagementLayout(
    title: 'Cuentas bancarias',
    busy: _busy,
    error: _error,
    children: [
      const Text(
        'Consulta una cuenta por su número o selecciona una cuenta activa del listado.',
      ),
      const SizedBox(height: 18),
      Form(
        key: _form,
        child: Column(
          children: [
            TextFormField(
              key: const ValueKey('account-number'),
              controller: _number,
              enabled: !_busy,
              maxLength: 24,
              textCapitalization: TextCapitalization.characters,
              decoration: const InputDecoration(
                labelText: 'Número de cuenta',
                hintText: 'CTA-…',
              ),
              validator: (v) =>
                  RegExp(
                    r'^CTA-[0-9A-F]{20}$',
                    caseSensitive: false,
                  ).hasMatch(v?.trim() ?? '')
                  ? null
                  : 'Escribe CTA- y los 20 caracteres de la cuenta',
            ),
            FilledButton.icon(
              onPressed: _busy ? null : () => _lookup(),
              icon: const Icon(Icons.search),
              label: const Text('Consultar cuenta'),
            ),
          ],
        ),
      ),
      const SizedBox(height: 22),
      if (_account case final account?)
        ManagementCard(
          children: [
            Text(
              'Cuenta consultada',
              style: Theme.of(context).textTheme.titleLarge,
            ),
            SelectableText('${account['numeroCuenta']}'),
            Text(account['activa'] == true ? 'Activa' : 'Inactiva'),
            const SizedBox(height: 12),
            if (_balance case final balance?) ...[
              Text('Saldo: ${MoneyFormat.mxn(balance['saldo'])}'),
              Text(
                'Disponible: ${MoneyFormat.mxn(balance['saldoDisponible'])}',
              ),
              Text('Retenido: ${MoneyFormat.mxn(balance['saldoRetenido'])}'),
            ] else
              const Text(
                'No se pudo actualizar el saldo. Vuelve a consultarlo.',
              ),
            TextButton.icon(
              onPressed: _busy ? null : _refreshBalance,
              icon: const Icon(Icons.refresh),
              label: const Text('Actualizar saldo'),
            ),
          ],
        ),
      Row(
        children: [
          Expanded(
            child: Text(
              'Cuentas activas',
              style: Theme.of(context).textTheme.titleLarge,
            ),
          ),
          IconButton(
            tooltip: 'Actualizar cuentas',
            onPressed: _busy ? null : () => _load(_page?.page ?? 0),
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      if (_page case final page?) ...[
        Text('${page.total} cuentas · Página ${page.page + 1}'),
        const SizedBox(height: 14),
        if (page.items.isEmpty)
          const Text('No se encontraron cuentas activas.'),
        for (final item in page.items)
          ManagementCard(
            children: [
              SelectableText('${item['numeroCuenta']}'),
              Text('Disponible: ${MoneyFormat.mxn(item['saldoDisponible'])}'),
              TextButton(
                onPressed: _busy
                    ? null
                    : () => _lookup(item['numeroCuenta'] as String),
                child: const Text('Ver cuenta y saldo'),
              ),
            ],
          ),
        Row(
          children: [
            Expanded(
              child: OutlinedButton(
                onPressed: _busy || page.page == 0
                    ? null
                    : () => _load(page.page - 1),
                child: const Text('Anterior'),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: OutlinedButton(
                onPressed: _busy || page.last
                    ? null
                    : () => _load(page.page + 1),
                child: const Text('Siguiente'),
              ),
            ),
          ],
        ),
      ],
    ],
  );
}
