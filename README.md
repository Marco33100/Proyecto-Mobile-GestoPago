# Catálogo, onboarding y pagos de servicios con Gestopago

Documento técnico de la solución implementada.

Backend Spring Boot y aplicación móvil Flutter para registrar clientes, administrar
cuentas internas, consultar el catálogo de Gestopago y pagar servicios, recargas o
tarjetas de regalo mediante el proveedor externo. PostgreSQL conserva los datos y
Redis funciona como caché de lectura del catálogo.

## Objetivo y alcance

Registrar clientes personas físicas con sus datos personales, contacto, domicilio
e información laboral; crear automáticamente una cuenta interna y un usuario de
acceso; consultar y actualizar información y realizar bajas lógicas. La API integra
el catálogo externo de Gestopago, con persistencia, caché y sincronización diaria,
y contiene un flujo para preparar, autorizar y consultar pagos de servicios.
Flutter consume la API para login, cuentas, catálogo, pagos y registro de clientes
por un ejecutivo autenticado.

Se trata de una implementación académica: la cuenta asignada es interna, no una
cuenta real provisionada por una institución financiera. Los pagos son compras
externas de servicios, recargas y tarjetas de regalo mediante Gestopago; no son
transferencias bancarias. No hay un historial general de movimientos bancarios ni
reconocimiento facial. La autorización de pagos usa un segundo factor TOTP de seis
dígitos. El módulo de pagos está deshabilitado por defecto y requiere configuración
explícita del proveedor.

### Índice de documentación

