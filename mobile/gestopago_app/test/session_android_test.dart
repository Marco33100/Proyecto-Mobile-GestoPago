import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite/sqflite.dart' as sqlite;
import 'package:gestopago_app/storage/session_database.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test(
    'apertura respeta la restricción Android para PRAGMA con resultados',
    () async {
      const channel = MethodChannel('com.tekartik.sqflite');
      var secureDeleteEnabled = false;
      final messenger =
          TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      messenger.setMockMethodCallHandler(channel, (call) async {
        if (call.method == 'openDatabase') return 1;
        if (call.method == 'closeDatabase') return null;
        final arguments = call.arguments as Map;
        final sql = arguments['sql'];
        if (sql == 'PRAGMA secure_delete = ON') {
          if (call.method != 'query') {
            throw PlatformException(
              code: 'sqlite_error',
              message:
                  'Queries can be performed using query or rawQuery methods only.',
            );
          }
          secureDeleteEnabled = true;
          return {
            'columns': ['secure_delete'],
            'rows': [
              [1],
            ],
          };
        }
        if (call.method == 'query' && sql == 'PRAGMA user_version') {
          return {
            'columns': ['user_version'],
            'rows': [
              [5],
            ],
          };
        }
        return null;
      });
      addTearDown(() => messenger.setMockMethodCallHandler(channel, null));
      final store = SessionDatabase(
        factory: sqlite.databaseFactorySqflitePlugin,
        databasePath: sqlite.inMemoryDatabasePath,
      );
      final db = await store.database;
      try {
        expect(db.isOpen, isTrue);
        expect(secureDeleteEnabled, isTrue);
      } finally {
        await db.close();
      }
    },
  );
}
