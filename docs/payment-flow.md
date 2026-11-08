# Pagos con segundo factor TOTP

## Alcance y seguridad

La proteccion del Bearer interno, la clave externa de almacenamiento, la migracion V20 y la coordinacion entre replicas se describen en [Seguridad del token de Gestopago](provider-token-security.md). Configurar `GESTOPAGO_TOKEN_STORAGE_KEY` antes de usar autenticacion automatica o registrar TOTP. El mismo secreto externo protege ambos almacenes con AAD distinto por proposito y usuario.

Extensión de la autenticación existente: solo CLIENTE puede preparar, autorizar y consultar pagos de sus propias cuentas. No se introduce otro login. La app usa el catálogo existente (Redis/PostgreSQL); el servidor vuelve a comprobar producto, servicio, tipo y precio en PostgreSQL. No se confía en el precio, titular ni tipo enviado por la app.

Se admiten los contratos documentados tipoFront 1 (recargas y compra de tarjetas de regalo), 2 y 4 (servicios; 4 verifica referencia antes de autorizar). Otros tipos, incluido 5, se rechazan antes de retener: falta un contrato verificable de sus campos. No se implementa canje de códigos promocionales ni una función nueva para abonar saldo.

No se cobra comisión adicional al cliente. La comisión que responda el proveedor queda en la evidencia cifrada como costo del negocio. El SALDO del proveedor no sustituye al saldo de la cuenta bancaria local.

## Configuración

Los pagos están deshabilitados por defecto. Configurar mediante variables del entorno, nunca confirmar secretos en Git:

| Variable | Uso |
| --- | --- |
| GESTOPAGO_PAYMENTS_ENABLED | `true` solo tras validar configuración y entorno de pruebas |
| GESTOPAGO_CRYPTO_SECRET_KEY | Clave AES de 32 bytes codificada en Base64 suministrada por el proveedor |
| GESTOPAGO_CRYPTO_IV | IV de 16 bytes en Base64 suministrado por el proveedor |
| GESTOPAGO_PAYMENT_UNIT | `083`, texto, unidad comercial/sucursal; no es el tipo de servicio |
| GESTOPAGO_PAYMENT_TIMEZONE | `America/Mexico_City`, valor inicial que debe confirmar el proveedor |

Se conserva la configuración existente del token, URL y API key del proveedor. El destino sendTx debe ser HTTPS. No se envían claves AES al móvil. La app requiere API_BASE_URL HTTPS en compilaciones release.

El cifrado hacia el proveedor utiliza `AES.AesEncryption`, con los metodos `AESCipher`, `encrypt`, `decrypt` y el enum `Mode` del ejemplo solicitado. `PagoCrypto` lee la clave y el IV desde properties y delega el cifrado CBC a esa clase. Se conserva UTF-8, Base64 y `AES/CBC/PKCS5Padding`. No se imprimen claves ni errores tecnicos; ante un fallo se lanza una excepcion en lugar de retornar null. El cifrado autenticado GCM de la evidencia almacenada se mantiene separado.

Flyway agrega `classpath:db/payments` a las ubicaciones existentes. V17 añade saldo_retenido y tablas payment_orders, payment_biometric_keys y payment_evidence. V18 añade huellas del catálogo/operación, seguimiento de duplicados del proveedor e índices de protección. V19 añade el identificador exclusivo de cada confirmación, contador de consultas y motivo de revisión. V20 protege el token persistido. V21 agrega payment_second_factors y expira solo las preparaciones biometricas anteriores; no toca saldos ni pagos ya enviados. La tabla historica payment_biometric_keys permanece para no reescribir migraciones. No modifica migraciones ya aplicadas. Respaldar y validar en PostgreSQL de pruebas antes de producción. Si ya existen cuentas con saldo negativo, la restricción de retenciones requiere revisar esos datos antes de migrar.

La evidencia y el payload persistido se cifran con AES-GCM y nonce aleatorio usando una clave derivada, separada por propósito, de la clave del proveedor. **No rotar ni perder la clave mientras haya registros que deban descifrarse**: esta versión no implementa un llavero histórico ni migración de claves. Respaldo y controles de acceso a la base siguen siendo necesarios.

## Endpoints

