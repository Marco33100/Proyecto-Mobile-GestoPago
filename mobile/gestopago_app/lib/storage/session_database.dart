import 'package:path/path.dart' as path;
import 'package:sqflite/sqflite.dart';

import '../models/login_session.dart';

class SessionDatabase {
  SessionDatabase._();

  static final SessionDatabase instance = SessionDatabase._();
  Database? _database;

  Future<Database> get database async {
    return _database ??= await _open();
  }

  Future<Database> _open() async {
    final databasePath = path.join(
      await getDatabasesPath(),
      'gestopago_session.db',
    );
    return openDatabase(
      databasePath,
      version: 3,
      onConfigure: (db) => db.execute('PRAGMA foreign_keys = ON'),
      onCreate: (db, version) => _createSessionTable(db),
      onUpgrade: (db, oldVersion, newVersion) async {
        if (oldVersion < 3) {
          await db.execute('DROP TABLE IF EXISTS user_session');
          await _createSessionTable(db);
        }
      },
    );
  }

  Future<void> _createSessionTable(DatabaseExecutor db) async {
    await db.execute('''
      CREATE TABLE user_session (
        id INTEGER PRIMARY KEY CHECK (id = 1),
        access_token TEXT NOT NULL,
        token_type TEXT NOT NULL,
        expires_at TEXT NOT NULL,
        user_id TEXT NOT NULL,
        email TEXT NOT NULL,
        identifier TEXT NOT NULL,
        full_name TEXT NOT NULL
      )
    ''');
  }

  Future<void> save(LoginSession session) async {
    final db = await database;
    await db.transaction((transaction) async {
      await transaction.insert(
        'user_session',
        session.toDatabaseMap(),
        conflictAlgorithm: ConflictAlgorithm.replace,
      );
    });
  }

  Future<LoginSession?> current() async {
    final db = await database;
    final rows = await db.query('user_session', where: 'id = ?', whereArgs: [1], limit: 1);
    if (rows.isEmpty) return null;

    final session = LoginSession.fromDatabaseMap(rows.first);
    if (session.expiresAt.isBefore(DateTime.now().toUtc())) {
      await clear();
      return null;
    }
    return session;
  }

  Future<void> clear() async {
    final db = await database;
    await db.delete('user_session');
  }
}
