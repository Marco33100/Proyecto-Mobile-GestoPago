import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;

import 'storage/session_database.dart';
import 'storage/session_store.dart';
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
  const SessionGate({super.key, required this.authApi, this.sessionStore});

  final AuthApiService authApi;
  final SessionStore? sessionStore;

  @override
  State<SessionGate> createState() => _SessionGateState();
}

class _SessionGateState extends State<SessionGate> {
  LoginSession? _session;
  bool _loading = true;
  bool _loggingOut = false;

  SessionStore get _store => widget.sessionStore ?? SessionDatabase.instance;

  @override
  void initState() {
    super.initState();
    _restoreSession();
  }

  Future<void> _restoreSession() async {
    try {
      final session = await _store.current();
      if (mounted) setState(() => _session = session);
    } catch (_) {
      _showNotice(
          'No fue posible recuperar la sesion local. Inicia sesion nuevamente.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _logout() async {
    if (_loggingOut) return;
    setState(() => _loggingOut = true);
    final session = _session;
    final warnings = <String>[];
    try {
      if (session != null) await widget.authApi.logout(session);
    } catch (_) {
      warnings.add(
          'No se pudo confirmar el cierre en el servidor. La sesion remota podria seguir activa.');
    }
    try {
      await _store.clear();
    } catch (_) {
      warnings
          .add('No fue posible borrar la sesion guardada en el dispositivo.');
    } finally {
      if (mounted) {
        setState(() {
          _session = null;
          _loggingOut = false;
        });
        if (warnings.isNotEmpty) _showNotice(warnings.join(' '));
      }
    }
  }

  void _showNotice(String message) {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text(message)));
    });
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    final session = _session;
    if (session != null) {
      return HomePage(
          session: session,
          onLogout: _logout,
          authApi: widget.authApi,
          isLoggingOut: _loggingOut);
    }
    return LoginPage(
      authApi: widget.authApi,
      sessionStore: _store,
      onAuthenticated: (session) => setState(() => _session = session),
    );
  }
}