Todos reciben POST, JSON mediante DTO validado y Bearer de la sesión existente. No utilizan PathVariable ni RequestParam.

| Ruta bajo /api/pagos | Cuerpo | Resultado |
| --- | --- | --- |
| /segundo-factor/estado | `{}` | Indica si el cliente tiene TOTP activo; no revela el secreto |
| /segundo-factor/preparar | contrasena, codigoActual o codigoRecuperacion si ya esta activo | Nuevo secreto Base32 y URI para QR; vence en 5 minutos |
| /segundo-factor/activar | codigo | Activa tras verificar el primer TOTP; codigos de recuperacion una sola vez |
| /preparar | solicitudId, numeroCuenta, idProducto, idServicio, tipoFront, monto y teléfono/referencia según producto | Fija datos y UPC; no retiene ni cobra |
| /autorizar | pagoId, codigo | Consume TOTP, reserva saldo y hace un unico intento sendTx |
| /consulta | pagoId | Consulta propia sin reenviar el pago |
| /cancelar | pagoId | Expira solo PREPARADO; no cancela una operación ya enviada |

Ejemplo de preparación de un servicio (usar identificadores reales del catálogo):

```json
{
  "solicitudId": "894e31c9-4ada-47d5-b998-3ace383b769e",
  "numeroCuenta": "00000000000000000038",
  "idProducto": 123,
  "idServicio": 45,
  "tipoFront": 2,
  "monto": "100.00",
  "referencia": "REFERENCIA-DEL-RECIBO"
}
```

`solicitudId` lo genera la app con UUID v4 aleatorio antes de preparar y lo conserva en almacenamiento seguro. La misma intención no admite cambiar importe ni destino. El backend genera una sola vez UPC alfanumérico seguro de 10 caracteres y horaLocal `yyyyMMddHHmmss` (14 dígitos, no epoch en milisegundos). La cuenta local no se envía al proveedor.

La preparación y el registro TOTP usan GenericResponse existente: codigo entero 1 y datos. El resultado final de autorización/consulta usa códigos **de texto**, distintos del contrato general:

```json
{"codigo":"01","mensaje":"Pago realizado correctamente","datos":{"pagoId":"...","estado":"APLICADO","monto":100.00,"comisionCliente":0}}
```

```json
{"codigo":"02","mensaje":"Servicio no Disponible","datos":{"pagoId":"...","estado":"PENDIENTE","aviso":"Pago pendiente de confirmacion. No repitas la operacion; el importe sigue retenido."}}
```

```json
{"codigo":"02","mensaje":"Servicio no Disponible"}
```

Los ejemplos de datos están abreviados; la respuesta incluye concepto, cuenta, UPC, autorización y comprobante cuando existen. **02 no significa necesariamente rechazo**: comprobar estado y aviso. Los errores conservan HTTP apropiado (400, 401, 403, 404, 409, 429 o 503). No hay reenvío automático ante HTTP, timeout o token vencido. Los comprobantes no deben cachearse. El código real del proveedor se conserva internamente, sin sustituirlo por 02 en la auditoría.

## Retención y conciliación

Saldo contable permanece en `saldo`; disponible = saldo - saldo_retenido. Preparar valida pero no reserva. Autorizar vuelve a validar propietario, usuario activo, cuenta activa y disponible, bloquea la cuenta y registra atómicamente retención + ENVIADO antes de la red. Ninguna llamada al proveedor ocurre dentro de la transacción de base de datos.

- PREPARADO: reto válido 5 minutos; cancelar/expirar no cobra.
- ENVIADO/PENDIENTE: importe retenido. Un timeout no permite liberarlo ni iniciar otro cobro por reintento.
- APLICADO: sendTx 01 o confirmTx 06 con identificadores verificables y sin antecedente de duplicado de sendTx; descuenta exactamente una vez y libera la retención.
- RECHAZADO: envío comprobablemente no realizado o confirmTx 70 tras un intento ya finalizado; libera retención sin descontar saldo.
- REVISION: respuesta contradictoria, transacción duplicada en otra orden, proceso interrumpido sin cierre del envío o ventana de 24 h vencida. Conserva retención y requiere intervención operativa.

