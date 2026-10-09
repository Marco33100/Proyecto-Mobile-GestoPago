# Aplicacion Flutter de Gestopago

Este modulo contiene login, registro bancario completo para ejecutivos y manejo de errores HTTP. SQLite (`sqflite`) conserva el perfil; `flutter_secure_storage` conserva el JWT mediante Android Keystore / iOS Keychain. No guarda la contrasena del usuario.

## Flujo de acceso y registro

El backend crea el primer ejecutivo mediante la configuracion privada descrita en el README de la raiz. No hay usuario o contrasena predeterminados.

1. Inicia sesion con el ejecutivo. En Inicio aparece **Registrar cliente**.
2. Captura el formulario completo: datos personales, contacto, domicilio, empleo y contrasena del cliente.
3. Se envia `POST /api/clientes` con el JWT del ejecutivo. El backend crea cliente, domicilio, cuenta bancaria y usuario asociado en una transaccion. No se llama a `/api/auth/register` ni se crea una cuenta adicional en `accounts`.
4. El mensaje muestra la cuenta asignada; la sesion del ejecutivo se conserva. El cliente nuevo puede iniciar sesion con su correo y contrasena, pero no puede administrar otros clientes.

Los nombres se normalizan eliminando espacios exteriores y repetidos. La contrasena no se recorta ni normaliza. Se muestran errores amigables por validacion, duplicados (409), sesion invalida (401), falta de permisos (403), fallos de red y persistencia. Los importes se envian como texto decimal para no perder precision con `double`.

La fecha de nacimiento se elige en un calendario, sin escritura libre ni hora.
El selector aplica el mismo limite de 18 anos que la validacion; cancelar no
modifica la fecha anterior. `DateOnly.format()` envia solamente `AAAA-MM-DD`
usando el dia elegido, sin convertirlo a UTC ni cambiarlo por zona horaria.
No se corrigen fechas imposibles por suposicion. El backend mantiene su validacion
estricta y devuelve 400 ante solicitudes con una fecha mal formada de otros clientes.

## Preparacion

El SDK y las carpetas nativas ya estan preparados. Con un emulador Android iniciado, ejecutar desde esta carpeta:

```powershell
flutter pub get
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080
```

Verificacion realizada: `flutter analyze` sin problemas y pruebas Flutter aprobadas.

`10.0.2.2` corresponde al localhost de Windows visto desde el emulador Android. Para un telefono fisico se debe usar la IP local de la computadora, por ejemplo `http://192.168.1.20:8080`, y ambos dispositivos deben estar en la misma red.

La base SQLite se inicializa en `SessionDatabase._open()`. Crea `user_session`
con perfil, rol, vencimiento y una referencia aleatoria, sin columna de JWT.
`SessionTokenStore` guarda token y referencia en almacenamiento seguro; en iOS
se restringe al dispositivo y a cuando esta desbloqueado. `save()`, `current()`
y `clear()` se serializan, ya que SQLite y el almacen seguro no comparten una
transaccion. Una referencia distinta, token ausente o sesion vencida invalida
ambas partes; una escritura fallida limpia la sesion parcial.

La version 5 retira las sesiones SQLite antiguas que contenian tokens en texto
plano y compacta esa base. Al actualizar hay que iniciar sesion una vez mas;
los datos del servidor no se borran. Esto no elimina copias antiguas que ya se
hayan exportado. Android desactiva el respaldo y excluye bases y preferencias
de transferencia; iOS configura los entitlements de Keychain del Runner.
El rol local solo controla la interfaz: el backend valida sesion y permisos
en cada peticion protegida. HTTP 429 muestra un aviso para esperar antes de reintentar.

## Cierre de sesion y fallos locales

La accion **Cerrar sesion** llama a `POST /api/auth/logout` con el JWT y luego
borra el perfil en SQLite y el token en el almacen seguro. Un 401 se considera una sesion ya invalida; errores de
red o del servidor muestran un aviso de que la revocacion remota no fue confirmada.
Un fallo de SQLite muestra un mensaje y permite volver al login sin quedar cargando.
El almacenamiento se abstrae mediante `SessionStore` para probar esos fallos sin
necesitar un emulador. Las pruebas verifican persistencia, limpieza, concurrencia,
actualizacion de SQLite y el contrato del almacen seguro con un sustituto de prueba;
no sustituyen verificar Keychain en un dispositivo iOS. El backend debe utilizar
HTTPS fuera del entorno local.
