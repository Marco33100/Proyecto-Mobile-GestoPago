# Sesión del cliente web (cookie HttpOnly + CSRF)

La app móvil y la app web comparten el mismo login y el mismo JWT, pero lo transportan distinto.

| | Móvil | Web |
|---|---|---|
| Login | `POST /api/auth/login` | `POST /api/auth/login` con `X-Auth-Mode: cookie` |
| Dónde queda el JWT | Cuerpo de la respuesta → `flutter_secure_storage` | Cookie `GP_SESSION` (`HttpOnly`, `Secure`, `Path=/api`); el cuerpo no trae `accessToken` |
| Cómo se envía | `Authorization: Bearer <jwt>` | El navegador envía la cookie (`withCredentials`) |
| CSRF | No aplica | Obligatorio en POST/PUT/PATCH/DELETE: encabezado `X-XSRF-TOKEN` |
| Restaurar sesión | `GET /api/auth/sesion` con Bearer | `GET /api/auth/sesion` con la cookie y luego `GET /api/auth/csrf` |
| Logout | Revoca la sesión en el servidor | Igual; además expira `GP_SESSION` y `GP_XSRF` |

Si una petición trae `Authorization`, ese encabezado tiene prioridad y no se exige CSRF: el navegador no lo agrega solo, así que no hay riesgo de CSRF. La revocación por logout, el reemplazo de la sesión y la validación del rol funcionan igual en ambos modos.

## Por qué

En web, `flutter_secure_storage` termina en el almacenamiento del navegador, donde cualquier script inyectado (XSS) podría leer el JWT. Una cookie `HttpOnly` no es legible desde JavaScript. Como el navegador la envía automáticamente, se agrega un token CSRF (patrón *double submit* con la cookie `GP_XSRF`, también `HttpOnly`) que la app obtiene en `GET /api/auth/csrf` y guarda solo en memoria.

## Configuración

| Variable | Default | Uso |
|---|---|---|
| `WEB_ALLOWED_ORIGINS` | vacío (sin CORS) | Orígenes de la app web, separados por coma. Admite patrones: `http://localhost:[*]` |
| `WEB_SESSION_COOKIE_SECURE` | `true` | Solo se desactiva para pruebas sin HTTPS fuera de `localhost` |
| `WEB_SESSION_COOKIE_SAME_SITE` | `Lax` | `Lax` si la web y la API comparten sitio; `None` si están en sitios distintos |

Desarrollo local: agregar a `application-local.properties`

```properties
app.web-session.allowed-origins=http://localhost:[*],http://127.0.0.1:[*]
```

y ejecutar `flutter run -d chrome --dart-define=API_BASE_URL=http://localhost:8080`. Chrome y Firefox aceptan cookies `Secure` en `http://localhost`.

## Despliegue

- **Recomendado:** servir la web y la API en el mismo sitio (por ejemplo `app.midominio.com` y `api.midominio.com`) con `SameSite=Lax`.
- `*.onrender.com` está en la Public Suffix List: dos servicios de Render son **sitios distintos**. En ese caso se necesita `WEB_SESSION_COOKIE_SAME_SITE=None`, y algunos navegadores (Safari) bloquean las cookies de terceros, así que el login web podría fallar. Por eso conviene un dominio propio.
