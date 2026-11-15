import 'package:flutter/material.dart';

import '../theme.dart';

/// The project has no approved logo asset; keep the brand name as typography.
class BrandWordmark extends StatelessWidget {
  const BrandWordmark({super.key, this.centered = false, this.compact = false});

  final bool centered;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    final wordmark = Column(
      crossAxisAlignment: centered
          ? CrossAxisAlignment.center
          : CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(
          'Gestopago',
          style: TextStyle(
            color: ink,
            fontSize: compact ? 20 : 25,
            height: 1.05,
            fontWeight: FontWeight.w800,
            letterSpacing: compact ? -0.4 : -0.7,
          ),
        ),
        const SizedBox(height: 7),
        Container(
          width: compact ? 30 : 42,
          height: 3,
          decoration: BoxDecoration(
            color: terracotta,
            borderRadius: BorderRadius.circular(3),
          ),
        ),
      ],
    );

    return centered ? Center(child: wordmark) : wordmark;
  }
}
