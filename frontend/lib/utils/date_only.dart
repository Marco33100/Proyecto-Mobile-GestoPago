/// Fecha de calendario para el contrato LocalDate/DATE del backend.
abstract final class DateOnly {
  static final _pattern = RegExp(r'^[0-9]{4}-[0-9]{2}-[0-9]{2}$');

  /// Conserva el dia elegido: no convierte zonas ni serializa la hora.
  static String format(DateTime date) =>
      '${date.year.toString().padLeft(4, '0')}-'
      '${date.month.toString().padLeft(2, '0')}-'
      '${date.day.toString().padLeft(2, '0')}';

  static DateTime? tryParse(String text) {
    if (!_pattern.hasMatch(text)) return null;
    final date = DateTime.tryParse(text);
    // DateTime acepta fechas desbordadas: no convertir 30 de febrero a marzo.
    return date != null && format(date) == text ? date : null;
  }
}
