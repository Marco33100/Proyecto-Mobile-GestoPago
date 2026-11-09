import 'package:flutter/material.dart';
import '../theme.dart';
import 'brand_wordmark.dart';
import 'paper_background.dart';

class ManagementLayout extends StatelessWidget {
  const ManagementLayout({
    super.key,
    required this.title,
    required this.children,
    this.busy = false,
    this.error,
  });
  final String title;
  final List<Widget> children;
  final bool busy;
  final String? error;

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: Text(title)),
    body: PaperBackground(
      child: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(24, 12, 24, 32),
          child: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 620),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const BrandWordmark(compact: true),
                  const SizedBox(height: 26),
                  Text(
                    title,
                    style: Theme.of(context).textTheme.headlineMedium,
                  ),
                  const SizedBox(height: 20),
                  if (busy)
                    const LinearProgressIndicator(
                      semanticsLabel: 'Cargando información',
                    ),
                  if (error != null) ...[
                    Container(
                      padding: const EdgeInsets.all(16),
                      margin: const EdgeInsets.only(bottom: 16),
                      decoration: BoxDecoration(
                        color: Theme.of(context).colorScheme.errorContainer,
                        borderRadius: BorderRadius.circular(18),
                      ),
                      child: Text(
                        error!,
                        style: TextStyle(
                          color: Theme.of(context).colorScheme.onErrorContainer,
                        ),
                      ),
                    ),
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
    padding: const EdgeInsets.all(18),
    margin: const EdgeInsets.only(bottom: 14),
    decoration: BoxDecoration(
      color: warmSurface,
      border: Border.all(color: outlineColor),
      borderRadius: BorderRadius.circular(20),
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
        content: Text(detail),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Volver'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Confirmar'),
          ),
        ],
      ),
    ) ??
    false;
