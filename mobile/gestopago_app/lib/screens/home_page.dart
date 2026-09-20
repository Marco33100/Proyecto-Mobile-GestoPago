import 'package:flutter/material.dart';

import '../models/login_session.dart';

class HomePage extends StatelessWidget {
  const HomePage({super.key, required this.session, required this.onLogout});

  final LoginSession session;
  final Future<void> Function() onLogout;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Gestopago'),
        actions: [
          IconButton(
            tooltip: 'Cerrar sesion',
            onPressed: onLogout,
            icon: const Icon(Icons.logout),
          ),
        ],
      ),
      body: Center(child: Text('Bienvenido, ${session.fullName}')),
    );
  }
}