El worker recupera pagos de PostgreSQL, usa el mismo payload/UPC, espera al menos 62 segundos desde el envío y no repite sendTx. Utiliza una reserva temporal de trabajo para reducir confirmaciones concurrentes. No promete atomicidad distribuida ni exactamente una ejecución global del proveedor: la recuperación conservadora evita liberar dinero cuando el resultado es desconocido.

La reserva temporal de confirmación dura 120 segundos y tiene un UUID persistido. Una respuesta solo puede cambiar estado y saldo si todavía corresponde al intento vigente. Si otro proceso recuperó una reserva vencida, la respuesta del proceso anterior se archiva como obsoleta, sin sobrescribir la decisión nueva. Las respuestas tardías de pagos terminados también se conservan cifradas, sin ejecutar otra liquidación. El registro identifica el intento y si fue descartado. Nunca se imprime el cuerpo externo en los logs.

Ante una confirmación incierta, el worker registra el resultado desconocido y programa exclusivamente otra consulta: con la configuración inicial, espera 1, 2, 4, 8 y hasta 15 minutos. La espera queda en PostgreSQL, no depende de memoria ni de que sobreviva el proceso. No prolonga la ventana de 24 horas; al vencerla se conserva la retención y se registra `VENTANA_CONFIRMACION_VENCIDA`. Otros motivos internos son `ENVIO_SIN_CIERRE`, `RESPUESTA_INCONSISTENTE` y `DUPLICADO_NO_CONCILIADO`. Cambiar la configuración técnica de espera no habilita reenvíos de ventas.

Si Gestopago respondió pero falla el guardado local, la reserva y el estado ENVIADO previamente confirmados permanecen. La recuperación consulta al proveedor; no vuelve a ejecutar sendTx. Si falla la liquidación local, la transacción revierte conjuntamente saldo, retención, estado y evidencia. El trabajador vuelve a consultar después de expirar su reserva de trabajo. Una excepción de una operación no impide revisar el resto del lote.

**Conciliación con doble control**: los pagos en REVISION conservan la retención mientras no exista un resultado definitivo verificable. Un ejecutivo expresamente habilitado registra una propuesta y la evidencia del proveedor; otro ejecutivo habilitado revisa y aprueba. La propuesta por sí sola no modifica el saldo, y la aprobación, la resolución de la retención y la auditoría ocurren en una transacción. Una tarjeta aplicada sin PIN se informa como aplicada con comprobante pendiente; no comprarla otra vez.

Para investigar sin modificar dinero, un operador autorizado con acceso de lectura puede identificar los casos mediante:

```sql
SELECT id, upc, estado, motivo_revision, numero_confirmaciones, enviado_en
FROM payment_orders
WHERE estado = 'REVISION'
ORDER BY enviado_en;
```

El procedimiento conserva cifrados el folio, la evidencia y la decisión de los operadores. Estos deben comprobar la autenticidad y el resultado con el proveedor: el sistema no puede verificar por sí solo que el texto presentado sea auténtico. No interpretar un silencio, HTTP 500, timeout o vencimiento de ventana como autorización de devolución. Para declarar `NO_APLICADO_DEFINITIVO`, la evidencia debe confirmar tanto la no aplicación como que no puede aplicarse posteriormente; de otro modo se mantiene REVISION. La conciliación no reenvía sendTx.

La función está deshabilitada por defecto. Solo debe habilitarse tras designar al menos dos usuarios EJECUTIVO activos e independientes y acordar el procedimiento con el proveedor. La lista de UUID es una lista explícita de operadores, no identifica a los clientes. Ejemplo de configuración externa con marcadores que deben sustituirse:

```properties
gestopago.pagos.conciliacion.enabled=true
gestopago.pagos.conciliacion.operadores=UUID_EJECUTIVO_1,UUID_EJECUTIVO_2
gestopago.pagos.conciliacion.propuesta-ttl=1h
```

