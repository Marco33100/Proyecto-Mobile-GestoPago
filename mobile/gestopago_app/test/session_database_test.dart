import 'dart:io';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:gestopago_app/models/login_session.dart';
import 'package:gestopago_app/storage/session_database.dart';
import 'package:gestopago_app/storage/session_token_store.dart';
import 'package:gestopago_app/services/auth_api_service.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

class FakeTokenStore implements SessionTokenStore {
  String? reference;
  String? token;
  bool failWrite = false;
  @override
  Future<void> write(String reference, String token) async {
    if (failWrite) throw StateError('Keystore no disponible');
    this.reference = reference;
    this.token = token;
  }

  @override
  Future<String?> read(String reference) async =>
      this.reference == reference ? token : null;
  @override
  Future<void> clear() async {
    reference = null;
    token = null;
  }
}

LoginSession session(String token, {bool expired = false}) => LoginSession(
  accessToken: token,
  tokenType: 'Bearer',
  expiresAt: DateTime.now().toUtc().add(Duration(hours: expired ? -1 : 1)),
  userId: 'usuario-$token',
  email: 'uno@example.com',
  identifier: 'uno@example.com',
  fullName: 'Marco Martinez',
  rol: 'EJECUTIVO',
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  sqfliteFfiInit();
  late FakeTokenStore tokens;
  late SessionDatabase store;
  setUp(() {
    tokens = FakeTokenStore();
    store = SessionDatabase(
      tokenStore: tokens,
      factory: databaseFactoryFfi,
      databasePath: inMemoryDatabasePath,
    );
  });
  tearDown(() async {
    await (await store.database).close();
  });

  test('SQLite conserva perfil y rol pero nunca el token', () async {
    await store.save(session('SECRETO-UNICO'));
    final db = await store.database;
    final rows = await db.query('user_session');
    expect(rows.single.containsKey('access_token'), isFalse);
    expect(rows.single.values, isNot(contains('SECRETO-UNICO')));
    expect(tokens.token, 'SECRETO-UNICO');
    final restored = await store.current();
    expect(restored!.accessToken, 'SECRETO-UNICO');
    expect(restored.isExecutive, isTrue);
    await store.clear();
    expect(tokens.token, isNull);
    expect(await store.current(), isNull);
  });

  test('perfil y secreto desparejados no restauran una sesion', () async {
    await store.save(session('uno'));
    tokens.reference = 'otra-sesion';
    expect(await store.current(), isNull);
    expect(tokens.token, isNull);
    expect(await (await store.database).query('user_session'), isEmpty);
  });

  test(
    'fallo al guardar secreto no persiste sesion ni restaura la anterior',
    () async {
      await store.save(session('anterior'));
      tokens.failWrite = true;
      await expectLater(store.save(session('nueva')), throwsStateError);
      expect(await store.current(), isNull);
      expect(tokens.token, isNull);
    },
  );

  test(
    'fallo SQLite despues de escribir el secreto limpia ambas partes',
    () async {
      final db = await store.database;
      await db.execute(
        "CREATE TRIGGER reject_session BEFORE INSERT ON user_session BEGIN SELECT RAISE(ABORT, 'fixture'); END",
      );
      await expectLater(
        store.save(session('secreto')),
        throwsA(isA<DatabaseException>()),
      );
      expect(tokens.token, isNull);
      expect(await store.current(), isNull);
    },
  );

  test('sesion expirada elimina perfil y token seguro', () async {
    await store.save(session('expirada', expired: true));
    expect(await store.current(), isNull);
    expect(tokens.token, isNull);
  });

  test(
    'guardar y borrar concurrentemente no mezcla perfiles y tokens',
    () async {
      await Future.wait([
        store.save(session('uno')),
        store.clear(),
        store.save(session('dos')),
      ]);
      expect((await store.current())!.accessToken, 'dos');
      expect((await store.current())!.userId, 'usuario-dos');
    },
  );

  test('actualizacion v4 retira token plaintext y exige login nuevo', () async {
    final directory = await Directory.systemTemp.createTemp(
      'gestopago-session-test-',
    );
    final dbPath = '${directory.path}/session.db';
    SessionDatabase? upgraded;
    try {
      final old = await databaseFactoryFfi.openDatabase(
        dbPath,
        options: OpenDatabaseOptions(
          version: 4,
          onCreate: (db, _) => db.execute(
            'CREATE TABLE user_session(id INTEGER PRIMARY KEY, access_token TEXT)',
          ),
        ),
      );
      await old.insert('user_session', {
        'id': 1,
        'access_token': 'token-antiguo',
      });
      await old.close();
      upgraded = SessionDatabase(
        tokenStore: tokens,
        factory: databaseFactoryFfi,
        databasePath: dbPath,
      );
      expect(await upgraded.current(), isNull);
      final db = await upgraded.database;
      final columns = await db.rawQuery('PRAGMA table_info(user_session)');
      expect(
        columns.map((row) => row['name']),
        isNot(contains('access_token')),
      );
      expect(await db.getVersion(), 5);
    } finally {
      if (upgraded != null) await (await upgraded.database).close();
      await directory.delete(recursive: true);
    }
  });

  test('adaptador seguro vincula referencia y limpia su clave', () async {
    FlutterSecureStorage.setMockInitialValues({});
    final secure = SecureSessionTokenStore();
    await secure.write('ref-uno', 'token-uno');
    expect(await secure.read('ref-uno'), 'token-uno');
    expect(await secure.read('ref-dos'), isNull);
    await secure.clear();
    expect(await secure.read('ref-uno'), isNull);
  });

  test(
    '429 de login muestra un mensaje de espera sin detalles internos',
    () async {
      final client = MockClient(
        (_) async => http.Response(
          '{"code":"AUTH_TOO_MANY_ATTEMPTS","message":"interno"}',
          429,
        ),
      );
      addTearDown(client.close);
      await expectLater(
        AuthApiService(
          client,
        ).login(email: 'uno@example.com', password: 'Segura123!'),
        throwsA(
          isA<ApiException>()
              .having((e) => e.statusCode, 'status', 429)
              .having((e) => e.message, 'message', contains('Espera')),
        ),
      );
    },
  );
}
