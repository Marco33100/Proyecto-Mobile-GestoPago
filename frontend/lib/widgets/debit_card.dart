import 'package:flutter/material.dart';

import '../theme.dart';
import '../utils/money_format.dart';

/// Cuenta como tarjeta de débito; el número completo solo se ve a petición.
class DebitCard extends StatefulWidget {
  const DebitCard({super.key, required this.account});

  final Map<String, dynamic> account;

  @override
  State<DebitCard> createState() => _DebitCardState();
}

class _DebitCardState extends State<DebitCard> {
  static const _cardDeep = Color(0xFF6E3519);
  static const _chip = Color(0xFFE3C68F);
  bool _revealed = false;

  /// "CTA-0123456789ABCDEF0123" -> grupos de 4; oculto deja solo los últimos 4.
  static String _format(String number, {required bool revealed}) {
    final prefix = number.startsWith('CTA-') ? 'CTA' : '';
    final body = prefix.isEmpty ? number : number.substring(4);
    final groups = <String>[
      for (var i = 0; i < body.length; i += 4)
        body.substring(i, i + 4 > body.length ? body.length : i + 4),
    ];
    final shown = [
      for (var i = 0; i < groups.length; i++)
        revealed || i == groups.length - 1 ? groups[i] : '••••',
    ];
    return [if (prefix.isNotEmpty) prefix, ...shown].join(' ');
  }

  @override
  Widget build(BuildContext context) {
    final textTheme = Theme.of(context).textTheme;
    final number = widget.account['numeroCuenta']?.toString() ?? '';
    final last4 = number.length > 4
        ? number.substring(number.length - 4)
        : number;
    final amount = MoneyFormat.mxn(widget.account['saldoDisponible']);
    // Lo apartado por un pago pendiente ya no está disponible hasta saberse el resultado.
    final held = num.tryParse('${widget.account['saldoRetenido'] ?? 0}') ?? 0;
    const onCard = warmSurface;
    final onCardMuted = warmSurface.withValues(alpha: 0.78);
    const digits = [FontFeature.tabularFigures()];
    return Semantics(
      container: true,
      label:
          'Cuenta terminación $last4, saldo disponible $amount'
          '${held > 0 ? ', ${MoneyFormat.mxn(held)} apartado por un pago pendiente' : ''}',
      child: AspectRatio(
        // Proporción de una tarjeta bancaria (ISO/IEC 7810 ID-1).
        aspectRatio: 1.586,
        // Se diseña a 420 px y se escala completa: en teléfono conserva aire.
        child: FittedBox(
          child: SizedBox(
            width: 420,
            height: 420 / 1.586,
            child: DecoratedBox(
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(AppRadius.lg),
                gradient: const LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [terracotta, _cardDeep],
                ),
                boxShadow: const [
                  BoxShadow(
                    color: Color(0x243A1A0C),
                    blurRadius: 32,
                    spreadRadius: -8,
                    offset: Offset(0, 14),
                  ),
                ],
              ),
              child: ClipRRect(
                borderRadius: BorderRadius.circular(AppRadius.lg),
                child: Stack(
                  children: [
                    // Arcos tenues, el mismo motivo del panel de saldo.
                    Positioned(
                      right: -70,
                      top: -90,
                      child: _Ring(
                        size: 240,
                        color: onCard.withValues(alpha: 0.08),
                      ),
                    ),
                    Positioned(
                      right: -20,
                      bottom: -120,
                      child: _Ring(
                        size: 220,
                        color: onCard.withValues(alpha: 0.06),
                      ),
                    ),
                    Padding(
                      padding: const EdgeInsets.fromLTRB(22, 18, 10, 18),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              Text(
                                'Gestopago',
                                style: textTheme.titleMedium?.copyWith(
                                  color: onCard,
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                              const Spacer(),
                              IconButton(
                                tooltip: _revealed
                                    ? 'Ocultar número de cuenta'
                                    : 'Mostrar número de cuenta',
                                onPressed: () =>
                                    setState(() => _revealed = !_revealed),
                                color: onCard,
                                icon: Icon(
                                  _revealed
                                      ? Icons.visibility_off_outlined
                                      : Icons.visibility_outlined,
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: AppSpace.xs),
                          Row(
                            children: [
                              Container(
                                width: 40,
                                height: 30,
                                decoration: BoxDecoration(
                                  color: _chip,
                                  borderRadius: BorderRadius.circular(6),
                                  border: Border.all(
                                    color: const Color(0xFFC9A766),
                                  ),
                                ),
                              ),
                              const SizedBox(width: AppSpace.md),
                              Icon(
                                Icons.contactless_outlined,
                                color: onCardMuted,
                              ),
                            ],
                          ),
                          const Spacer(),
                          FittedBox(
                            fit: BoxFit.scaleDown,
                            alignment: Alignment.centerLeft,
                            child: Text(
                              _format(number, revealed: _revealed),
                              semanticsLabel: _revealed
                                  ? 'Número de cuenta $number'
                                  : 'Número oculto, terminación $last4',
                              style: textTheme.titleLarge?.copyWith(
                                color: onCard,
                                letterSpacing: 1.6,
                                fontFeatures: digits,
                              ),
                            ),
                          ),
                          const Spacer(),
                          Padding(
                            padding: const EdgeInsets.only(right: 12),
                            child: Row(
                              crossAxisAlignment: CrossAxisAlignment.end,
                              children: [
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    children: [
                                      Text(
                                        'Saldo disponible',
                                        style: textTheme.labelMedium?.copyWith(
                                          color: onCardMuted,
                                        ),
                                      ),
                                      FittedBox(
                                        fit: BoxFit.scaleDown,
                                        alignment: Alignment.centerLeft,
                                        child: Text(
                                          amount,
                                          style: textTheme.titleLarge?.copyWith(
                                            color: onCard,
                                            fontWeight: FontWeight.w700,
                                            fontFeatures: digits,
                                          ),
                                        ),
                                      ),
                                      if (held > 0)
                                        Text(
                                          '${MoneyFormat.mxn(held)} apartado por un pago pendiente',
                                          maxLines: 1,
                                          overflow: TextOverflow.ellipsis,
                                          style: textTheme.labelMedium
                                              ?.copyWith(
                                                color: onCardMuted,
                                                fontFeatures: digits,
                                              ),
                                        ),
                                    ],
                                  ),
                                ),
                                Text(
                                  'Débito',
                                  style: textTheme.labelLarge?.copyWith(
                                    color: onCardMuted,
                                    letterSpacing: 0.8,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _Ring extends StatelessWidget {
  const _Ring({required this.size, required this.color});

  final double size;
  final Color color;

  @override
  Widget build(BuildContext context) => IgnorePointer(
    child: Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        border: Border.all(color: color, width: 26),
      ),
    ),
  );
}
