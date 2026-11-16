import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../theme.dart';

/// Marca de Gestopago: símbolo de dos cuadros entrelazados + nombre.
///
/// - [centered]: símbolo arriba y nombre debajo (login, carga).
/// - [compact]: símbolo a la izquierda del nombre (encabezados, barra lateral).
class BrandWordmark extends StatelessWidget {
  const BrandWordmark({
    super.key,
    this.centered = false,
    this.compact = false,
    this.onDark = false,
  });

  final bool centered;
  final bool compact;

  /// Versión clara para fondos `ink`.
  final bool onDark;

  @override
  Widget build(BuildContext context) {
    final color = onDark ? warmSurface : ink;
    final name = Text(
      'Gestopago',
      style: TextStyle(
        color: color,
        fontSize: compact ? 22 : 34,
        height: 1.05,
        fontWeight: FontWeight.w700,
        letterSpacing: compact ? -0.4 : -0.9,
      ),
    );
    final Widget wordmark = centered
        ? Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              BrandMark(size: compact ? 44 : 72, onDark: onDark),
              const SizedBox(height: AppSpace.md),
              name,
            ],
          )
        : Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              BrandMark(size: compact ? 30 : 40, onDark: onDark),
              const SizedBox(width: AppSpace.sm + 2),
              Flexible(
                child: FittedBox(fit: BoxFit.scaleDown, child: name),
              ),
            ],
          );
    return Semantics(
      label: 'Gestopago',
      excludeSemantics: true,
      child: centered ? Center(child: wordmark) : wordmark,
    );
  }
}

/// Símbolo: un cuadro verde y uno terracota, girados 45° y entrelazados.
class BrandMark extends StatelessWidget {
  const BrandMark({super.key, this.size = 40, this.onDark = false});

  final double size;
  final bool onDark;

  @override
  Widget build(BuildContext context) => SizedBox.square(
    dimension: size,
    child: CustomPaint(painter: _BrandMarkPainter(onDark: onDark)),
  );
}

class _BrandMarkPainter extends CustomPainter {
  const _BrandMarkPainter({required this.onDark});

  final bool onDark;

  @override
  void paint(Canvas canvas, Size size) {
    final s = size.shortestSide;
    final stroke = s * 0.15;
    final side = s * 0.46;
    void diamond(Offset center, Color color) {
      canvas
        ..save()
        ..translate(center.dx, center.dy)
        ..rotate(math.pi / 4)
        ..drawRRect(
          RRect.fromRectAndRadius(
            Rect.fromCenter(center: Offset.zero, width: side, height: side),
            Radius.circular(side * 0.22),
          ),
          Paint()
            ..style = PaintingStyle.stroke
            ..strokeWidth = stroke
            ..color = color,
        )
        ..restore();
    }

    diamond(Offset(s * 0.38, s * 0.42), onDark ? warmSurface : ink);
    diamond(Offset(s * 0.62, s * 0.58), onDark ? terracottaOnInk : terracotta);
  }

  @override
  bool shouldRepaint(covariant _BrandMarkPainter oldDelegate) =>
      oldDelegate.onDark != onDark;
}
