# Aplicacion Flutter de Gestopago

Este modulo contiene registro, login, manejo de errores HTTP y persistencia local de la sesion con SQLite (`sqflite`). No guarda la contrasena del usuario.

## Preparacion

El SDK y las carpetas nativas ya estan preparados. Con un emulador Android iniciado, ejecutar desde esta carpeta:

```powershell
flutter pub get
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080
```

Verificacion realizada: `flutter analyze` sin problemas y pruebas Flutter aprobadas.

`10.0.2.2` corresponde al localhost de Windows visto desde el emulador Android. Para un telefono fisico se debe usar la IP local de la computadora, por ejemplo `http://192.168.1.20:8080`, y ambos dispositivos deben estar en la misma red.

La base SQLite se inicializa en `SessionDatabase._open()`. En la primera ejecucion crea la tabla `user_session`; `save()` reemplaza de forma transaccional la unica sesion y `current()` elimina sesiones expiradas.

> Para una aplicacion productiva, el token debe almacenarse en el Keychain/Keystore mediante almacenamiento seguro. SQLite se utiliza aqui porque es un requerimiento de la actividad. Nunca se guarda la contrasena.
