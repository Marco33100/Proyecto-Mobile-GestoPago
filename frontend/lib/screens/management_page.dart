import 'package:flutter/material.dart';
import '../config/http_client_factory.dart';
import '../models/login_session.dart';
import '../services/admin_api_service.dart';
import '../services/auth_api_service.dart';
import '../services/payment_api_service.dart';
import '../theme.dart';
import '../widgets/management_layout.dart';
import '../widgets/responsive.dart';
import '../widgets/surfaces.dart';
import 'management_sections.dart';

class ManagementPage extends StatefulWidget {
  const ManagementPage({
    super.key,
    required this.session,
    required this.authApi,
    this.api,
  });
  final LoginSession session;
  final AuthApiService authApi;
  final AdminApiService? api;
  @override
  State<ManagementPage> createState() => _ManagementPageState();
}

class _ManagementPageState extends State<ManagementPage> {
  final _client = createHttpClient();
  late final _api =
      widget.api ??
      AdminApiService(
        PaymentApiService(_client, widget.session),
        widget.session,
      );
  @override
  void dispose() {
    _client.close();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ManagementLayout(
    title: 'Gestión',
    maxWidth: ContentWidth.wide,
    children: [
      if (!widget.session.isExecutive)
        const Text('Solo un ejecutivo puede usar esta sección.')
      else ...[
        const Text(
          'Elige qué quieres atender. Cada sección usa tu acceso de ejecutivo.',
        ),
        const SizedBox(height: AppSpace.xl),
        ResponsiveGrid(
          minItemWidth: 360,
          maxColumns: 2,
          spacing: AppSpace.xl,
          children: [
            for (final (index, section) in ManagementSection.values.indexed)
              Padding(
                padding: const EdgeInsets.only(bottom: AppSpace.xl),
                child: FeatureTile(
                  icon: section.icon,
                  title: section.title,
                  description: section.description,
                  accent: index == 0,
                  onTap: () => section.open(context, _api, widget.authApi),
                ),
              ),
          ],
        ),
      ],
    ],
  );
}
