# Aplicacion Flutter de Gestopago

Este modulo contiene login, registro bancario completo para ejecutivos y manejo de errores HTTP. PostgreSQL conserva los usuarios y sesiones; `flutter_secure_storage` conserva la credencial y su vencimiento mediante Android Keystore / iOS Keychain. No guarda la contrasena del usuario.

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

La app consulta `GET /api/auth/sesion` al recuperar la credencial segura y obtiene
el perfil vigente del backend. Un fallo temporal no borra la credencial; una
sesion vencida o rechazada si se retira. La version nueva requiere iniciar sesion
otra vez. El archivo SQLite de instalaciones anteriores no se elimina automaticamente.
Los pagos pendientes usan almacenamiento independiente. El backend debe desplegarse
antes de distribuir esta version de la app.

## Cierre de sesion y fallos locales

La accion **Cerrar sesion** llama a `POST /api/auth/logout` con el JWT y luego
borra el token en el almacen seguro. Un 401 se considera una sesion ya invalida; errores de
red o del servidor muestran un aviso de que la revocacion remota no fue confirmada.
Un fallo del almacenamiento muestra un mensaje y permite volver al login sin quedar cargando.
El almacenamiento se abstrae mediante `SessionStore` para probar esos fallos sin
necesitar un emulador. Las pruebas verifican persistencia, limpieza, concurrencia,
restauracion del perfil y el contrato del almacen seguro con un sustituto de prueba;
no sustituyen verificar Keychain en un dispositivo iOS. El backend debe utilizar
HTTPS fuera del entorno local.
