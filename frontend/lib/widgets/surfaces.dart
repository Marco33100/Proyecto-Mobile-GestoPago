import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../theme.dart';

/// Tarjeta base del sistema: superficie cálida, borde suave y sombra difusa.
///
/// Con [onTap] se vuelve interactiva: se eleva con el puntero encima (solo
/// mouse; en touch Flutter no emite hover), se comprime al presionar y muestra
/// el anillo de foco al navegar con teclado.
class AppCard extends StatefulWidget {
  const AppCard({
    super.key,
    required this.child,
    this.onTap,
    this.padding = const EdgeInsets.all(AppSpace.xl),
    this.semanticLabel,
  });

  final Widget child;
  final VoidCallback? onTap;
  final EdgeInsetsGeometry padding;
  final String? semanticLabel;

  @override
  State<AppCard> createState() => _AppCardState();
}

class _AppCardState extends State<AppCard> {
  bool _hovered = false;
  bool _pressed = false;
  bool _focused = false;

  @override
  Widget build(BuildContext context) {
    final interactive = widget.onTap != null;
    final radius = BorderRadius.circular(AppRadius.lg);
    final lifted = interactive && (_hovered || _focused);
    final card = AnimatedContainer(
      duration: AppMotion.of(context, AppMotion.hover),
      curve: AppMotion.curve,
      decoration: BoxDecoration(
        color: warmSurface,
        borderRadius: radius,
        border: Border.all(
          color: _focused ? ink : outlineColor,
          width: _focused ? 2 : 1,
        ),
        boxShadow: lifted ? AppShadow.raised : AppShadow.card,
      ),
      child: Material(
        type: MaterialType.transparency,
        child: interactive
            ? InkWell(
                borderRadius: radius,
                onTap: widget.onTap,
                onHover: (value) => setState(() => _hovered = value),
                onFocusChange: (value) => setState(() => _focused = value),
                onHighlightChanged: (value) => setState(() => _pressed = value),
                child: Padding(padding: widget.padding, child: widget.child),
              )
            : Padding(padding: widget.padding, child: widget.child),
      ),
    );
    if (!interactive) return card;
    return Semantics(
      button: true,
      label: widget.semanticLabel,
      child: AnimatedScale(
        scale: _pressed ? 0.98 : 1,
        duration: AppMotion.of(context, AppMotion.press),
        curve: AppMotion.curve,
        child: card,
      ),
    );
  }
}

/// Ícono dentro de un cuadro tintado; da peso visual a tarjetas y listas.
class IconBadge extends StatelessWidget {
  const IconBadge({
    super.key,
    required this.icon,
    this.size = 48,
    this.background = sage,
    this.foreground = ink,
  });

  final IconData icon;
  final double size;
  final Color background;
  final Color foreground;

  @override
  Widget build(BuildContext context) => Container(
    width: size,
    height: size,
    decoration: BoxDecoration(
      color: background,
      borderRadius: BorderRadius.circular(AppRadius.sm),
    ),
    child: Icon(icon, color: foreground, size: size * 0.5),
  );
}

/// Tarjeta de acción: ícono, título, descripción y flecha.
class ActionCard extends StatelessWidget {
  const ActionCard({
    super.key,
    required this.icon,
    required this.title,
    required this.description,
    required this.onTap,
    this.accent = false,
  });

  final IconData icon;
  final String title;
  final String description;
  final VoidCallback onTap;

  /// Usa el tono terracota para la acción principal de un grupo.
  final bool accent;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return AppCard(
      onTap: onTap,
      padding: const EdgeInsets.all(AppSpace.lg + 2),
      child: Row(
        children: [
          IconBadge(
            icon: icon,
            background: accent ? terracottaSoft : sage,
            foreground: accent ? terracotta : ink,
          ),
          const SizedBox(width: AppSpace.lg),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: textTheme.titleMedium),
                const SizedBox(height: 2),
                Text(description, style: textTheme.bodyMedium),
              ],
            ),
          ),
          const SizedBox(width: AppSpace.sm),
          const ExcludeSemantics(
            child: Icon(Icons.arrow_forward_rounded, color: mutedInk),
          ),
        ],
      ),
    );
  }
}

/// Panel oscuro de marca: degradado de `ink` con anillos decorativos.
class InkPanel extends StatelessWidget {
  const InkPanel({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(AppSpace.xxl),
    this.radius = AppRadius.lg,
  });

  final Widget child;
  final EdgeInsetsGeometry padding;
  final double radius;

  @override
  Widget build(BuildContext context) => ClipRRect(
    borderRadius: BorderRadius.circular(radius),
    child: DecoratedBox(
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [inkRaised, ink, inkDeep],
        ),
      ),
      child: Stack(
        children: [
          const Positioned.fill(
            child: IgnorePointer(child: CustomPaint(painter: _RingsPainter())),
          ),
          Padding(
            padding: padding,
            child: DefaultTextStyle.merge(
              style: const TextStyle(color: warmSurface),
              child: IconTheme.merge(
                data: const IconThemeData(color: warmSurface),
                child: child,
              ),
            ),
          ),
        ],
      ),
    ),
  );
}

