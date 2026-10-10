# Backend en Render

Se publica solamente la API Java. Flutter no se compila ni se incluye en la imagen.
El backend, PostgreSQL y la cache son servicios separados: `localhost` en Render
no es tu computadora. La instalacion nueva no copia usuarios, cuentas ni productos
de tu base local. Usar datos ficticios para la demostracion.

## Archivos preparados

- `Dockerfile`: compila el `bootJar` con el wrapper Gradle del proyecto y Java 21;
  la etapa final usa JRE 21 y un usuario sin privilegios de root. El codigo mantiene
  compatibilidad Java 17. No se ejecutan tests durante la construccion de la imagen;
  las verificaciones se hacen antes de publicar.
- `.dockerignore`: excluye configuracion local, secretos, respaldos, Flutter,
  archivos generados y evidencias del contexto Docker.
- `application-render.properties`: activa PostgreSQL, usa `PORT`, escucha en
  `0.0.0.0` y configura conexiones por variables de entorno.
- `RenderStartupValidator`: rechaza claves JWT vacias/cortas y conexiones invalidas
  sin mostrar sus valores secretos en el mensaje.

La imagen contiene unicamente el JAR y el runtime Java; las credenciales se
proporcionan en **Environment** de Render, nunca en `Dockerfile`, `ARG`, commits
o capturas. `JAVA_TOOL_OPTIONS` reserva por defecto hasta el 60% de la memoria
del contenedor para el heap; esto no garantiza capacidad para cualquier carga.

## 1. Crear PostgreSQL

En el proyecto de Render: **New > Postgres**. Elegir nombre, base, usuario, region
y plan. Para una demostracion se puede seleccionar **Free**, si esta disponible.
Crear una base **nueva y vacia** y esperar a que aparezca disponible.

Conservar sus datos de conexion: **Hostname interno**, **Port**, **Database**,
**Username** y **Password**. No pegarlos en el chat ni en el repositorio.

El backend requiere una URL JDBC, no la URL `postgresql://usuario:password@...`
que Render muestra completa. Armarla con host, puerto y base, dejando las
credenciales en sus variables separadas:

```text
jdbc:postgresql://HOST_INTERNO:5432/NOMBRE_BASE?sslmode=require
```