- [Tecnologías y organización](#tecnologías-y-organización).
- [Configuración y ejecución local](#configuración-y-ejecución-local).
- [Solución y decisiones técnicas](#resumen-de-la-solución-y-decisiones-técnicas).
- [Catálogo y errores de integración](#endpoints-del-catálogo).
- [Clientes, cuentas, usuarios y seguridad](#clientes-personas-físicas).
- [Pagos de servicios y segundo factor](docs/payment-flow.md).
- [Consultas y optimización](#revision-de-consultas).
- [Contrato de tipos y longitudes](docs/database-lengths.md).
- [Configuración y uso de Flutter](mobile/gestopago_app/README.md).

## Tecnologías y organización

| Tecnología | Uso en el proyecto |
| --- | --- |
| Java 17 como nivel de compilación | Backend; ejecución local también verificada con JDK 21 |
| Spring Boot 3.3.6 | API REST, configuración y ciclo de vida |
| Spring Data JPA / Hibernate | Repositorios, entidades y transacciones |
| PostgreSQL | Persistencia, integridad e índices; pruebas locales con PostgreSQL 18 |
| Flyway | Esquema principal V1 a V16; pagos y conciliación V17 a V23 en `db/payments` |
| Redis / Spring Data Redis | Caché del catálogo, con TTL configurable |
| OpenFeign / Jackson XML | Consumo HTTP y conversión XML a modelos Java |
| MapStruct | Conversión del modelo de productos a entidades y DTOs |
| Spring Security / JWT / BCrypt | Autenticación, autorización y hashes de contraseñas |
| Gradle Wrapper | Compilación y ejecución reproducible sin instalar Gradle aparte |
| JUnit / Mockito / H2 | Pruebas unitarias y transaccionales; PostgreSQL para verificar el esquema real |
| Flutter / Dart / sqflite | Aplicación móvil y persistencia local de sesión |
| Swagger / OpenAPI / Actuator | Documentación de endpoints y comprobación de salud |

### Arquitectura por capas

Los paquetes del backend están bajo `src/main/java/com/proyecto/servicios`.

| Capa / directorio | Responsabilidad y ejemplos |
| --- | --- |
| `controller` | Contratos HTTP: `ClienteController`, `CuentaController`, `AuthController`, `ProductCatalogController`, `PagoController` y `ConciliacionPagoController` |
| `service` y `service/Impl` | Reglas de negocio, sesiones, catálogo, pagos, TOTP, conciliación y transacciones |
| `client` | Comunicación con Gestopago; no contiene reglas del cliente bancario |
| `repositorys` | Consultas de persistencia mediante Spring Data JPA |
| `entity/sf` y `entity/gestopago` | Mapeos Java/PostgreSQL de los dos dominios |
| `model` y `mapper` | Payloads, DTOs, respuestas y conversiones; no exponen hashes ni contraseñas |
| `config`, `support`, `exception` | Configuración, validaciones compartidas y manejo de errores |
| `src/main/resources/db/migration` | Historial de cambios de base de datos |
| `src/main/resources/db/initial` | Una V1 consolidada, exclusiva para bases nuevas |
| `mobile/gestopago_app` | Cliente Flutter separado del backend |

Se usa inyección por constructor: los campos `private final` representan
dependencias que no cambian durante la vida del componente. La validación del
formulario Flutter mejora la experiencia, pero el backend vuelve a validar todo.

### Modelo de persistencia

El esquema principal de onboarding y catálogo comprende `clientes`, `domicilios`,
`cuentas`, `app_users`, `user_sessions`, `gestopago_products` y `gestopago_tokens`.
El módulo de pagos extiende `cuentas` con el saldo retenido y agrega tablas para
órdenes, evidencias, factores TOTP y conciliaciones. Se conserva `payment_biometric_keys`
como estructura histórica; el flujo actual autoriza pagos con TOTP.
`flyway_schema_history` es una tabla técnica. Las tablas `legacy_*`, si existen,
son archivo del flujo anterior y no participan en los endpoints actuales.

Cliente tiene un domicilio, como máximo un usuario asociado y puede tener varias
cuentas. La base conserva relaciones opcionales para registros históricos y
ejecutivos sin cliente; los nuevos registros crean todas las asociaciones en una
transacción. Un usuario mantiene como máximo una sesión local vigente.
Productos y tokens de Gestopago pertenecen a la integración externa: sus IDs no
se usan como llaves foráneas hacia clientes o cuentas bancarias.

Las bases existentes usan `src/main/resources/db/migration` para el historial
principal V1 a V16; `FlywayConfig` agrega además `src/main/resources/db/payments`
con las migraciones V17 a V23. No editar ni eliminar migraciones que ya fueron
aplicadas.

### Instalación nueva con esquema inicial consolidado

`src/main/resources/db/initial/V1__create_initial_schema.sql` crea directamente el
esquema principal de clientes, cuentas y catálogo, equivalente al estado final V16.
El perfil `new-database` selecciona esa ubicación y `FlywayConfig` agrega
`db/payments`, por lo que Flyway aplica después las migraciones V17 a V23 de pagos
y conciliación. No se ejecuta también el historial principal V1 a V16.
La instalación no carga datos reales ni crea registros artificiales en el historial.
No crea las tablas retiradas `personas` y `accounts`; conserva `id_anterior` para
mantener la estructura de clientes.

Crear primero una base **nueva y vacía**, por ejemplo `gestopago_nueva`, desde
pgAdmin conectado a la base administrativa `postgres`:

```sql
CREATE DATABASE gestopago_nueva;
```

Después, desde la raíz del backend, seleccionar el perfil explícitamente:

```powershell
$env:NEW_DATABASE_JDBC_URL = "jdbc:postgresql://localhost:5432/gestopago_nueva"
.\gradlew.bat bootRun --args="--spring.profiles.active=local,new-database"
```

El usuario y la contraseña siguen viniendo de la configuración local o de
`SPRING_DATASOURCE_USERNAME` y `SPRING_DATASOURCE_PASSWORD`. La URL nueva es
obligatoria, sin valor por defecto, para no apuntar accidentalmente a `Database-1`.
El perfil deshabilita el baseline automático de bases con tablas existentes.


## Configuración y ejecución local

### Requisitos

- JDK 17 o superior compatible con el Gradle Wrapper utilizado.
- PostgreSQL iniciado, una base creada y credenciales válidas.
- Redis iniciado si se desea probar el primer nivel de caché.
- Credenciales autorizadas de Gestopago para comprobar la integración real.
- Flutter SDK y emulador Android para ejecutar la aplicación móvil.

La configuración compartida está en `src/main/resources/application.properties`.
El perfil predeterminado es `local`. Los valores privados pueden definirse en
`src/main/resources/application-local.properties`, ignorado por Git, o mediante
las variables de entorno previstas en la configuración. No se publica el archivo
privado ni se incorporan secretos a Java, Flutter, ejemplos o imágenes Docker.

| Propiedad | Finalidad / valor predeterminado |
| --- | --- |
| `app.database.enabled` | Activar persistencia y autenticación: debe ser `true` para usar el módulo bancario |
| `spring.datasource.url`, `username`, `password` | Conexión PostgreSQL; URL local predeterminada `jdbc:postgresql://localhost:5432/Database-1` |
| `spring.data.redis.host`, `port`, `password` | Redis; host `localhost`, puerto `6379`; contraseña vacía solo si la instancia local no exige autenticación |
| `spring.cloud.config.enabled` | `false` en la configuración local; no exige un Config Server para arrancar |
| `product.service.url` | Base de Gestopago: `https://gestopago.portalventas.net` |
| `product.service.bearer-token`, `api-key` | Secretos de integración, sin valores reales en el repositorio |
| `product.cache.key`, `ttl` | `gestopago:products:v1`, expiración `26h` |
| `product.sync.cron`, `zone` | `0 0 2 * * *`: diario a las 02:00 en `America/Mexico_City` |
| `product.sync.temp-directory` | `./data/gestopago-sync`, fuera del código fuente |
| `auth.jwt.secret`, `issuer`, `expiration` | Llave estable privada, emisor `prueba-api`, duración `8h` |
| `cliente.cuenta.saldo-inicial` | `0.00`; debe ser no negativo |

Los timeouts predeterminados de Gestopago son 5 segundos para conexión y 10
segundos para lectura. Redis tiene timeouts de conexión y lectura de 2 segundos.
El cooldown de errores externos es 30 segundos. Estos valores son configurables;
no constituyen una garantía de tiempo total de respuesta.

### Arranque y comprobación

Desde la raíz Java, donde están `build.gradle` y `gradlew.bat`:

```powershell
.\gradlew.bat bootRun
```

El proceso permanece activo mientras atiende solicitudes; la barra de progreso
de `bootRun` no tiene que llegar al 100 % para que la API esté disponible. Revisar
los logs de inicio y comprobar:

- Salud: `http://localhost:8080/actuator/health`.
- Swagger: `http://localhost:8080/swagger-ui/index.html`.
- OpenAPI: `http://localhost:8080/v3/api-docs`.

La salud básica no sustituye probar PostgreSQL, Redis y los endpoints de negocio.
Crear el primer ejecutivo siguiendo la sección de bootstrap de este documento,
iniciar sesión en `/api/auth/login` y colocar su `accessToken` en **Authorize** de
Swagger. El token de usuario no es el Bearer Token del proveedor Gestopago.

Desde `mobile/gestopago_app`, con el emulador Android encendido:

```powershell
flutter pub get
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080
```

`10.0.2.2` permite al emulador acceder al backend de la computadora. Un teléfono
físico necesita la IP de la computadora accesible en su red. Los ejemplos HTTP
son para desarrollo local. Las credenciales siempre se mantienen fuera del código
fuente y del repositorio.

## Resumen de la solución y decisiones técnicas

Se implementó la integración con el servicio externo Gestopago para consumir el
endpoint `GET /sistema/service/getProductList.do`. La comunicación se realiza con
Spring Cloud OpenFeign y la respuesta XML se transforma a DTOs de Java mediante
Jackson XML. Las credenciales de autenticación —Bearer Token y API Key— se obtienen
desde la configuración de la aplicación y nunca se escriben directamente en el
código fuente.

La solución mantiene una separación por capas: el `Controller` expone el endpoint
local, el `Service` coordina el flujo de negocio, el `Client/Integration` se comunica
con Gestopago y los DTOs representan la respuesta recibida. Las dependencias se
inyectan mediante constructores para facilitar las pruebas y evitar acoplamiento
entre componentes.

Para optimizar las consultas se utiliza el patrón Cache-Aside. Primero se busca el
catálogo en Redis; si no está disponible o se encuentra vacío, se consulta
PostgreSQL; solamente cuando ninguna de esas fuentes tiene información se realiza
la petición a Gestopago. Este orden disminuye el tiempo de respuesta, reduce llamadas
innecesarias al proveedor y permite continuar operando cuando Redis presenta una
falla temporal.

También se agregó una tarea programada diaria que descarga el catálogo, crea una
carpeta temporal independiente y serializa la respuesta en XML. Antes de reemplazar
la información se compara la cantidad de productos nuevos contra la existente. El
catálogo únicamente se actualiza cuando la nueva cantidad es mayor; si está vacío,
es igual o es menor, se conserva la información vigente. Después de guardar
correctamente en PostgreSQL se actualiza Redis.

La integración controla errores de comunicación, autenticación, respuestas HTTP no
exitosas, timeouts, deserialización y persistencia. Los errores se transforman en
respuestas JSON con códigos propios y mensajes comprensibles, sin exponer tokens,
contraseñas ni trazas internas. Asimismo, se registran en logs el inicio, el final y
los fallos de cada invocación.

Finalmente, se crearon pruebas unitarias con respuestas simuladas para comprobar el
caso exitoso, los fallos del servicio externo, los timeouts, el XML inválido y el
orden de fallback entre Redis, PostgreSQL y Gestopago. Estas decisiones buscan
mantener el código legible, comprobable y preparado para futuras ampliaciones.

El backend también implementa pagos externos de productos del catálogo. El cliente
selecciona una cuenta y un producto, prepara la operación, la autoriza con TOTP y
puede consultar su estado. Las órdenes pendientes no se reenvían automáticamente;
la conciliación de casos inciertos tiene endpoints separados y doble aprobación.
El módulo se deshabilita por defecto mediante `GESTOPAGO_PAYMENTS_ENABLED`.
El flujo completo y sus límites están descritos en [Pagos con segundo factor TOTP](docs/payment-flow.md).

## Endpoints del catálogo

- `GET /api/products`: endpoint para clientes. Aplica el fallback Redis,
  PostgreSQL y, finalmente, la integración con Gestopago.

El endpoint cliente reutiliza el servicio interno de integración para consultar
Gestopago, deserializar y validar el XML, convertir el modelo mediante MapStruct,
guardar en PostgreSQL y actualizar Redis. No realiza una petición HTTP hacia el
propio backend, lo que evita duplicación y latencia innecesaria.

### Serialización, caché y sincronización

El XML recibido se deserializa al modelo Java `ProductListResponse`. MapStruct
convierte productos a entidades para persistirlos y a DTOs para responder.
Redis usa `RedisTemplate<String, ProductListResponse>` y un serializador JSON:
la aplicación trabaja con un objeto de respuesta, aunque el valor se almacene
como una clave Redis de tipo `STRING`. Eso no implica que sea RedisJSON.

Una lectura con datos en PostgreSQL repuebla Redis de forma asíncrona; si Redis
falla, se devuelve el catálogo disponible sin hacer depender la lectura de esa
escritura. El TTL puede hacer desaparecer la clave sin que se pierdan los datos
de PostgreSQL. La persistencia de Redis ante reinicios depende de cómo se haya
configurado la instancia, no de RedisInsight.

El fallback captura tambien los errores de acceso a datos y los fallos al abrir
o confirmar la transaccion de lectura de PostgreSQL, fuera del metodo del servicio.
En esos casos continua con Gestopago; no se ocultan errores de programacion.

El cron utiliza un ejecutor separado y crea una carpeta `run-*` con `products.xml`
por ejecución. Es un snapshot serializado del modelo recibido, no un archivo
permanente ni necesariamente el XML original byte a byte. La carpeta de esa
ejecución se limpia en `finally`, también cuando se omite la actualización.
Se compara la cantidad de IDs de producto únicos: solo se reemplaza PostgreSQL
si la nueva cantidad es estrictamente mayor y no está vacía. El reemplazo es
transaccional; después del commit se intenta sobrescribir Redis. Si Redis falla,
el cambio confirmado en PostgreSQL no se revierte.

Esta regla de cantidad conserva el requisito acordado, pero no detecta cambios
de precio o descripción si el tamaño del catálogo permanece igual. El cron
necesita que el proceso esté encendido a la hora programada. El bloqueo de
actualización es local a la instancia; múltiples réplicas requieren coordinación
distribuida antes de habilitar la misma sincronización en todas ellas.

### Respuestas de error del catálogo

| Código interno | Situación | HTTP |
| --- | --- | --- |
| `1` | Gestopago no disponible, error de autenticación externo, respuesta no exitosa, conexión o timeout | `502` |
| `2` | Conversión/serialización, persistencia o procesamiento interno del catálogo | `500` |

El contrato actual usa los códigos `1` y `2`; no hay un código `3` independiente.
La respuesta incluye `timestamp`, `code`, `message` y `path`. Un error de Redis
o de lectura de PostgreSQL activa el siguiente nivel de fallback; si hay datos
disponibles, no tiene por qué producir un error al cliente. Los rechazos de
autenticación de nuestra API se manejan por separado como HTTP 401/403.

## Clientes personas físicas

El módulo de clientes usa las tablas `clientes`, `domicilios` y `cuentas` (migraciones
Flyway V6 a V16, junto con el historial previo). Se habilita con `app.database.enabled=true` y requiere un JWT
válido de ejecutivo para llamar a `/api/clientes`.

El ID del cliente es numérico (`Long` / `BIGINT`) y PostgreSQL lo genera automáticamente.
El registro devuelve, por ejemplo, `"id": 1`. Las consultas, actualización y baja
reciben el identificador en el cuerpo JSON; no se usa `/api/clientes/{id}`. La
migración V9 conserva los clientes anteriores y convierte las referencias de
domicilios y cuentas al nuevo ID. Los IDs internos de domicilio y cuenta mantienen
su tipo UUID.

- `POST /api/clientes`: valida los datos y guarda cliente, domicilio, cuenta y usuario
  en una sola transacción. La cuenta se crea activa con el saldo inicial de
  `cliente.cuenta.saldo-inicial` (por defecto `0.00`).
- `POST /api/clientes/consulta`: busca clientes de forma paginada con filtros JSON;
  es una operación para ejecutivos.
- `PUT /api/clientes`: actualiza los datos modificables usando `id` y `datos` en el
  cuerpo. CURP, RFC y número de cuenta no forman parte de los datos editables.
- `DELETE /api/clientes`: baja lógica con el ID en el cuerpo. Marca al cliente y sus
  cuentas como inactivos dentro de la misma transacción; no borra filas ni historial.

El registro verifica mayoría de edad, formatos de CURP/RFC/correo, teléfonos de
10 dígitos, código postal de 5 dígitos, ingreso mensual positivo y nombres con
letras y espacios. CURP, RFC, correo y número de cuenta tienen restricciones únicas
en PostgreSQL. La migración V8 también impide una cuenta activa con cliente inactivo.
Las consultas históricas incluyen clientes desactivados, identificados por `activo`.

### Consultas de clientes y cuentas

Todos los endpoints de negocio usan el prefijo `/api`. Las consultas de clientes,
cuentas y usuarios reciben filtros en cuerpos JSON, no en parámetros de ruta o
consulta. Los endpoints protegidos requieren un JWT y una sesión activa; las
operaciones administrativas requieren el rol `EJECUTIVO`. El cliente solo consulta
sus propias cuentas y el backend verifica la titularidad.

| Operación | Ruta actual | Acceso / uso |
| --- | --- | --- |
| Consultar clientes | `POST /api/clientes/consulta` | Ejecutivo; filtros y paginación en JSON |
| Consultar cuenta por número | `POST /api/cuentas/consulta` | Cliente consulta una cuenta propia; ejecutivo tiene acceso administrativo |
| Listar cuentas activas | `POST /api/cuentas/activas/consulta` | Consulta paginada |
| Consultar cuentas propias | `POST /api/cuentas/mias/consulta` | Cliente autenticado; el servidor obtiene su identidad de la sesión |
| Consultar saldo disponible | `POST /api/cuentas/saldo/consulta` | Cliente consulta cuentas propias; ejecutivo tiene acceso administrativo |

La búsqueda de clientes combina nombre, RFC y CURP parciales con OR; filtros como
ID, correo y número de cuenta se comparan de forma exacta. También admite estado,
rango de fechas de registro y paginación. Las fechas usan `AAAA-MM-DD`, días UTC e
incluyen todo el día final; un rango invertido o formato incorrecto devuelve HTTP
400. La paginación usa `pagina` y `tamanio` (20 por defecto, máximo 100), con orden
estable. Los filtros se aplican en SQL, no cargando toda la tabla en memoria.
La migracion V10 agrega un indice parcial para el listado de cuentas activas.

Los conflictos devuelven HTTP 409 y los codigos `DUPLICATE_CURP`,
`DUPLICATE_RFC`, `CLIENT_ALREADY_REGISTERED` (correo duplicado) o
`CLIENT_DATA_CONFLICT` (conflicto detectado por una restriccion de PostgreSQL).
Cliente y cuenta inexistentes devuelven HTTP 404 con `CLIENT_NOT_FOUND` y
`ACCOUNT_NOT_FOUND`. Los fallos de persistencia devuelven HTTP 503 sin detalles
internos. Las restricciones unicas siguen siendo la proteccion final ante
registros concurrentes.

Las relaciones son cliente-domicilio 1:1 y cliente-cuentas 1:N. El cliente usa
`BIGINT` autoincremental como PK; domicilio y cuenta usan UUID, con FK `cliente_id`.
CURP, RFC, correo y numero de cuenta son cadenas con restricciones unicas;
telefonos y codigo postal tambien son cadenas para conservar ceros iniciales.
Saldo e ingreso usan `NUMERIC(19,2)` / `BigDecimal`, nacimiento usa `DATE` /
`LocalDate`, y las fechas de auditoria usan `TIMESTAMPTZ` / `Instant`.
La actividad se representa con booleanos y la concurrencia con `@Version`.
Se mantiene Gradle porque Maven aparece como tecnologia sugerida, no obligatoria.

### Desactivacion de cuenta y baja del cliente

`PATCH /api/cuentas` permite al ejecutivo desactivar una cuenta enviando su número y
`cambios.activa=false`; conserva saldo, cliente, usuario y sesiones. Repetir la baja
es idempotente. `DELETE /api/cuentas` se conserva por compatibilidad, pero está
deprecado. Las consultas de cuenta y perfil no requieren que la cuenta esté activa.

`DELETE /api/clientes` desactiva al cliente, sus cuentas y su usuario, y revoca su
sesión. El login y los endpoints protegidos rechazan clientes inactivos, incluso
con un JWT anterior. La suspensión de una cuenta no bloquea el login.

### Pagos de servicios y recargas

El backend incluye pagos de servicios, recargas y tarjetas de regalo del catálogo
Gestopago; no implementa transferencias bancarias. Solo un `CLIENTE` puede operar
con sus propias cuentas. El flujo prepara la operación sin cobrar, la autoriza con
TOTP y permite consultar el estado; cancelar solo funciona antes de enviar el pago.
Una respuesta incierta no debe provocar un reintento de cobro.

Las rutas principales son `POST /api/pagos/segundo-factor/estado`,
`/segundo-factor/preparar`, `/segundo-factor/activar`, `/preparar`, `/autorizar`,
`/consulta` y `/cancelar`, bajo el prefijo `/api/pagos`. Los casos de conciliación
para ejecutivos habilitados usan `/api/pagos/conciliacion/*` y requieren doble
aprobación. El módulo de pagos y la conciliación están deshabilitados por defecto.
Consulta [la guía de pagos](docs/payment-flow.md) para los contratos, configuración,
estados y tratamiento de operaciones pendientes.

### Usuario de acceso automatico

`POST /api/clientes` ahora requiere tambien `contrasena` en el
cuerpo del registro. Primero crea cliente, domicilio y cuenta bancaria y despues
el usuario en `app_users`; todos forman parte de la misma transaccion.
Si cualquiera falla, se revierten las cuatro inserciones.
No se llama al registro de autenticacion independiente ni se crea otra cuenta
en un modelo paralelo: la cuenta bancaria se guarda solamente en `cuentas`.

El correo normalizado del cliente se usa como `email` e `identifier` del usuario.
La relacion `app_users.cliente_id` tiene FK y restriccion UNIQUE: un cliente no
puede tener dos usuarios. El usuario nace activo (`enabled=true`).
La contrasena tiene entre 8 y 72 caracteres, mayuscula, minuscula, numero y caracter especial;
tambien se comprueba el limite de 72 bytes UTF-8 de BCrypt. No se recorta ni
normaliza. Se almacena solamente su hash BCrypt con factor 12, nunca el texto
original, y no se devuelve ni la contrasena ni el hash en las respuestas.

Para ingresar se reutiliza `POST /api/auth/login`:

```json
{
  "email": "marco@example.com",
  "password": "TuContrasenaElegida1!"
}
```

En el POST de cliente, agrega `"contrasena": "TuContrasenaElegida1!"` a los
datos personales, de contacto, laborales y domicilio existentes.
No necesitas llamar despues a `/api/auth/register` para ese cliente.
Al actualizar el correo se sincroniza el usuario vinculado y se revoca la sesion;
al dar de baja al cliente se desactiva tambien su usuario y se revoca su sesion.
La invalidacion de Redis se realiza despues del commit. Si Redis falla, se
registra el error; PostgreSQL sigue siendo la fuente de verdad. El login y la
validacion de sesiones ya no autorizan mediante credenciales antiguas de Redis.
Si PostgreSQL no permite verificar el acceso, se devuelve HTTP 503, en lugar de
aceptar un usuario desactivado o una sesion revocada. El fallback del catalogo
de productos sigue siendo Redis, PostgreSQL y Gestopago; no se cambia ese flujo.

V11 conserva clientes y usuarios anteriores sin inventar contrasenas ni asociar
identidades por coincidencias de correo. Esos registros previos pueden seguir
sin vinculacion; la creacion automatica aplica a los nuevos registros de cliente.
Reinicia el backend con PostgreSQL habilitado para aplicar la migracion.

### Inicio de sesion y gestion del usuario

- `POST /api/auth/login`: correo y contrasena. Verifica el hash
  BCrypt, el usuario activo y, si esta asociado, el cliente activo. Devuelve
  `accessToken`, `tokenType`, `expiresAt` y el perfil `user`.
- `POST /api/usuarios/consulta`: consulta el perfil propio con nombre completo y
  correo exactos; la identidad de sesión limita el resultado.
- `POST /api/usuarios/busqueda`: búsqueda paginada exclusiva para ejecutivos.
- `PUT /api/usuarios/password`: cambia la contraseña del usuario autenticado,
  requiere la contraseña actual y revoca la sesión. Devuelve HTTP 204; se debe
  iniciar sesión de nuevo. No recibe un ID en la ruta.

El cuerpo de cambio incluye el correo del usuario y un objeto `credenciales` con
la contraseña actual y la nueva. El formato exacto está disponible en Swagger.

Los servicios protegidos requieren `Authorization: Bearer <accessToken>`.
El JWT se verifica por firma, emisor y expiracion; tambien se comprueba en
PostgreSQL que su sesion siga vigente. Un nuevo login reemplaza la sesion anterior.
Los bloqueos de escritura por usuario coordinan login, baja y cambio de contrasena.
La consulta de perfil solo devuelve el usuario de la sesión; un selector que no
corresponde devuelve 404. Un cliente que intente la búsqueda administrativa recibe
403.

En login se usa el mismo error HTTP 401 `AUTH_INVALID_CREDENTIALS` para correo
inexistente, contrasena incorrecta o usuario inactivo, evitando revelar cuentas.
El login limita solicitudes por IP (30 por minuto) y por correo normalizado
(5 intentos en 5 minutos). Un login exitoso reinicia el contador del correo,
pero no el de la IP; las solicitudes en curso tambien cuentan. Al superar un
limite devuelve 429 `AUTH_TOO_MANY_ATTEMPTS` con `Retry-After` en segundos,
sin revelar si el correo existe. El limite por IP se aplica antes de validar
el payload y utiliza la direccion de conexion, no un `X-Forwarded-For` arbitrario.

Estos valores son configurables con `LOGIN_IP_ATTEMPTS`, `LOGIN_IP_WINDOW`,
`LOGIN_ACCOUNT_ATTEMPTS` y `LOGIN_ACCOUNT_WINDOW`. Se conservan como maximo
`LOGIN_MAX_TRACKED_KEYS` claves (10000 por defecto), sin expulsar bloqueos vigentes.
El contador es local a cada instancia y se reinicia al reiniciar el backend:
varias instancias requieren un contador compartido y los proxies requieren
configuracion explicita de confianza. No es un bloqueo permanente de usuarios.

En gestion de usuarios se distinguen `AUTH_USER_NOT_FOUND` (404),
`AUTH_USER_INACTIVE` (403), `AUTH_INVALID_PASSWORD` (400) y
`AUTH_FORBIDDEN` (403). Un payload invalido devuelve 400 con errores por campo;
un correo duplicado en onboarding devuelve 409 `CLIENT_ALREADY_REGISTERED`.
Los fallos de persistencia o apertura de transaccion devuelven 503 sin detalles internos.

La tabla de usuarios existente conserva su nombre `app_users`: `email` representa
correo, `password_hash` la contrasena hasheada, `enabled` el booleano activo y
`created_at`/`updated_at` las fechas de auditoria. V12 agrega `updated_at`,
`version` para concurrencia optimista y un indice unico de correo normalizado.
Si hay duplicados historicos por mayusculas o espacios, Flyway se detiene:
no elimina usuarios automaticamente; esos datos deben corregirse antes de migrar.

No existe un endpoint de registro independiente en `/api/auth/register`: los nuevos
clientes se registran mediante `POST /api/clientes`.
Flutter permite al ejecutivo capturar el formulario completo y envia su JWT;
el alta no reemplaza la sesion del ejecutivo por la del cliente nuevo.
Los usuarios y las cuentas historicas (archivadas en `legacy_accounts` por V16 si existen) se conservan; no se migran
por coincidencia de correo ni se otorgan permisos de ejecutivo automaticamente.
Si un correo historico ya existe, se devuelve 409; usa un correo nuevo para el alta
escolar. Vincular identidades historicas requiere un proceso administrativo verificado,
no saltarse la unicidad ni borrar registros para resolver el conflicto.

Configura `auth.jwt.secret` con una llave privada de al menos 32 bytes en el
archivo local no versionado; sin ella se genera una llave temporal y reiniciar
el backend invalida los tokens. Con `app.database.enabled=false` solo quedan
publicas salud y documentacion; los servicios de negocio no se abren sin autenticacion.

Las pruebas cubren login correcto e incorrecto, inactividad, propiedad de usuario,
validacion de contrasenas, revocacion, fallos de PostgreSQL y rollback de cambios
de hash/sesion usando transacciones reales en H2. No sustituyen pruebas de carga.

### Primer ejecutivo y uso del registro en Flutter

V13 agrega el rol en PostgreSQL. Los usuarios existentes conservan el rol
`CLIENTE`; solo un ejecutivo puede registrar, consultar, actualizar o dar de baja
clientes y consultar cuentas. El rol y la sesion se verifican en PostgreSQL en
cada peticion: un JWT anterior no conserva permisos retirados. No se aceptan roles
desde el formulario ni se usa Redis como autoridad de autorizacion.

Para crear el primer ejecutivo, agrega temporalmente estas propiedades al archivo
local ignorado por Git: `src/main/resources/application-local.properties`.
Conserva en ese archivo tus conexiones existentes y el secreto JWT.

```properties
auth.bootstrap-executive.enabled=true
auth.bootstrap-executive.email=ejecutivo@tu-correo.com
auth.bootstrap-executive.password=REEMPLAZAR_CON_TU_CONTRASENA_SEGURA
```

El correo debe ser nuevo y la contrasena cumplir las mismas reglas del cliente;
el texto de ejemplo NO es una contrasena valida. No hay credenciales predeterminadas.
Arranca PostgreSQL y el backend con `.\gradlew.bat bootRun`.
Flyway aplica V13 y el inicializador crea un usuario ejecutivo con BCrypt, sin
inventar un cliente o cuenta bancaria. Si ya existe un ejecutivo activo, el
inicializador no cambia su contrasena. Si el correo pertenece a otro usuario, se
detiene: nunca eleva sus privilegios.

Despues del primer arranque exitoso, cambia `enabled=false` y elimina del archivo
las dos propiedades con credenciales de bootstrap. No publiques ese archivo ni
su contenido. Inicia sesion en Flutter con el correo y contrasena elegidos.

1. El ejecutivo ve **Registrar cliente** en Inicio.
2. Captura datos personales, contacto, domicilio, empleo y contrasena del cliente.
3. Flutter llama a `POST /api/clientes` con el JWT del ejecutivo; el backend valida,
   comprueba duplicados y guarda cliente, domicilio, cuenta activa y usuario activo
   en una transaccion.
4. El numero de cuenta unico y el saldo inicial los asigna el servidor, no Flutter.
   No es una CLABE real ni provisiona una cuenta en una institucion externa.
5. El cliente nuevo puede iniciar sesion con su correo. No recibe permisos de
   ejecutivo y solo puede consultar su perfil de usuario o cambiar su propia
   contrasena; el catalogo Gestopago sigue disponible con autenticacion.

La pantalla de Login ya no ofrece el registro basico anonimo. La sesion se guarda
con el perfil, rol y vencimiento en SQLite, sin contrasena ni JWT. El token se
almacena mediante `flutter_secure_storage`: cifrado respaldado por Android
Keystore y Keychain en iOS. Una referencia aleatoria vincula ambos almacenes;
las operaciones se serializan y una escritura fallida retira la sesion parcial.
La version 5 elimina sesiones locales antiguas que guardaban el token en texto
plano y exige iniciar sesion otra vez, sin modificar clientes ni cuentas del servidor.
Android excluye estos datos de las copias de seguridad. La API vuelve a comprobar
permisos, aunque alguien altere SQLite; debe usarse HTTPS fuera del entorno local.

### Manejo centralizado y verificacion

Las excepciones se manejan por capa: los servicios traducen errores de acceso a
datos y el advice controla tambien los fallos al abrir o confirmar transacciones.
Clientes/cuentas devuelven 400 por validacion, 404 por inexistencia, 409 por
duplicados o concurrencia, 503 por persistencia y 500 por un error inesperado.
Los mensajes y logs no incluyen passwords, hashes, tokens ni detalles SQL internos.

```powershell
# Backend, desde la raiz Java
.\gradlew.bat test --console=plain
# Flutter, desde mobile/gestopago_app
flutter analyze
flutter test
```

Las pruebas incluyen autorización ejecutivo/cliente, rutas con prefijo `/api`,
cambios de rol, bootstrap, respuestas de error, altas atómicas, BCrypt, sesiones,
pagos, TOTP, retenciones, conciliación y rollback. Las pruebas Flutter cubren
autenticación, registro, almacenamiento de sesión, validaciones y estados de pagos
pendientes; no sustituyen una comprobación interactiva en dispositivo.

Por defecto las pruebas transaccionales usan H2. Para repetirlas en PostgreSQL,
puedes definir `ONBOARDING_TEST_JDBC_URL`, `ONBOARDING_TEST_DB_USER` y
`ONBOARDING_TEST_DB_PASSWORD` apuntando a una instancia **exclusiva de pruebas**.
Cada test aplica Flyway en un schema temporal `sf_test_*`, valida el mapeo Hibernate
y elimina solamente ese schema al terminar. El usuario necesita permiso para crear
schemas. No apuntar esta verificacion a produccion.
La verificacion local se realizo con PostgreSQL 18: Flyway aplica las migraciones,
pero advierte que esta version del motor es mas reciente que su soporte probado.
Actualizar Flyway/Spring Boot requiere una revision de dependencias separada;
no se cambiaron versiones de librerias como parte de este ajuste funcional.

### Cierre de sesion y recuperacion de errores

`POST /api/auth/logout` requiere el JWT del usuario y
devuelve 204. Revoca en PostgreSQL solo la sesion identificada por ese JWT, no una
sesion nueva creada por otro login. Redis se invalida despues del commit con una
comparacion y borrado atomicos; una falla del cache no vuelve a autorizar el token.
La firma JWT por si sola no basta: se sigue comprobando la sesion vigente.

Flutter solicita esa revocacion antes de borrar el perfil SQLite y el token del
almacen seguro, y bloquea envios repetidos.
Si la red falla, borra la sesion local y advierte que no pudo confirmar el cierre
remoto. Si SQLite falla al restaurar o borrar, muestra un aviso y vuelve al login,
sin dejar una carga indefinida. No se afirma que el cierre remoto haya ocurrido
si el servidor no pudo confirmarlo.

Registro y actualizacion comparten normalizacion y reglas de nombres/edad;
se conservan las validaciones de los DTOs y las restricciones de PostgreSQL.
Flutter comparte el transporte HTTP y traduce timeouts, errores de red, JSON
incorrecto y respuestas HTML de proxies a mensajes controlados.

La autenticacion opcional de Gestopago conserva los parametros de URL que exige
el [contrato del proveedor](https://documenter.getpostman.com/view/19876210/Uz5MFtdn).
Ese cliente solo admite HTTPS, no registra solicitudes/respuestas HTTP y sus
errores locales no imprimen URL, cuerpo ni trazas con secretos, incluso con
`loggerLevel=FULL`. Esto no controla los logs del proveedor o de proxies externos:
no compartir URLs de autenticacion ni activar trazas de transporte con credenciales.

### Indices y restricciones de base de datos (V14)

V14 es una migracion nueva y transaccional: no modifica migraciones ya aplicadas,
identificadores, hashes ni registros. Conserva las PK, FK y restricciones UNIQUE
de CURP, RFC, correos, cliente-usuario y numero de cuenta. Saldo e ingreso mantienen
`NUMERIC(19,2)`; telefonos y codigo postal siguen como cadenas para conservar ceros.
Reducir la longitud maxima de un `VARCHAR` no reduce el almacenamiento del mismo
valor: los limites deben responder al dominio, no cambiarse arbitrariamente.

- El listado de clientes usa `(fecha_creacion DESC, id DESC)`, alineado con la
  paginacion estable y las consultas por rango de fechas.
- Un indice parcial con ese orden cubre clientes inactivos. Se espera que la
  mayoria sean activos; ese listado utiliza el indice general. Si cambia la
  distribucion, reevaluar con estadisticas y `EXPLAIN`, no duplicar indices a ciegas.
- Se conserva `(cliente_id, activa)` en cuentas: sirve para buscar por cliente
  y por cliente/actividad. Se retira el indice simple redundante de `cliente_id`.
- Se retira el indice simple de `app_users.email` duplicado por UNIQUE; se conserva
  ademas la unicidad del correo normalizado. El indice de bootstrap se limita
  a ejecutivos activos.
- Se retiran los indices de apellidos y codigo postal porque los repositorios
  actuales no tienen esas busquedas. Si se incorporan, evaluar indices adecuados
  a sus filtros; los indices tambien consumen espacio y trabajo en cada escritura.
- Los CHECK rechazan `NaN` y valores no finitos: ingreso debe ser mayor a cero
  y saldo no negativo. El precio externo conserva nulabilidad y reglas de signo.
  Si existen datos incompatibles, la migracion falla y revierte sus cambios;
  no borra ni corrige informacion financiera automaticamente.

Flyway aplica V14 al arrancar el backend con PostgreSQL habilitado. La migracion
tiene limites de espera de bloqueo y ejecucion; un fallo debe revisarse antes de
reintentar, nunca borrar el historial de Flyway para forzarla. Antes de aplicar
una migracion, realizar un respaldo y coordinarla con los procesos que usan la base.
Los respaldos locales en `.local-db-backups/` estan ignorados por Git y pueden
contener datos personales y hashes: no compartirlos ni subirlos al repositorio.

`DatabaseSchemaMigrationTest` se habilita con `ONBOARDING_TEST_JDBC_URL` en una
instancia PostgreSQL exclusiva de pruebas. Crea y elimina schemas `schema_test_*`.
Cubre instalacion nueva, upgrade desde V13 sin cambios en los datos, unicidad y FK,
importes invalidos y rollback. Tambien comprueba planes con 30 mil clientes/cuentas
sinteticos y compara el espacio ocupado por los indices. Es una verificacion de
esquema y consultas representativas.

### Contrato de longitudes y fechas

Detalle por tabla y justificacion de cada limite: [docs/database-lengths.md](docs/database-lengths.md).

Los limites del alta y actualizacion de clientes se comparten entre validaciones
Java, entidades, columnas y formulario Flutter. Nombres/apellidos: 50; correo del
cliente: 100; CURP: exactamente 18; RFC: 12 o 13; telefonos: exactamente 10 digitos;
codigo postal: exactamente 5. Calle, colonia, municipio, estado y ocupacion admiten
100; empresa 150; numeros exterior/interior 20; pais/nacionalidad 60. Los limites
sin longitud oficial son decisiones del contrato local, no maximos universales.
La cuenta generada tiene 24 caracteres (`CTA-` y 20 caracteres hexadecimales).

V16 retira el CRUD antiguo de personas y el modelo antiguo de cuentas: ya no
hay entidades, repositorios ni endpoints para esas estructuras. Las tablas
`personas` y `accounts` se eliminan solo si estan vacias; si tienen datos, se
renombran a `legacy_personas` y `legacy_accounts` sin perder registros ni FK.
Estas tablas de archivo no son parte del flujo actual ni se exponen por API.
V1 a V15 se conservan intactas para mantener el historial de Flyway.

El usuario de acceso conserva `email`/`identifier` de 254 para el contrato de
acceso, mientras `full_name` pasa a 203: cuatro componentes de 50 mas tres espacios,
no el limite de un solo nombre. El alta actual sigue limitando el correo
del cliente a 100. Login Java/Flutter admite correo de hasta 254 y contrasena de
hasta 72 caracteres; no recorta contrasenas ni aplica las reglas de fuerza del
alta a credenciales previas. La validacion final de autenticacion es del backend.
El hash del `BCryptPasswordEncoder` actual se almacena en `password_hash` (60),
no la contrasena original; un futuro cambio de algoritmo requiere revisar esa columna.
Sexo (10), estado civil (11) y rol (9) corresponden al valor mas largo de sus enums.
La cuenta archivada `legacy_accounts.account_number` admite 20 (ACC- y 16 caracteres);
no se confunde con `cuentas.numero_cuenta`, que admite 24 (CTA- y 20 caracteres).

Los limites de nombres externos Gestopago (200/250), referencias externas y
tokens se conservan; no se truncan valores para hacerlos caber ni se deducen
maximos del tamano observado del catalogo. Hace falta un contrato de longitudes
del proveedor antes de reducirlos. La referencia facial opcional (255) esta
destinada a un identificador, no a almacenar una imagen o plantilla biometrica;
su longitud definitiva depende de la integracion futura.

Nacimiento usa `LocalDate` y `DATE`. Alta/actualizacion solo aceptan texto JSON
`AAAA-MM-DD` con una fecha real, sin hora, zona, espacios, arrays ni timestamps
numericos. Un formato invalido devuelve HTTP 400, sin llegar a persistencia;
una fecha futura o menor de edad sigue siendo rechazada por las validaciones.
La respuesta tambien presenta nacimiento sin hora. Flutter usa un calendario
en lugar de escritura libre y formatea el dia seleccionado como `AAAA-MM-DD`,
sin convertir zonas horarias; cancelar conserva la fecha anterior. El selector
comparte el limite de mayoria de edad con la validacion. Las fechas de auditoria
y expiracion conservan fecha y hora:
`Instant`/`TIMESTAMPTZ`; los registros existentes Gestopago mantienen
`LocalDateTime`/`TIMESTAMP`, sin convertir zonas historicas por suposicion.

La prueba PostgreSQL `tiposLongitudesNulabilidadYPrecisionDeEntidadesCoincidenConPostgres`
compara las siete entidades funcionales con `information_schema`, incluyendo tipos, limites
VARCHAR, nullabilidad, precision/escala y tipos de FKs. Las pruebas JSON cubren
fechas con hora, formatos alternativos y dias invalidos; las de Flutter cubren
limites y que un login demasiado largo no envie peticiones.

## Revision de consultas

Las consultas siguen estas decisiones:

- Usar comprobaciones de existencia para detectar duplicados sin cargar entidades
  completas. Correo e identificador del usuario se verifican en una sola consulta;
  las restricciones unicas de PostgreSQL protegen tambien los registros concurrentes.
- Recuperar el usuario una sola vez durante el login. `Optional` representa una
  posible ausencia; no hace mas lenta la consulta por si mismo.
- Buscar CURP, RFC, correo y numero de cuenta por coincidencia exacta, no por
  coincidencias parciales que podrian devolver varios registros.
- Paginar clientes y cuentas. El listado de clientes tiene un limite de 100 y
  orden determinista; sus domicilios y cuentas se cargan por lotes de IDs para
  evitar una consulta adicional por cada cliente.
- Reutilizar los indices de unicidad, correo normalizado, fechas y relaciones,
  sin agregar indices redundantes.

Las pruebas de servicios y repositorios verifican estas consultas. Las pruebas
PostgreSQL de `DatabaseSchemaMigrationTest` revisan los planes con 30 000 registros
sinteticos en schemas aislados y requieren las variables de conexion de pruebas;
sin ellas se omiten. Esta comprobacion no garantiza un tiempo de respuesta bajo carga.
