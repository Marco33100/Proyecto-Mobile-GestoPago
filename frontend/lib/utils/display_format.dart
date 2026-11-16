/// Formatos de presentación compartidos por el pago y el historial.
library;

/// Solo los últimos cuatro dígitos de la cuenta: `•••• 0001`.
/// `null` si no hay número.
String? maskAccount(Object? value) {
  final text = value?.toString().trim() ?? '';
  if (text.isEmpty) return null;
  return text.length <= 4 ? text : '•••• ${text.substring(text.length - 4)}';
}

/// Fecha ISO del servidor en hora local: `15/11/2026 10:32`.
/// `null` si no hay fecha o no se puede leer.
String? formatDateTime(Object? value) {
  final date = value is String ? DateTime.tryParse(value)?.toLocal() : null;
  if (date == null) return null;
  String two(int n) => n.toString().padLeft(2, '0');
  return '${two(date.day)}/${two(date.month)}/${date.year} '
      '${two(date.hour)}:${two(date.minute)}';
}