Los endpoints POST `/api/pagos/conciliacion/pendientes`, `/propuestas`, `/propuestas/consulta`, `/propuestas/aprobar` y `/propuestas/rechazar` reciben DTO JSON validados. La lista devuelve el UPC, importe y versión del caso para identificarlo; la propuesta requiere esos valores, el folio, la evidencia y el resultado. Un segundo operador aprueba solo si el pago sigue en REVISION con la misma versión, importe y UPC. `APLICADO` descuenta el monto retenido; `NO_APLICADO_DEFINITIVO` lo libera; `DUPLICADO_YA_CONTABILIZADO` solo libera si existe un pago local previo que coincide. Una propuesta vencida no puede aprobarse y debe rechazarse antes de crear otra. Rechazar una propuesta conserva la retención. Las propuestas y decisiones se auditan y las respuestas no se almacenan en caché; las consultas de lectura no generan un evento de auditoría específico.

La consulta de pendientes acepta `{"pagina":0,"tamanio":20}`. Ejemplo de estructura de una propuesta de aplicación; todos los valores son ilustrativos y deben sustituirse con los datos revisados del caso y del proveedor:

```json
{
  "propuestaId": "00000000-0000-4000-8000-000000000001",
  "pagoId": "00000000-0000-4000-8000-000000000002",
  "versionPago": 1,
  "upc": "ABC1234567",
  "monto": 20.00,
  "resultado": "APLICADO",
  "idTransaccion": "123456",
  "autorizacion": "654321",
  "folioProveedor": "FOLIO-DE-SOPORTE",
  "evidenciaProveedor": "Evidencia oficial verificada para este caso",
  "motivo": "Resultado confirmado con el proveedor"
}
```

Para `NO_APLICADO_DEFINITIVO` se omiten `idTransaccion` y `autorizacion`; los otros dos resultados los requieren. El segundo operador envía a `/propuestas/aprobar` el `propuestaId` y una `observacion` de su revisión. Nunca se acepta un importe distinto del registrado en el pago.

## Límites, cambios del catálogo y duplicados

Los productos de precio fijo deben coincidir exactamente con el precio del catálogo. Los productos de monto variable pueden utilizarse sin configurar mínimos o máximos por producto; el monto debe ser positivo y respetar el saldo disponible. Si se configuran límites opcionales, se aplican al preparar y autorizar; una configuración incoherente devuelve HTTP 503 y un monto fuera del rango devuelve HTTP 400. Los límites incluyen ambos extremos. La ausencia de límites confirmados significa que el backend no puede asegurar que el importe cumpla restricciones adicionales del proveedor.

### Consulta de deuda y reglas de referencia (tipoFront 4)

`verifyReference` devuelve un resultado estructurado: VALIDA, INVALIDA o INCIERTA, codigo y saldo del servicio. Solo `01` con un saldo decimal verificable confirma la consulta. `02` rechaza la referencia con HTTP 400 sin retencion. Timeout, XML incompleto o fallo de transporte producen HTTP 503 y dejan la preparacion sin validar; se puede repetir `/preparar` con el mismo solicitudId para consultar nuevamente, nunca para reenviar una compra. Los XML se procesan sin entidades externas.

La primera consulta valida se conserva cifrada junto con su fecha; una respuesta concurrente posterior no reemplaza la deuda que se mostro al usuario. El plazo de autorizacion sigue siendo el de la preparacion (cinco minutos por defecto); consultar no lo renueva. Es una fotografia del proveedor, no una garantia de que la deuda externa permanezca igual hasta sendTx.

La respuesta agrega `saldoReferencia`, `consultaReferenciaEn`, `reglaImporteReferencia` y `autorizacionDisponible`. El saldo positivo significa deuda, cero significa sin deuda y negativo significa saldo a favor; no se interpreta como el saldo bancario del cliente. La app muestra estos datos y bloquea el boton si autorizacionDisponible=false. El servicio verifica la regla nuevamente antes de consumir TOTP y retener; el cliente no controla ese permiso ni el saldo consultado.

Las reglas requieren confirmacion expresa por producto y servicio:

```properties
gestopago.pagos.reglas-referencia-por-producto[ID_PRODUCTO].id-servicio=ID_SERVICIO
gestopago.pagos.reglas-referencia-por-producto[ID_PRODUCTO].modalidad=IMPORTE_EXACTO
```

Los identificadores son marcadores para sustituir por valores confirmados. `IMPORTE_EXACTO` exige monto igual a deuda. `PARCIAL_HASTA_SALDO` permite un monto positivo menor o igual a la deuda, sujeto tambien al rango minimo/maximo si existe. Sin politica configurada se informa `HASTA_SALDO_SIN_POLITICA` y se aplica el control propio de no superar la deuda; esto no confirma que el proveedor acepte abonos parciales. Con deuda cero/negativa o importe incompatible se bloquea el cobro con HTTP 409. Nunca se cambia automaticamente el monto solicitado.

