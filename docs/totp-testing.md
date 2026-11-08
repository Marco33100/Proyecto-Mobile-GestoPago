# Pruebas de pagos con TOTP sin telefono

TOTP reemplaza completamente la biometria para autorizar pagos. Las retenciones, idempotencia, validaciones bancarias y conciliacion se conservan. El algoritmo usa los vectores oficiales de [RFC 6238](https://datatracker.ietf.org/doc/html/rfc6238) y HOTP de [RFC 4226](https://www.rfc-editor.org/rfc/rfc4226).

## Preparacion local

Configurar GESTOPAGO_TOKEN_STORAGE_KEY tambien en el proceso local o application-local.properties (ignorado por Git); la variable de Render no se comparte con Windows. Clave aleatoria de 32 bytes Base64; nunca la clave publica Base32 del autenticador. Guardarla entre reinicios para poder descifrar registros. Se admite la anterior mediante GESTOPAGO_TOKEN_PREVIOUS_STORAGE_KEY y se recifra al usar/activar el factor. No mezclar replicas con distintas claves.

Los secretos TOTP activos no vencen junto con el Bearer: antes de retirar una clave anterior deben recifrarse todos, incluidos usuarios inactivos y respaldos necesarios. Esta version no incorpora una migracion masiva de claves TOTP; no retirar una clave porque hayan pasado 24 horas.

El registro del segundo factor no depende de GESTOPAGO_PAYMENTS_ENABLED ni de claves del proveedor: se puede probar sin habilitar ni enviar pagos. El backend debe tener base local, sesion CLIENTE y la clave de almacenamiento. Para preparar y enviar pagos siguen siendo necesarias las configuraciones previas del proveedor, catalogo, limites aprobados y ambiente de pruebas. No se agrega ningun endpoint que fabrique saldos.

Al iniciar se aplica V21: crea payment_second_factors y expira solo pagos PREPARADO anteriores; ENVIADO, PENDIENTE, REVISION y sus retenciones permanecen. La tabla biometrica historica se conserva sin consumidores Java; no se reescribe V17.

## Registro desde Swagger o Postman

En el backend local iniciado en 8081 abrir http://localhost:8081/swagger-ui/index.html. Iniciar sesion con un CLIENTE mediante POST /api/auth/login y configurar Authorize con su accessToken. Es el JWT propio, no el token de Gestopago. Un nuevo login puede reemplazar la sesion anterior.

1. POST /api/pagos/segundo-factor/estado con `{}` devuelve codigo 1, datos.activo true/false.
2. POST /api/pagos/segundo-factor/preparar con el siguiente cuerpo:

```json
{"contrasena":"CONTRASENA_ACTUAL"}
```

Respuesta ilustrativa, no usar la clave de ejemplo como secreto real:

```json
{"codigo":1,"mensaje":"Segundo factor preparado","datos":{"secreto":"CLAVE_BASE32_DE_32_CARACTERES","uri":"otpauth://totp/...","venceEn":"..."}}
```

El secreto se recibe para este registro y no se puede consultar despues. En Flutter se muestra QR y clave para registro manual; el QR se genera localmente, sin enviarlo a un sitio externo. Nunca compartir capturas del QR, clave, JWT ni recuperacion.

3. Sin telefono, abrir otra terminal PowerShell en la raiz del proyecto y ejecutar:

```powershell
.\scripts\Get-LocalTotp.ps1 -Continuo
```

Introducir en el prompt oculto la clave Base32 de datos.secreto. No introducir GESTOPAGO_TOKEN_STORAGE_KEY ni las claves AES. El script calcula localmente los codigos y no llama a ninguna API. Ctrl+C lo detiene. Si una politica local impide ejecutar scripts, puede usarse un autenticador TOTP de escritorio; no es necesario cambiar las politicas del equipo ni subir secretos a paginas web.

4. POST /api/pagos/segundo-factor/activar con el codigo mostrado:

```json
{"codigo":"123456"}
```

El valor es ilustrativo: sustituirlo por el codigo actual. Una activacion correcta devuelve datos.activo=true y diez codigosRecuperacion mostrados una sola vez. Guardarlos en un gestor seguro. Esperar al siguiente codigo de 30 segundos para autorizar un pago: el de activacion ya fue consumido. Si se pierde la respuesta de activacion, estado permite verificar si quedo activo; los codigos de recuperacion no se reexponen. Cambiar el autenticador con un TOTP vigente permite emitir un juego nuevo.

## Autorizacion y errores

POST /api/pagos/preparar mantiene los campos existentes y fija la cuenta, destino e importe. POST /api/pagos/autorizar ahora recibe:

```json
{"pagoId":"UUID_DEVUELTO_AL_PREPARAR","codigo":"123456"}
```

No acepta firma, claves publicas ni booleanos. Cambiar el monto, destino o cuenta requiere otra preparacion, no campos adicionales en autorizar. Un codigo se consume en la misma transaccion de la retencion. Repetir autorizar un pago ya enviado no consume otro codigo ni envia otro sendTx; consultar /api/pagos/consulta para conocer el estado. Un codigo ya consumido no autoriza otro pago.

- Codigo con formato invalido: HTTP 400.
- Codigo incorrecto, vencido, consumido o registro de otra sesion: HTTP 403.
- Tras cinco fallos persistidos, siguientes intentos: HTTP 429 durante cinco minutos.
- Sin sesion: HTTP 401; rol o propiedad incorrectos no permiten operar cuentas ajenas.
- Sin clave de almacenamiento o configuracion de pagos: HTTP 503 sin detalles internos.

Los errores bajo /api/pagos conservan codigo de texto 02 y mensaje Servicio no Disponible, con el estado HTTP correspondiente. Registro/estado usan GenericResponse codigo entero 1; autorizacion/consulta mantienen 01 aplicado y 02 para los otros estados. Un 02 con datos.estado=PENDIENTE o REVISION no autoriza a repetir la operacion.

Para cambiar el autenticador, preparar acepta contrasena mas codigoActual o codigoRecuperacion (solo uno). El factor previo sigue activo hasta activar el nuevo. Cada codigo de recuperacion funciona una sola vez y solo sirve para registrar otro factor. No se permite pagar con el codigo de recuperacion. Si se pierden autenticador y recuperacion, no hay bypass por contrasena; falta un procedimiento de recuperacion asistida con verificacion de identidad fuera de estos endpoints.

## Verificacion automatica

```powershell
.\gradlew.bat --offline test --tests "*TotpCodesTest" --tests "*PagoFlowPersistenceTest" --console=plain
```

TotpCodesTest comprueba los seis vectores SHA1 oficiales RFC 6238, desfase, replay y el script PowerShell en Windows. Las pruebas JPA comprueban registro, cifrado, recuperacion de un uso, sesiones, expiracion, bloqueo persistente, cambio de factor, codigo entre pagos y concurrencia financiera. DatabaseSchemaMigrationTest agrega la migracion V21 sobre PostgreSQL real y verifica que no cambie saldos ni operaciones enviadas; requiere las variables ONBOARDING_TEST_* existentes.

El generador en el mismo PC es una ayuda de desarrollo: no es un endpoint de pruebas ni una excepcion a las validaciones, pero usarlo junto con las credenciales en el mismo equipo reduce la independencia de factores. En produccion usar autenticador protegido y HTTPS; TOTP no es resistente al phishing. No se ha realizado un pago real para validar esta implementacion.
# Perfil TOTP local protegido con Windows

El generador admite `-PerfilLocal NOMBRE` para cargar un secreto previamente guardado con DPAPI CurrentUser en `data/totp-NOMBRE.secret.dpapi`. Estos archivos se excluyen de Git y solo pueden descifrarse con el usuario de Windows que los creo; no se guarda Base32 en texto plano. El perfil no se crea automaticamente ni evita el registro o la activacion del segundo factor mediante la API.

Para el perfil de demostracion `daniel` registrado en esta maquina:

```powershell
.\scripts\Get-LocalTotp.ps1 -PerfilLocal daniel -Continuo
```

No pide pegar Base32. `-SoloCodigo` devuelve solo los seis digitos para una prueba local automatizada. Los codigos de recuperacion del registro se conservaron aparte, tambien protegidos con DPAPI, en `data/totp-daniel.recovery.dpapi`. No publicar ni compartir estos archivos. Este perfil es una comodidad de desarrollo: usar contrasena y autenticador en el mismo equipo no demuestra independencia de factores frente a un equipo comprometido. En produccion debe utilizarse un autenticador protegido independiente.