class _RingsPainter extends CustomPainter {
  const _RingsPainter();

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width * 0.95, size.height * 0.05);
    final base = math.max(size.width, size.height);
    final paint = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.2;
    for (var i = 1; i <= 4; i++) {
      paint.color = warmSurface.withValues(alpha: 0.07 - i * 0.012);
      canvas.drawCircle(center, base * 0.18 * i, paint);
    }
    canvas.drawCircle(
      Offset(size.width * 0.05, size.height * 1.05),
      base * 0.22,
      Paint()..color = terracottaOnInk.withValues(alpha: 0.10),
    );
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

/// Encabezado de sección/página: antetítulo, título, descripción y acciones.
class PageHeader extends StatelessWidget {
  const PageHeader({
    super.key,
    required this.title,
    this.eyebrow,
    this.description,
    this.trailing,
    this.accent = false,
  });

  final String title;
  final String? eyebrow;
  final String? description;
  final Widget? trailing;

  /// Subrayado terracota bajo el título.
  final bool accent;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return Row(
      crossAxisAlignment: CrossAxisAlignment.end,
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              if (eyebrow != null) ...[
                Text(eyebrow!, style: textTheme.bodyLarge),
                const SizedBox(height: AppSpace.xs),
              ],
              Semantics(
                header: true,
                child: Text(title, style: textTheme.headlineLarge),
              ),
              if (accent) ...[
                const SizedBox(height: AppSpace.lg),
                Container(
                  width: 56,
                  height: 3,
                  decoration: BoxDecoration(
                    color: terracotta,
                    borderRadius: BorderRadius.circular(3),
                  ),
                ),
              ],
              if (description != null) ...[
                const SizedBox(height: AppSpace.sm),
                Text(description!, style: textTheme.bodyLarge),
              ],
            ],
          ),
        ),
        if (trailing != null) ...[
          const SizedBox(width: AppSpace.lg),
          trailing!,
        ],
      ],
    );
  }
}

/// Título de sección con acción opcional a la derecha.
class SectionTitle extends StatelessWidget {
  const SectionTitle({super.key, required this.title, this.trailing});

  final String title;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: AppSpace.md),
    child: Row(
      children: [
        Expanded(
          child: Semantics(
            header: true,
            child: Text(title, style: Theme.of(context).textTheme.titleLarge),
          ),
        ),
        ?trailing,
      ],
    ),
  );
}

/// Tarjeta grande de sección: ícono destacado arriba, título, descripción y
/// una acción "Abrir" al pie. Pensada para llenar una cuadrícula en web.
class FeatureTile extends StatelessWidget {
  const FeatureTile({
    super.key,
    required this.icon,
    required this.title,
    required this.description,
    required this.onTap,
    this.actionLabel = 'Abrir',
    this.accent = false,
  });