V22 agrega las columnas de consulta cifrada, fecha y tipo de producto. Expira preparaciones antiguas pendientes de verificacion sin alterar saldos ni pagos enviados. Las preparaciones tipoFront 4 previamente validadas pero sin consulta conservada tampoco se autorizan: deben cancelarse y prepararse nuevamente. No se reescriben migraciones anteriores ni se configuran limites o politicas del proveedor por suposicion.

Plantilla de propiedades externas; sustituir todos los nombres en mayúsculas por datos confirmados antes de usarla:

```properties
gestopago.pagos.limites-por-producto[ID_PRODUCTO].id-servicio=ID_SERVICIO
gestopago.pagos.limites-por-producto[ID_PRODUCTO].minimo=MINIMO_CONFIRMADO
gestopago.pagos.limites-por-producto[ID_PRODUCTO].maximo=MAXIMO_CONFIRMADO
```

También puede suministrarse el mismo mapa anidado mediante `SPRING_APPLICATION_JSON`, conservando cualquier configuración existente. Los límites son del backend: no se aceptan en el Request DTO ni los fija el móvil. Si el producto usa denominaciones, incrementos u otras restricciones adicionales, deben incorporarse cuando el proveedor las confirme; un rango no las reemplaza.

El negocio también puede configurar `gestopago.pagos.maximo-por-operacion` y `gestopago.pagos.maximo-diario-por-cliente`. Son límites propios opcionales, no sustituyen los límites confirmados del proveedor. El límite diario utiliza el día de `zona-horaria` y cuenta operaciones enviadas, pendientes, en revisión y aplicadas; el bloqueo del usuario evita que dos solicitudes simultáneas superen el presupuesto. El backend conserva cifrada una fotografía de las reglas y su huella: si cambian antes de autorizar, expira la preparación y no retiene dinero. V23 expira preparaciones antiguas sin esa fotografía, sin alterar operaciones enviadas ni saldos.

Al autorizar se relee el producto con bloqueo de lectura en la transacción de reserva. Se comparan servicio, tipo, categoría, nombre, precio y reglas de referencia contra la huella de la preparación. Un cambio o eliminación expira esa preparación sin cobrar; el cliente debe preparar y autorizar de nuevo. Un simple refresco de `updated_at` o un cambio en la representación decimal equivalente no invalida la preparacion. No se mantiene ningún bloqueo de base de datos durante la llamada HTTP. Esto protege el catálogo local; no garantiza que el proveedor no cambie su catálogo remoto después de la validación.

La huella de operación combina servicio, producto, tipo, importe y destino canónico (teléfono o referencia con espacios extremos eliminados). No incluye la cuenta de cargo, el UPC ni `solicitudId`. Para el mismo usuario, una operación equivalente PREPARADA y vigente, ENVIADA, PENDIENTE o en REVISION bloquea nuevas intenciones con HTTP 409, incluso desde otra cuenta o instancia. Se serializa por usuario en PostgreSQL; un índice único parcial refuerza la exclusión de las operaciones enviadas/pendientes/en revisión. Las rechazadas, expiradas y exitosas no crean una ventana artificial de 5 minutos o 24 horas. El proveedor sigue siendo autoridad para repetir compras ya terminadas. No se equiparan automáticamente referencias que difieran en mayúsculas o importes, porque no conocemos esas reglas del proveedor.

`sendTx` con código 06 **no equivale a una nueva venta exitosa**. Se guardan sus identificadores anteriores y el antecedente de duplicado de forma persistente, junto a la evidencia cifrada:

- Si ID_TX y autorización coinciden con un pago local APLICADO del mismo usuario, destino, producto e importe, la nueva intención se rechaza y se libera exclusivamente su retención. El débito anterior no se modifica.
- Si no hay una correspondencia verificable, se conserva la retención y se confirma con el payload/UPC original. Un 06 de confirmación no convierte automáticamente ese duplicado externo desconocido en otro débito: pasa a REVISION.
- Un 70 de confirmación que establezca que la nueva intención no fue aplicada libera su retención. Ante respuesta ambigua no se libera ni se reenvía sendTx.

