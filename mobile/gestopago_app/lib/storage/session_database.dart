import 'dart:convert';
import 'dart:math';
import 'package:path/path.dart' as path;
import 'package:sqflite/sqflite.dart' as sqlite;
import '../models/login_session.dart';
import 'session_store.dart';
import 'session_token_store.dart';

class SessionDatabase implements SessionStore {
  SessionDatabase({
    SessionTokenStore? tokenStore,
    sqlite.DatabaseFactory? factory,
    String? databasePath,
  }) : _tokenStore = tokenStore ?? SecureSessionTokenStore(),
       _factory = factory ?? sqlite.databaseFactory,
       _databasePath = databasePath;

  static final SessionDatabase instance = SessionDatabase();
  final SessionTokenStore _tokenStore;
  final sqlite.DatabaseFactory _factory;
  final String? _databasePath;
  Future<sqlite.Database>? _database;
  Future<void> _pending = Future<void>.value();

  Future<sqlite.Database> get database =>
      _database ??= _open().onError((error, stack) {
        _database = null;
        Error.throwWithStackTrace(
          error ?? StateError('SQLite no disponible'),
          stack,
        );
      });

  Future<sqlite.Database> _open() async {
    final databasePath =
        _databasePath ??
        path.join(await _factory.getDatabasesPath(), 'gestopago_session.db');
    var retiredPlaintextSession = false;
    final db = await _factory.openDatabase(
      databasePath,
      options: sqlite.OpenDatabaseOptions(
        version: 5,
        onConfigure: (db) async {
          await db.execute('PRAGMA foreign_keys = ON');
          await db.execute('PRAGMA secure_delete = ON');
        },
        onCreate: (db, version) => _createSessionTable(db),
        onUpgrade: (db, oldVersion, newVersion) async {
          if (oldVersion < 5) {
            // Retirar la sesion insegura, sin copiar el token antiguo al perfil.
            await db.execute('DROP TABLE IF EXISTS user_session');
            await _createSessionTable(db);
            retiredPlaintextSession = true;
          }
        },
      ),
    );
    try {
      if (retiredPlaintextSession) {
        // Fuera de onUpgrade: elimina paginas libres de esta base local.
        await db.execute('VACUUM');
        await _tokenStore.clear();
      }
      return db;
    } catch (_) {
      await db.close();
      rethrow;
    }
  }

  Future<void> _createSessionTable(sqlite.DatabaseExecutor db) => db.execute('''
    CREATE TABLE user_session (
      id INTEGER PRIMARY KEY CHECK (id = 1),
      session_reference TEXT NOT NULL,
      token_type TEXT NOT NULL,
      expires_at TEXT NOT NULL,
      user_id TEXT NOT NULL,
      email TEXT NOT NULL,
      identifier TEXT NOT NULL,
      full_name TEXT NOT NULL,
      rol TEXT NOT NULL DEFAULT 'CLIENTE'
    )
  ''');

  // SQLite y el almacen seguro no comparten transaccion. Serializar y vincular
  // ambas partes evita restaurar perfiles con tokens de una sesion diferente.
  Future<T> _serialized<T>(Future<T> Function() action) {
    final result = _pending.then((_) => action());
    _pending = result.then<void>((_) {}, onError: (Object _, StackTrace __) {});
    return result;
  }

  @override
  Future<void> save(LoginSession session) => _serialized(() async {
    final db = await database;
    await _clear(db);
    final random = Random.secure();
    final reference = base64UrlEncode(
      List<int>.generate(32, (_) => random.nextInt(256)),
    );
    try {
      await _tokenStore.write(reference, session.accessToken);
      await db.insert(
        'user_session',
        session.toDatabaseMap(sessionReference: reference),
        conflictAlgorithm: sqlite.ConflictAlgorithm.replace,
      );
    } catch (_) {
      await _clear(db);
      rethrow;
    }
  });

  @override
  Future<LoginSession?> current() => _serialized(() async {
    final db = await database;
    final rows = await db.query(
      'user_session',
      where: 'id = ?',
      whereArgs: [1],
      limit: 1,
    );
    if (rows.isEmpty) {
      await _tokenStore.clear();
      return null;
    }
    final row = rows.first;
    final reference = row['session_reference'];
    if (reference is! String) {
      await _clear(db);
      return null;
    }
    final token = await _tokenStore.read(reference);
    if (token == null) {
      await _clear(db);
      return null;
    }
    final session = LoginSession.fromDatabaseMap(row, accessToken: token);
    if (!session.expiresAt.isAfter(DateTime.now().toUtc())) {
      await _clear(db);
      return null;
    }
    return session;
  });

  Future<void> _clear(sqlite.Database db) async {
    try {
      await db.delete('user_session');
    } finally {
      await _tokenStore.clear();
    }
  }

  @override
  Future<void> clear() => _serialized(() async {
    try {
      await _clear(await database);
    } catch (_) {
      // Si SQLite no abre, intentar igualmente retirar la credencial segura.
      await _tokenStore.clear();
      rethrow;
    }
  });
}
