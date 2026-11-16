import 'package:flutter/material.dart';
import '../theme.dart';
import 'paper_background.dart';
import 'status_message.dart';

class ManagementLayout extends StatelessWidget {
  const ManagementLayout({
    super.key,
    required this.title,
    required this.children,
    this.busy = false,
    this.error,
    this.maxWidth = ContentWidth.page,
  });
  final String title;
  final List<Widget> children;
  final bool busy;
  final String? error;

  /// Ancho máximo del contenido. Las pantallas con listados usan
  /// [ContentWidth.wide] para aprovechar escritorio y web.
  final double maxWidth;

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: Text(title)),
    body: PaperBackground(
      child: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(24, 12, 24, 32),
          child: Center(
            child: ConstrainedBox(
              constraints: BoxConstraints(maxWidth: maxWidth),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text(
                    title,
                    style: Theme.of(context).textTheme.headlineMedium,
                  ),
                  const SizedBox(height: 20),
                  if (busy)
                    const Padding(
                      padding: EdgeInsets.only(bottom: 16),
                      child: LinearProgressIndicator(
                        semanticsLabel: 'Cargando información',
                      ),
                    ),
                  if (error != null) ...[
                    StatusMessage(
                      message: error!,
                      icon: Icons.error_outline_rounded,
                      tone: StatusTone.error,
                    ),
                    const SizedBox(height: 16),
                  ],
                  ...children,
                ],
              ),
            ),
          ),
        ),
      ),
    ),
  );
}

class ManagementCard extends StatelessWidget {
  const ManagementCard({super.key, required this.children});
  final List<Widget> children;
  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(AppSpace.xl - 4),
    margin: const EdgeInsets.only(bottom: AppSpace.lg),
    decoration: BoxDecoration(
      color: warmSurface,
      border: Border.all(color: outlineColor),
      borderRadius: BorderRadius.circular(AppRadius.lg),
      boxShadow: AppShadow.card,
    ),
    child: Material(
      type: MaterialType.transparency,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: children,
      ),
    ),
  );
}

Future<bool> confirmManagementAction(
  BuildContext context,
  String title,
  String detail,
) async =>
    await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(title),
        content: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 420),
          child: Text(detail),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Volver'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(minimumSize: const Size(120, 48)),
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Confirmar'),
          ),
        ],
      ),
    ) ??
    false;
