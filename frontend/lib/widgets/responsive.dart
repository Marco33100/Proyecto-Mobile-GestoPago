import 'package:flutter/material.dart';

import '../theme.dart';

/// Acomoda [children] en columnas según el ancho disponible.
///
/// En teléfono queda una sola columna (igual que una lista); en pantallas
/// anchas reparte las tarjetas en dos o tres columnas de igual ancho.
class ResponsiveGrid extends StatelessWidget {
  const ResponsiveGrid({
    super.key,
    required this.children,
    this.minItemWidth = 320,
    this.maxColumns = 3,
    this.spacing = 14,
  });

  final List<Widget> children;
  final double minItemWidth;
  final int maxColumns;
  final double spacing;

  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, constraints) {
      final columns =
          ((constraints.maxWidth + spacing) / (minItemWidth + spacing))
              .floor()
              .clamp(1, maxColumns);
      if (columns == 1) {
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: children,
        );
      }
      final itemWidth =
          (constraints.maxWidth - spacing * (columns - 1)) / columns;
      return Wrap(
        spacing: spacing,
        children: [
          for (final child in children)
            SizedBox(width: itemWidth, child: child),
        ],
      );
    },
  );
}

/// Dos paneles lado a lado en pantallas anchas; apilados en teléfono.
///
/// El panel [secondary] (resumen, ayuda, pasos) va a la derecha con ancho fijo
/// o debajo del principal cuando no hay espacio. Con [secondaryFirst] se
/// coloca a la izquierda en pantallas anchas.
class TwoPane extends StatelessWidget {
  const TwoPane({
    super.key,
    required this.primary,
    required this.secondary,
    this.secondaryWidth = 320,
    this.secondaryFirst = false,
    this.showSecondaryWhenNarrow = true,
  });

  final Widget primary;
  final Widget secondary;
  final double secondaryWidth;
  final bool secondaryFirst;
  final bool showSecondaryWhenNarrow;

  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, constraints) {
      if (constraints.maxWidth < Breakpoints.expanded - 80) {
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            primary,
            if (showSecondaryWhenNarrow) ...[
              const SizedBox(height: 24),
              secondary,
            ],
          ],
        );
      }
      final side = SizedBox(width: secondaryWidth, child: secondary);
      return Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (secondaryFirst) ...[side, const SizedBox(width: 32)],
          Expanded(child: primary),
          if (!secondaryFirst) ...[const SizedBox(width: 32), side],
        ],
      );
    },
  );
}

/// Controles de paginación con indicador de la página actual.
class PaginationBar extends StatelessWidget {
  const PaginationBar({
    super.key,
    required this.page,
    required this.isLast,
    required this.busy,
    required this.onPage,
  });

  /// Página actual, iniciando en cero.
  final int page;
  final bool isLast;
  final bool busy;
  final ValueChanged<int> onPage;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(top: 4),
    child: Row(
      children: [
        Expanded(
          child: OutlinedButton.icon(
            onPressed: busy || page == 0 ? null : () => onPage(page - 1),
            icon: const Icon(Icons.chevron_left_rounded),
            label: const Text('Anterior'),
          ),
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Text(
            'Página ${page + 1}',
            style: Theme.of(context).textTheme.labelLarge?.copyWith(color: ink),
          ),
        ),
        Expanded(
          child: OutlinedButton.icon(
            onPressed: busy || isLast ? null : () => onPage(page + 1),
            icon: const Icon(Icons.chevron_right_rounded),
            iconAlignment: IconAlignment.end,
            label: const Text('Siguiente'),
          ),
        ),
      ],
    ),
  );
}
