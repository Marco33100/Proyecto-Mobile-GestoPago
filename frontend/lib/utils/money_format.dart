class MoneyFormat {
  const MoneyFormat._();

  static String mxn(Object? value) {
    final cents = _cents(value);
    if (cents == null) return 'No disponible';
    return _formatCents(cents);
  }

  static String? totalMxn(Object? amount, Object? commission) {
    final amountCents = _cents(amount);
    final commissionCents = _cents(commission);
    if (amountCents == null || commissionCents == null) return null;
    return _formatCents(amountCents + commissionCents);
  }

  static BigInt? _cents(Object? value) {
    if (value == null) return null;
    final raw = value.toString().trim();
    final match = RegExp(r'^([+-]?)(\d+)(?:\.(\d+))?$').firstMatch(raw);
    if (match == null) return null;
    final sign = match.group(1) == '-' ? -BigInt.one : BigInt.one;
    final whole = BigInt.tryParse(match.group(2)!);
    if (whole == null) return null;
    final fraction = (match.group(3) ?? '').padRight(2, '0');
    final fractionCents = BigInt.tryParse(fraction.substring(0, 2));
    if (fractionCents == null) return null;
    return sign * (whole * BigInt.from(100) + fractionCents);
  }

  static String _formatCents(BigInt cents) {
    final absolute = cents.abs();
    final whole = (absolute ~/ BigInt.from(100)).toString();
    final grouped = whole.replaceAllMapped(
      RegExp(r'\B(?=(\d{3})+(?!\d))'),
      (_) => ',',
    );
    final fraction = (absolute % BigInt.from(100)).toString().padLeft(2, '0');
    final sign = cents.isNegative ? '-' : '';
    return '$sign\$$grouped.$fraction MXN';
  }
}
