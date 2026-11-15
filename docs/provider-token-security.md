# Seguridad del token de Gestopago

El Bearer del proveedor permanece en el backend. Es distinto del JWT de sesion que recibe nuestra aplicacion movil. El modo manual utiliza `PRODUCT_SERVICE_BEARER_TOKEN` desde el entorno. El automatico obtiene el token con las credenciales externas existentes, lo conserva cifrado y reutiliza su vigencia; no se autentica por cada pago.

## Configuracion requerida antes de desplegar

Con autenticacion automatica, configurar **GESTOPAGO_TOKEN_STORAGE_KEY**: clave aleatoria independiente de 32 bytes, codificada en Base64, en el gestor de secretos o variables privadas del backend. No es la clave AES del payload de pagos ni el JWT secret. No tiene valor predeterminado; si falta o tiene formato incorrecto, el backend falla al iniciar en modo automatico. No compartirla con Flutter, capturas, repositorios, tickets o logs. No se generan ni se guardan claves reales en este repositorio.

Todas las replicas que comparten distribuidor/dispositivo deben usar las mismas claves y la misma base PostgreSQL. Los registros nuevos usan fecha de emision UTC; mantener relojes sincronizados. El cifrado AES-256-GCM genera un nonce aleatorio por escritura, autentica el contenido y lo vincula al distribuidor/dispositivo para impedir intercambiar ciphertext entre registros. El identificador de version/clave del sobre no contiene la clave.

## Migracion y rotacion

V20 invalida y reemplaza los tokens historicos sin cifrado por un marcador sin secreto. La siguiente solicitud obtiene uno nuevo bajo coordinacion PostgreSQL. Esta migracion no toca cuentas, saldos ni pagos. El CHECK impide guardar tokens activos sin el prefijo cifrado; la integridad criptografica se verifica en la aplicacion. Desplegar deteniendo las instancias antiguas antes de habilitar las nuevas: no mezclar versiones que escriban texto plano. Los backups anteriores pueden contener tokens antiguos y requieren sus propias politicas de acceso, cifrado y retencion.

Para rotar, conservar la clave anterior en **GESTOPAGO_TOKEN_PREVIOUS_STORAGE_KEY**, instalar la nueva en **GESTOPAGO_TOKEN_STORAGE_KEY** y reiniciar coordinadamente todas las replicas. No mezclar replicas con configuraciones distintas. Al leer un token con la clave anterior se recifra con la nueva bajo bloqueo, sin cambiar su fecha de emision ni extender su vigencia. Retirar la clave anterior solo cuando todos los tokens aun necesarios hayan sido recifrados o hayan expirado y sido renovados. Conservar claves en el gestor para restaurar backups que se necesiten. Una clave incorrecta o ciphertext alterado produce un error seguro: no se utiliza texto plano como fallback ni se provoca una cadena de autenticaciones para ocultar el fallo.

## Concurrencia, fallos y limites

**Al incorporar TOTP (V21), la clave de almacenamiento tambien protege secretos de segundo factor con AAD distinto.** Los secretos TOTP no caducan a las 24 horas. No basta esperar a que expire el Bearer para retirar la clave anterior: deben recifrarse tambien todos los factores activos, incluidos los de usuarios que no han vuelto a entrar. Se recifran al usar correctamente el factor; una rotacion masiva para usuarios inactivos requiere mantenimiento autorizado, que no se automatiza en estos endpoints. Conservar la clave anterior hasta completar esa migracion y considerar tambien los backups. No iniciar otra rotacion que pierda una clave aun necesaria.

Las consultas normales leen el token vigente desde PostgreSQL, sin una cache local que quede desactualizada tras una renovacion de otra replica. Para renovar o recifrar se usa una transaccion independiente y `pg_try_advisory_xact_lock`, cuya llave estable depende del distribuidor/dispositivo. Tambien cubre la primera insercion. Se relee el registro despues de adquirir el bloqueo: ante rechazos concurrentes del mismo token, se reutiliza la renovacion que otra replica ya guardo.

El bloqueo no espera: si otra instancia esta renovando, se devuelve un error de integracion saneado y no se inicia otra autenticacion. La transaccion tiene un limite de 20 segundos; el cliente de autenticacion mantiene sus timeouts y no hace reintentos automaticos. La llamada de autenticacion ocurre dentro de esta transaccion dedicada para mantener la exclusion entre replicas; nunca se bloquean filas de cuentas ni se ejecuta sendTx dentro de ella. Al fallar o terminar se libera el bloqueo. Las llamadas financieras siguen fuera de las transacciones bancarias y no se reenvian por renovar el token.

Si el proveedor emite un token y despues falla el commit, no se puede recuperar ese token no persistido: una solicitud posterior puede requerir otra autenticacion. El proveedor no ofrece en el contrato revisado una transaccion atomica compartida con nuestra base. Esto no dispara otro sendTx. La garantia de coordinacion exige que todos los procesos usen esta implementacion y la misma base. La renovacion administrativa explicita conserva su comportamiento de renovacion forzada.

Los clientes Feign conservan NoOpLogger, HTTPS y errores sin cuerpo, URL ni causa potencialmente sensibles. El campo token de la entidad no se serializa; el DTO de autenticacion permite leerlo del proveedor pero no serializarlo ni incluirlo en toString. No se agrega ningun endpoint para consultar el token. Mantener desactivados los logs de trafico HTTP de bajo nivel, proxies, agentes de tracing y dumps de memoria: el proceso necesariamente utiliza el Bearer en memoria para comunicarse por HTTPS. La configuracion externa de infraestructura, TLS, permisos de base y backups requiere verificacion operativa propia.

## Pruebas

Pruebas locales: nonce distinto, alteraciones, clave incorrecta, vinculo a registro, serializacion, sanitizacion de errores, vigencia, rotacion sin extender fecha, persistencia JPA real H2 y reutilizacion entre dos servicios. Feign se prueba con transporte simulado; no se ejecutan pagos reales.

`GestoPagoTokenCoordinationPostgresTest` comprueba con dos conexiones PostgreSQL que el bloqueo excluye otra instancia y se libera al commit y rollback. `DatabaseSchemaMigrationTest` comprueba V20 y compatibilidad con ambas cadenas de migraciones en esquemas desechables. Estas pruebas requieren `ONBOARDING_TEST_JDBC_URL`, `ONBOARDING_TEST_DB_USER` y `ONBOARDING_TEST_DB_PASSWORD`; si no estan configuradas se omiten. H2 y mocks no demuestran el comportamiento de los advisory locks de PostgreSQL. Sigue pendiente la prueba real con Gestopago.
