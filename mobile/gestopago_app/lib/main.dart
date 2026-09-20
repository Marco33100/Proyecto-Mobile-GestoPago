import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;

import 'storage/session_database.dart';
import 'models/login_session.dart';
import 'screens/home_page.dart';
import 'screens/login_page.dart';
import 'services/auth_api_service.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const GestopagoApp());
}

class GestopagoApp extends StatefulWidget {
  const GestopagoApp({super.key});

  @override
  State<GestopagoApp> createState() => _GestopagoAppState();
}

class _GestopagoAppState extends State<GestopagoApp> {
  late final http.Client _httpClient;
  late final AuthApiService _authApi;

  @override
  void initState() {
    super.initState();
    _httpClient = http.Client();
    _authApi = AuthApiService(_httpClient);
  }

  @override
  void dispose() {
    _httpClient.close();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Gestopago',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(colorSchemeSeed: Colors.indigo, useMaterial3: true),
      home: SessionGate(authApi: _authApi),
    );
  }
}

class SessionGate extends StatefulWidget {
  const SessionGate({super.key, required this.authApi});

  final AuthApiService authApi;

  @override
  State<SessionGate> createState() => _SessionGateState();
}

class _SessionGateState extends State<SessionGate> {
  LoginSession? _session;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _restoreSession();
  }

  Future<void> _restoreSession() async {
    final session = await SessionDatabase.instance.current();
    if (mounted) {
      setState(() {
        _session = session;
        _loading = false;
      });
    }
  }

  Future<void> _logout() async {
    await SessionDatabase.instance.clear();
    if (mounted) setState(() => _session = null);
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    final session = _session;
    if (session != null) {
      return HomePage(session: session, onLogout: _logout);
    }
    return LoginPage(
      authApi: widget.authApi,
      onAuthenticated: (session) => setState(() => _session = session),
    );
  }
}