Al actualizar desde V17 no se puede reconstruir en SQL la huella de un payload cifrado. Las preparaciones sin huella de catálogo deben cancelarse o expirar y prepararse otra vez. Las operaciones anteriores abiertas sin huella de operación bloquean conservadoramente nuevas preparaciones del usuario hasta que se resuelvan; no se eliminan ni se cobran de nuevo durante la migración.

## Segundo factor TOTP

La autorizacion ya no usa biometria, claves nativas ni firmas ECDSA. Se usa TOTP de seis digitos, HMAC-SHA1 y periodos de 30 segundos, segun [RFC 6238](https://datatracker.ietf.org/doc/html/rfc6238). Se tolera como maximo un periodo anterior o siguiente. Se persiste el ultimo periodo consumido y se rechazan los ya usados, incluso entre pagos distintos e instancias.

Registrar exige sesion CLIENTE activa y contrasena actual. La configuracion pendiente pertenece a esa sesion y vence en cinco minutos; solo la primera confirmacion correcta activa el factor y entrega diez codigos de recuperacion aleatorios de 128 bits. El secreto compartido se almacena con AES-GCM y AAD por usuario; los codigos de recuperacion solo como hashes SHA-256 vinculados al usuario. Los DTO sensibles no revelan secretos en toString y las respuestas llevan Cache-Control: no-store.

Cinco fallos bloquean cinco minutos. Los intentos y el bloqueo se guardan bajo el mismo bloqueo de usuario usado para pagos. Los fallos se convierten en respuestas HTTP despues del commit, para no revertir el contador. Cambiar de autenticador exige contrasena mas un TOTP actual no consumido o un codigo de recuperacion de un uso; la contrasena sola no reemplaza un factor activo. El anterior sigue activo hasta confirmar el nuevo. La generacion del factor cambia al activar; los pagos preparados con la anterior ya no se pueden autorizar.

El codigo se consume dentro de la misma transaccion que retiene saldo y deja ENVIADO. Si esa transaccion falla, se revierten tambien el consumo y la retencion; si se confirma y la respuesta HTTP se pierde, repetir autorizar consulta el estado sin reenviar sendTx. El codigo usado para activar no puede pagar: esperar al siguiente. Los codigos de recuperacion permiten cambiar autenticador, no autorizar pagos directamente. No hay desactivacion ni bypass administrativo desde el cliente.

TOTP prueba posesion del secreto; no firma criptograficamente importe o destino. La API vincula la autorizacion al pagoId del usuario y a los datos inmutables revisados en la preparacion. TOTP no es resistente al phishing. Para produccion se necesita TLS y un autenticador protegido separado de las credenciales de acceso. Usar el mismo equipo para todo sirve para pruebas, pero no demuestra independencia frente a un equipo comprometido.

Ver [prueba sin telefono y ejemplos JSON](totp-testing.md). Los endpoints biometricos se retiraron, /autorizar recibe codigo en lugar de firma y PagoResponse deja de devolver reto. El consumidor Flutter incluido esta actualizado; otros consumidores deben actualizarse conjuntamente.

## Verificación y puesta en marcha

Pruebas backend cubren cifrado compatible, TOTP, XML hostil, retención, concurrencia, propiedad, importes, UPC estable, repetición de autorización, confirmación y revisión. PagoFlowPostgresTest, DatabaseSchemaMigrationTest y ClienteUsuarioTransactionTest habilitan PostgreSQL mediante ONBOARDING_TEST_JDBC_URL, ONBOARDING_TEST_DB_USER y ONBOARDING_TEST_DB_PASSWORD; usan esquemas desechables, no public. Las pruebas H2 no sustituyen las de PostgreSQL.

Flutter tiene pruebas del contrato 01/02, ausencia de reintentos, registro por QR/manual, codigo TOTP, recuperacion y estado pendiente. Probar el recorrido interactivo con un autenticador independiente o con el script local solo para demostracion. Desde Windows no puede compilarse iOS. Las pruebas automatizadas de conciliación usan proveedores simulados y no envían nuevas ventas reales.
