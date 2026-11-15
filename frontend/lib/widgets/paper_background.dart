import 'dart:math' as math;
import 'dart:ui' show PointMode;

import 'package:flutter/material.dart';

import '../theme.dart';

class PaperBackground extends StatelessWidget {
  const PaperBackground({super.key, required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) => DecoratedBox(
    decoration: const BoxDecoration(
      gradient: LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [Color(0xFFFBF6EC), paper],
      ),
    ),
    child: Stack(
      fit: StackFit.expand,
      children: [
        const IgnorePointer(
          child: CustomPaint(painter: _PaperTexturePainter()),
        ),
        child,
      ],
    ),
  );
}

class _PaperTexturePainter extends CustomPainter {
  const _PaperTexturePainter();

  @override
  void paint(Canvas canvas, Size size) {
    if (size.isEmpty) return;
    final random = math.Random(17);
    final flecks = List<Offset>.generate(
      420,
      (_) => Offset(
        random.nextDouble() * size.width,
        random.nextDouble() * size.height,
      ),
    );
    canvas.drawPoints(
      PointMode.points,
      flecks,
      Paint()
        ..color = const Color(0xFF79694F).withValues(alpha: 0.045)
        ..strokeWidth = 0.7
        ..strokeCap = StrokeCap.round,
    );
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