Los nombres en mayusculas son marcadores: reemplazarlos, no copiarlos literalmente.
Render permite TLS en conexiones internas con `sslmode=require`; las conexiones
externas necesitan configuracion de acceso y no son la opcion para este backend.
[Conexion PostgreSQL en Render](https://render.com/docs/postgresql-creating-connecting).

En **Info > Networking**, revisar las reglas de entrada. Si solo se conectara
el backend por la red interna, quitar la regla externa `0.0.0.0/0` y guardar
la lista vacia. Esto desactiva el acceso externo, no la conexion interna desde
servicios de la misma region. Si se necesita pgAdmin desde una computadora,
permitir solo su IP publica (`IP/32`) en vez de abrirlo a todo Internet.

## 2. Crear la cache

**New > Key Value**, misma region que PostgreSQL y el backend. Elegir plan;
para cache puede utilizarse **Free** y politica de expulsion `allkeys-lru`.
Copiar la **Internal URL** del menu Connect. No habilitar acceso externo para
esta demostracion. Si se exige autenticacion interna, usar la URL con credenciales.

Key Value es compatible con los clientes Redis del proyecto. El backend admite
`redis://` o `rediss://` mediante `REDIS_URL`, sin cambiar el DTO ni el fallback.
[Documentacion Key Value](https://render.com/docs/key-value).

## 3. Crear el Web Service

**New > Web Service > conectar GitHub** y seleccionar
`Marco33100/Proyecto-Mobile-GestoPago`.

| Opcion | Valor |
| --- | --- |
| Branch | `main`, despues de integrar y subir los cambios verificados de `develop` |
| Language / Runtime | `Docker` |
| Root Directory | Vacio: el Dockerfile esta en la raiz del repositorio |
| Dockerfile Path | `./Dockerfile` |
| Docker Build Context | `.` |
| Region | La misma que los dos servicios de datos |
| Health Check Path | `/actuator/health` |
| Docker Command | Vacio: utilizar el ENTRYPOINT de la imagen |

No configurar comandos Maven ni `bootRun`: el contenedor ejecuta el JAR compilado.
Elegir el plan antes de crear el servicio y revisar los costos que muestre Render.
[Docker en Render](https://render.com/docs/docker).

## 4. Variables de entorno

Configurar antes del primer despliegue:

| Variable | Valor |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `render` (tambien es el predeterminado de la imagen) |
| `SPRING_DATASOURCE_URL` | URL JDBC de la base nueva, como se describe arriba |
| `SPRING_DATASOURCE_USERNAME` | Usuario de PostgreSQL de Render |
| `SPRING_DATASOURCE_PASSWORD` | Password de PostgreSQL de Render |
| `REDIS_URL` | Internal URL de Key Value |
| `AUTH_JWT_SECRET` | Secreto aleatorio estable de al menos 32 bytes; recomendado 64 caracteres hexadecimales |
| `PRODUCT_SERVICE_API_KEY` | API key del proveedor, si el servicio la requiere |
| `GESTOPAGO_AUTH_ENABLED` | `true`: predeterminado del perfil Render; obtener/renovar el token automaticamente |
| `GESTOPAGO_AUTH_ID_DISTRIBUIDOR` | idDistribuidor asignado por Gestopago, el mismo que funciona en Postman |
| `GESTOPAGO_AUTH_CODIGO_DISPOSITIVO` | codigoDispositivo asignado por Gestopago |
| `GESTOPAGO_AUTH_PASSWORD` | Password de autenticacion del proveedor; NO es la contrasena del ejecutivo |

Render proporciona `PORT` y `RENDER_EXTERNAL_URL`. Swagger utiliza esta ultima
para enviar peticiones al backend publicado, no a `localhost`. Un dominio propio
puede configurarse mediante `API_DOCUMENTATION_SERVER_URL=https://tu-dominio`.

Generar el secreto JWT en un administrador de passwords o generador criptografico
y guardarlo directamente en Render. No regenerarlo en cada despliegue: invalidaria
los JWT emitidos. Este secreto no es el token de Gestopago.

Con renovacion automatica **no se necesita `PRODUCT_SERVICE_BEARER_TOKEN`**.
La peticion POST de autenticacion conserva los Params del contrato que se probo
en Postman, siempre por HTTPS, e incluye `X-API-Key` cuando esta configurado.
Las credenciales quedan unicamente en Environment; el cliente no registra URLs,
cabeceras, cuerpos ni causas Feign que puedan contener secretos.

El proveedor documenta una vigencia de 24 horas y prohíbe obtener un token por
cada consulta. Se reutiliza el token activo en memoria y PostgreSQL. Al necesitar
el catalogo se renueva si ya vencio, con margen de 30 segundos, o si la respuesta
es HTTP 401 o HTTP 403 con JSON `token=EXPIRED`. Solo en esos rechazos se permite
un reintento del GET; otros 403 no provocan renovacion. Las peticiones concurrentes
comparten la renovacion por instancia y un reinicio puede reutilizar el token
persistido que siga vigente. No depende de un cron ejecutado durante la suspension.
Si llega un `expires_in` menor, se respeta esa vigencia mas corta.
[Contrato de autenticacion Gestopago](https://documenter.getpostman.com/view/19876210/Uz5MFtdn).

El modo manual sigue disponible: `GESTOPAGO_AUTH_ENABLED=false` y
`PRODUCT_SERVICE_BEARER_TOKEN=TOKEN_VIGENTE_SIN_PREFIJO_BEARER`. Ese modo exige
reemplazar el token al caducar; no es la opcion recomendada para una demostracion
en una fecha desconocida. El modo automatico nunca usa un token fijo como fallback
si falla la autenticacion. No se cambian los roles ni los JWT de usuarios.

El catalogo sigue el flujo Redis -> PostgreSQL -> proveedor; las pruebas del
backend no deben forzar consultas externas por cada usuario. Gestopago documenta
un maximo de tres consultas de catalogo por dia. La renovacion del token no elimina
esa restriccion ni garantiza disponibilidad del proveedor.

Para crear el primer ejecutivo en la base nueva, agregar temporalmente:

| Variable | Valor |
| --- | --- |
| `BOOTSTRAP_EXECUTIVE_ENABLED` | `true` |
| `BOOTSTRAP_EXECUTIVE_EMAIL` | Un correo nuevo para el ejecutivo de pruebas |
| `BOOTSTRAP_EXECUTIVE_PASSWORD` | Password elegido: minimo 8 caracteres, mayuscula, minuscula, numero y especial; maximo 72 bytes UTF-8 |

El inicializador crea el usuario con BCrypt sin promover clientes existentes.
Una vez creado, poner `BOOTSTRAP_EXECUTIVE_ENABLED=false` y **eliminar**
`BOOTSTRAP_EXECUTIVE_PASSWORD` de Environment. El usuario y su hash permanecen
en PostgreSQL. No compartir la contrasena ni los JWT en capturas.

## 5. Desplegar y comprobar

1. Crear el Web Service y revisar Logs. En una base vacia Flyway aplica solo
   `db/initial/V1__create_initial_schema.sql`; Hibernate no crea tablas por su cuenta.
2. Esperar a que el servicio figure **Live** y abrir su URL HTTPS.
3. Probar `/actuator/health`: debe devolver HTTP 200 sin detalles de conexiones.
4. Abrir `/swagger-ui/index.html` e iniciar sesion en `POST /api/auth/login`.
5. Colocar el `accessToken` en Authorize y probar el alta/consulta de un cliente
   ficticio y `GET /api/products` para comprobar tambien Gestopago.
6. Entregar al profesor la URL HTTPS de Swagger y las credenciales de prueba por
   un canal privado. Los endpoints protegidos mantienen JWT, sesion y roles.

La salud desactiva solamente el indicador Redis: una cache caida no debe impedir
atender peticiones que puedan resolverse con PostgreSQL. HTTP 200 en health no
sustituye comprobar el catalogo externo. Un fallo de PostgreSQL si afecta la salud.

Para una **restauracion** que ya contiene historial V1..V16, no usar la V1
consolidada. Establecer `SPRING_FLYWAY_LOCATIONS=classpath:db/migration` y revisar
el respaldo/historial antes de arrancar. No borrar `flyway_schema_history` ni
ejecutar `repair` para saltarse incompatibilidades. El despliegue nuevo no modifica
la base que sigue funcionando en tu computadora.

## Limites y operacion

- Free Web Service se suspende tras 15 minutos sin trafico y tarda en despertar.
  El cron interno no se ejecuta mientras la instancia duerme; no garantiza una
  sincronizacion diaria continua. Para eso se necesita una instancia siempre activa
  o un mecanismo programado separado, con su costo previamente aprobado.
- PostgreSQL Free vence 30 dias despues de crearse y no incluye respaldos
  automaticos. Exportar la base antes de su vencimiento si hay datos que conservar.
- Key Value Free puede perder la cache al reiniciar; PostgreSQL conserva el catalogo
  y el fallback vuelve a poblarla. Los snapshots XML son temporales en `/tmp`.
- Los limites de login son por instancia. Sin una politica verificada de proxies,
  la IP de conexion puede ser la de un proxy de Render y agrupar solicitudes:
  no se confia ciegamente en un `X-Forwarded-For` enviado por el cliente. El limite
  por correo sigue activo. No desactivar los limites para la demostracion.
- Un reinicio conserva los datos en PostgreSQL pero pierde los contadores locales.
  El plan gratuito sirve para una demostracion, no prueba disponibilidad o capacidad
  de un servicio financiero real.

[Limites de los planes gratuitos](https://render.com/docs/free).