  final IconData icon;
  final String title;
  final String description;
  final VoidCallback onTap;
  final String actionLabel;
  final bool accent;

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    return AppCard(
      onTap: onTap,
      semanticLabel: '$title. $description',
      padding: const EdgeInsets.all(AppSpace.xxl - 4),
      child: ExcludeSemantics(
        child: ConstrainedBox(
          constraints: const BoxConstraints(minHeight: 196),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              IconBadge(
                icon: icon,
                size: 64,
                background: accent ? terracottaSoft : sage,
                foreground: accent ? terracotta : ink,
              ),
              const SizedBox(height: AppSpace.xl),
              Text(title, style: textTheme.headlineSmall),
              const SizedBox(height: AppSpace.sm),
              Text(description, style: textTheme.bodyLarge),
              const SizedBox(height: AppSpace.xl),
              Row(
                children: [
                  Text(
                    actionLabel,
                    style: textTheme.labelLarge?.copyWith(color: terracotta),
                  ),
                  const SizedBox(width: AppSpace.xs),
                  const Icon(
                    Icons.arrow_forward_rounded,
                    size: 18,
                    color: terracotta,
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Cuadros redondeados girados (salvia y terracota) que asoman por una
/// esquina. Decoración de las pantallas de teléfono.
class CornerDiamonds extends StatelessWidget {
  const CornerDiamonds({super.key, this.size = 260});

  final double size;

  @override
  Widget build(BuildContext context) => IgnorePointer(
    child: ExcludeSemantics(
      child: SizedBox.square(
        dimension: size,
        child: const CustomPaint(painter: _CornerDiamondsPainter()),
      ),
    ),
  );
}

class _CornerDiamondsPainter extends CustomPainter {
  const _CornerDiamondsPainter();

  @override
  void paint(Canvas canvas, Size size) {
    final s = size.shortestSide;
    void diamond(Offset center, double side, Color color) {
      canvas
        ..save()
        ..translate(center.dx, center.dy)
        ..rotate(math.pi / 4)
        ..drawRRect(
          RRect.fromRectAndRadius(
            Rect.fromCenter(center: Offset.zero, width: side, height: side),
            Radius.circular(side * 0.2),
          ),
          Paint()
            ..style = PaintingStyle.stroke
            ..strokeWidth = s * 0.11
            ..strokeCap = StrokeCap.round
            ..color = color,
        )
        ..restore();
    }

    diamond(
      Offset(s * 0.55, s * 0.92),
      s * 0.62,
      const Color(0xFFB9BCA6).withValues(alpha: 0.75),
    );
    diamond(
      Offset(s * 0.95, s * 0.6),
      s * 0.62,
      const Color(0xFFD08A60).withValues(alpha: 0.85),
    );
    diamond(
      Offset(s * 1.05, s * 1.12),
      s * 0.5,
      const Color(0xFFB9BCA6).withValues(alpha: 0.6),
    );
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

/// Arcos en perspectiva con luz cálida: decoración del encabezado del inicio
/// en teléfono.
class ArchesDecoration extends StatelessWidget {
  const ArchesDecoration({super.key, this.width = 190, this.height = 230});

  final double width;
  final double height;

  @override
  Widget build(BuildContext context) => IgnorePointer(
    child: ExcludeSemantics(
      child: SizedBox(
        width: width,
        height: height,
        child: const CustomPaint(painter: _ArchesPainter()),
      ),
    ),
  );
}

class _ArchesPainter extends CustomPainter {
  const _ArchesPainter();

  @override
  void paint(Canvas canvas, Size size) {
    final w = size.width;
    final h = size.height;
    // Muro con degradado cálido que se desvanece hacia la izquierda.
    final wall = Path()
      ..moveTo(w * 0.18, h * 0.32)
      ..quadraticBezierTo(w * 0.18, h * 0.06, w * 0.48, h * 0.04)
      ..lineTo(w, h * 0.0)
      ..lineTo(w, h * 0.86)
      ..lineTo(w * 0.18, h * 0.92)
      ..close();
    canvas.drawPath(
      wall,
      Paint()
        ..shader = const LinearGradient(
          begin: Alignment.centerLeft,
          end: Alignment.centerRight,
          colors: [Color(0x00E8DCC6), Color(0xFFE8DCC6), Color(0xFFE2D3BA)],
          stops: [0, 0.35, 1],
        ).createShader(Offset.zero & size),
    );
    // Tres arcos que se alejan (más pequeños hacia la derecha).
    const openings = [
      (0.30, 0.30, 0.20, 0.86),
      (0.56, 0.36, 0.16, 0.84),
      (0.78, 0.41, 0.13, 0.82),
    ];
    for (final (x, top, width, bottom) in openings) {
      final left = w * x;
      final right = left + w * width;
      final radius = (right - left) / 2;
      final arch = Path()
        ..moveTo(left, h * bottom)
        ..lineTo(left, h * top + radius)
        ..arcToPoint(
          Offset(right, h * top + radius),
          radius: Radius.circular(radius),
        )
        ..lineTo(right, h * bottom)
        ..close();
      canvas.drawPath(
        arch,
        Paint()
          ..shader = const LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [Color(0xFFCDBB9C), Color(0xFFF1E7D6)],
          ).createShader(arch.getBounds()),
      );
    }
    // Hojas: un ramo salvia/verde en la esquina superior.
    final leaf = Paint()
      ..color = const Color(0xFF5E7A52).withValues(alpha: 0.85);
    final stem = Paint()
      ..color = const Color(0xFF5E7A52)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.4;
    canvas.drawLine(
      Offset(w * 1.02, h * 0.02),
      Offset(w * 0.55, h * 0.3),
      stem,
    );
    for (var i = 0; i < 6; i++) {
      final t = i / 6;
      final cx = w * (1.0 - 0.42 * t);
      final cy = h * (0.03 + 0.25 * t);
      for (final side in [-1, 1]) {
        canvas
          ..save()
          ..translate(cx, cy)
          ..rotate(side * 0.9 + 0.5)
          ..drawOval(
            Rect.fromCenter(
              center: Offset(0, side * 9.0),
              width: 7,
              height: 18,
            ),
            leaf,
          )
          ..restore();
      }
    }
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}

/// Iniciales para el avatar del usuario.
String initialsOf(String fullName) {
  final parts = fullName
      .trim()
      .split(RegExp(r'\s+'))
      .where((part) => part.isNotEmpty)
      .toList();
  if (parts.isEmpty) return '?';
  final first = parts.first[0];
  final last = parts.length > 1 ? parts.last[0] : '';
  return (first + last).toUpperCase();
}
