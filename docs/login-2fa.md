# Inicio de sesión con doble factor (TOTP)

Clientes y ejecutivos inician sesión en dos pasos. Se usa la **misma app autenticadora** que autoriza los pagos: un solo autenticador por usuario (tabla `payment_second_factors`), con sus códigos de recuperación, el límite de 5 intentos y el bloqueo de 5 minutos.

## Flujo

1. `POST /api/auth/login` `{email, password}`
   - Verifica la contraseña. **No emite sesión.**
   - Responde `{desafio, segundoFactorConfigurado, venceEn, configuracion?}`.
   - `desafio`: JWT firmado con `purpose=login-2fa`, válido 5 minutos. No sirve como sesión: `JwtTokenService.validate` lo rechaza.
   - Sin autenticador: `configuracion = {secreto, uri}` (otpauth) y se guarda una clave pendiente ligada a ese desafío.
2. `POST /api/auth/login/verificar` `{desafio, codigo}` o `{desafio, codigoRecuperacion}`
   - Con autenticador: valida el código (sin repetir el mismo paso de 30 s) o consume un código de recuperación.
   - Sin autenticador: valida el código contra la clave pendiente **de ese mismo desafío**, la activa y devuelve 10 `codigosRecuperacion` (se muestran una sola vez).
   - Emite la sesión igual que antes. Con `X-Auth-Mode: cookie` (web) el JWT va en la cookie `HttpOnly`.

Un usuario sin autenticador lo vincula durante el inicio de sesión: nadie entra sin segundo factor.

## Una sola sesión activa (web o móvil)

Cada usuario tiene una sola sesión vigente (`user_sessions`, una fila por usuario, con su `plataforma`: `WEB` si el login llegó con `X-Auth-Mode: cookie`, `MOVIL` si no).

1. Si en el paso 2 el código es correcto y ya hay otra sesión vigente, el servidor responde **409** sin consumir el código:
   `{codigo: 2, mensaje: "Ya tienes una sesion abierta en la app movil…", datos: {plataforma, iniciadaEn}}`.
   El aviso solo aparece con un código válido; con un código incorrecto la respuesta es el 401 normal y cuenta como intento.
2. La app pregunta "¿Cerrar la otra sesión y entrar?". Si el usuario acepta, reenvía el **mismo código** con `cerrarSesionAnterior: true` (booleano JSON estricto); el servidor reemplaza la sesión y emite la nueva.
3. La sesión anterior deja de valer de inmediato. Su siguiente petición recibe **401** con `datos.plataforma` de la sesión nueva, y la app muestra "Tu sesión se cerró porque iniciaste sesión en la plataforma web". La app revisa `GET /api/auth/sesion` cada 30 s y al volver a primer plano, así que el aviso llega sin que el usuario toque nada.

Cerrar sesión (`/logout`) o dejar que expire libera la cuenta: el siguiente login ya no muestra el aviso.

## Errores

| Situación | HTTP | Mensaje |
|---|---|---|
| Contraseña incorrecta o usuario inexistente | 401 | "El correo o la contrasena son incorrectos" |
| Código incorrecto, repetido o desafío vencido | 401 | "El codigo no es correcto o la verificacion vencio…" |
| 5 códigos fallidos | 429 + `Retry-After` | Bloqueo de 5 minutos (compartido con pagos) |
| Código correcto, pero hay otra sesión abierta | 409 | "Ya tienes una sesion abierta en …" (+ `datos.plataforma`) |
| Sesión reemplazada desde otro dispositivo | 401 | "Tu sesion se cerro porque iniciaste sesion en …" (+ `datos.plataforma`) |
| Falta `GESTOPAGO_TOKEN_STORAGE_KEY` | 503 | El inicio de sesión no está disponible |

Los intentos fallidos se guardan fuera de la transacción que falla, igual que en pagos, para que no se pierdan.

## Configuración

- `GESTOPAGO_TOKEN_STORAGE_KEY` cifra las claves TOTP. **Es obligatoria en Render**: `RenderStartupValidator` detiene el arranque sin ella, para que un despliegue mal configurado falle visiblemente y no deje a todos fuera.
- Cambiar esa clave invalida los autenticadores existentes. Para rotarla sin perderlos, usa `GESTOPAGO_TOKEN_PREVIOUS_STORAGE_KEY`.

## Primer inicio de sesión tras el cambio

Todos los usuarios, incluido el ejecutivo inicial, deberán vincular su app autenticadora la próxima vez que entren. Los clientes que ya tenían el autenticador de pagos lo usan directamente.
