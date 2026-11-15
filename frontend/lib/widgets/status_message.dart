import 'package:flutter/material.dart';

import '../theme.dart';

/// Tono visual de un [StatusMessage].
enum StatusTone {
  /// Aviso informativo sobre fondo salvia.
  info,

  /// Error o rechazo que requiere atención.
  error,

  /// Estado neutro sobre superficie clara con borde (carga, vacío).
  neutral,
}

/// Mensaje de estado compartido: avisos, errores, estados vacíos y de carga.
///
/// Con [title] se muestra como bloque centrado (estado vacío); sin él, como
/// una fila compacta con icono y texto.
class StatusMessage extends StatelessWidget {
  const StatusMessage({
    super.key,
    required this.message,
    this.icon,
    this.tone = StatusTone.info,
    this.title,
    this.action,
    this.loading = false,
  });

  /// Indicador de carga en línea (por ejemplo, "Cargando cuentas").
  const StatusMessage.loading({super.key, required this.message})
    : icon = null,
      tone = StatusTone.neutral,
      title = null,
      action = null,
      loading = true;

  final String message;
  final IconData? icon;
  final StatusTone tone;
  final String? title;
  final Widget? action;
  final bool loading;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final (background, border, iconColor, textColor) = switch (tone) {
      StatusTone.info => (sage, null, ink, null),
      StatusTone.error => (
        errorSurface,
        const Color(0xFFD9B4A9),
        errorOnSurface,
        errorOnSurface,
      ),
      StatusTone.neutral => (warmSurface, outlineColor, terracotta, null),
    };
    final textStyle = theme.textTheme.bodyMedium?.copyWith(color: textColor);
    final leading = loading
        ? const SizedBox.square(
            dimension: 18,
            child: CircularProgressIndicator(strokeWidth: 2),
          )
        : icon == null
        ? null
        : Icon(icon, color: iconColor, size: title == null ? 22 : 40);

    final Widget content;
    if (title != null) {
      content = Column(
        children: [
          ?leading,
          const SizedBox(height: 12),
          Text(
            title!,
            textAlign: TextAlign.center,
            style: theme.textTheme.titleLarge,
          ),
          const SizedBox(height: 6),
          Text(message, textAlign: TextAlign.center, style: textStyle),
          if (action != null) ...[const SizedBox(height: 14), action!],
        ],
      );
    } else {
      content = Row(
        crossAxisAlignment: loading
            ? CrossAxisAlignment.center
            : CrossAxisAlignment.start,
        children: [
          if (leading != null) ...[leading, const SizedBox(width: 12)],
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(message, style: textStyle),
                if (action != null) ...[const SizedBox(height: 6), action!],
              ],
            ),
          ),
        ],
      );
    }

    return Semantics(
      liveRegion: tone == StatusTone.error,
      child: Container(
        width: double.infinity,
        padding: EdgeInsets.all(title == null ? 16 : 22),
        decoration: BoxDecoration(
          color: background,
          borderRadius: BorderRadius.circular(
            title == null ? AppRadius.md : AppRadius.lg,
          ),
          border: border == null ? null : Border.all(color: border),
        ),
        child: content,
      ),
    );
  }
}
