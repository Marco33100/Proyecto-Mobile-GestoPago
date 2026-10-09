import 'package:flutter/material.dart';

import '../models/login_session.dart';
import '../services/auth_api_service.dart';
import 'register_page.dart';

class HomePage extends StatelessWidget {
  const HomePage(
      {super.key,
      required this.session,
      required this.onLogout,
      required this.authApi,
      this.isLoggingOut = false});

  final LoginSession session;
  final Future<void> Function() onLogout;
  final AuthApiService authApi;
  final bool isLoggingOut;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Gestopago'),
        actions: [
          IconButton(
            tooltip: 'Cerrar sesion',
            onPressed: isLoggingOut ? null : onLogout,
            icon: const Icon(Icons.logout),
          ),
        ],
      ),
      body: Center(
          child: Column(mainAxisSize: MainAxisSize.min, children: [
        Text('Bienvenido, ${session.fullName}'),
        if (session.isExecutive) ...[
          const SizedBox(height: 24),
          FilledButton.icon(
            icon: const Icon(Icons.person_add),
            label: const Text('Registrar cliente'),
            onPressed: isLoggingOut
                ? null
                : () async {
                    final result = await Navigator.of(context)
                        .push<String>(MaterialPageRoute(
                      builder: (_) => RegisterPage(
                          authApi: authApi,
                          session: session,
                          onSessionInvalid: onLogout),
                    ));
                    if (context.mounted && result != null) {
                      ScaffoldMessenger.of(context)
                          .showSnackBar(SnackBar(content: Text(result)));
                    }
                  },
          ),
        ],
      ])),
    );
  }
}
