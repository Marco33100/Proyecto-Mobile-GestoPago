# Contrato de tipos y longitudes

Revision por campo, no un limite unico para todas las tablas. El esquema final
se obtiene aplicando V1 a V16: no editar una migracion ya aplicada ni ejecutar
de nuevo sus CREATE TABLE para cambiar columnas existentes.

## Texto de las tablas de aplicacion

| Tabla | Campos | Tipo / maximo | Criterio |
| --- | --- | --- | --- |
| clientes | nombre, segundo_nombre, apellido_paterno, apellido_materno | VARCHAR(50) | Maximo establecido por el documento; segundo nombre opcional. |
| clientes | curp | VARCHAR(18) | Exactamente 18 y CHECK de formato, no solo un maximo. |
| clientes | rfc | VARCHAR(13) | Formato de 12 o 13, validado por CHECK y DTO. |
| clientes | correo | VARCHAR(100) | Maximo del requerimiento; no reducir a partir de un correo de ejemplo. |
| clientes | telefono_movil, telefono_alternativo | VARCHAR(10) | Exactamente 10 digitos; conserva ceros iniciales. Alternativo opcional. |
| clientes | sexo | VARCHAR(10) | Enum: FEMENINO, MASCULINO, NO_BINARIO (10). |
| clientes | estado_civil | VARCHAR(11) | Enum: SOLTERO, CASADO, DIVORCIADO, VIUDO, UNION_LIBRE (11). |
| clientes | nacionalidad | VARCHAR(60) | Texto descriptivo, no codigo ISO; limite del contrato local. |
| clientes | ocupacion | VARCHAR(100) | Puede contener una profesion y especialidad; contrato local. |
| clientes | empresa | VARCHAR(150) | Nombre comercial o razon social compuesta; no limitar como nombre personal. |
| clientes | referencia_reconocimiento_facial | VARCHAR(255) | Referencia opcional, NO imagen. Conservar hasta conocer contrato de integracion. |
| domicilios | calle, colonia, municipio, estado | VARCHAR(100) | Nombres compuestos y referencias; contrato local, no maximo universal. |
| domicilios | numero_exterior, numero_interior | VARCHAR(20) | Texto: admite letras, guiones, S/N y ceros; no INTEGER. Interior opcional. |
| domicilios | codigo_postal | VARCHAR(5) | Exactamente 5 digitos, conserva ceros; CHECK de formato. |
| domicilios | pais | VARCHAR(60) | Nombre de pais, no codigo de dos caracteres. |
| cuentas | numero_cuenta | VARCHAR(24) | Generador vigente: CTA- y 20 caracteres hexadecimales, unico. |
| app_users | email, identifier | VARCHAR(254) | Contrato de acceso existente; identifier tambien puede guardar el correo, no solo un alias. El correo del cliente sigue limitado a 100. |
| app_users | full_name | VARCHAR(203) | 4 componentes de 50 + 3 separadores; alinear tambien DTO de nombre completo. |
| app_users | password_hash | VARCHAR(60) | Salida del BCryptPasswordEncoder actual sin prefijo {id}. No es la longitud de la contrasena. |
| app_users | rol | VARCHAR(9) | Enum CLIENTE / EJECUTIVO (9), con CHECK existente. |
| gestopago_products | service_name, product_name, reference_type | VARCHAR(200), VARCHAR(250), VARCHAR(20) | Contrato local de integracion conservado: falta maximo formal del proveedor para justificar reducirlo. No usar el maximo observado de 900 productos. |
| gestopago_products | legend | TEXT | Leyendas externas de longitud variable; no cortar texto legal o instrucciones. |
| gestopago_tokens | codigo_dispositivo, token_type | VARCHAR(100), VARCHAR(50) | Limites existentes de integracion; no cambiar sin contrato del proveedor. |
| gestopago_tokens | token | TEXT | Credencial externa opaca, de longitud variable; nunca recortar ni registrar el valor en logs. |
| user_sessions | sin columnas de texto | UUID / TIMESTAMPTZ | Identificadores y fechas tipados, no VARCHAR para guardar una fecha o un UUID. |

Los valores internos de enums tienen un maximo comprobable; si se agrega uno
mas largo, debe actualizarse entidad y migracion antes de desplegarlo. Las
columnas del historial `flyway_schema_history` pertenecen a Flyway y no se
reducen como si fueran campos de negocio.

## Tipos no textuales conservados

- Fecha de nacimiento: LocalDate / DATE, sin hora; Flutter envia AAAA-MM-DD.
- Auditoria y expiracion: Instant / TIMESTAMPTZ. Fechas historicas de tokens:
  LocalDateTime / TIMESTAMP sin cambiar su zona por suposicion.
- Saldo e ingreso: BigDecimal / NUMERIC(19,2), con validaciones y CHECK existentes.
  Precio externo: BigDecimal / NUMERIC(19,4), conserva precision del proveedor.
- Baja/activacion: boolean / BOOLEAN. No guardar verdadero/falso como texto.
- Identificadores: Long / BIGINT donde ya corresponden; UUID y Integer / INTEGER
  en los contratos restantes. Se conservan PK, FK, unicidad e identidades.

## Migracion segura V15

Antes de reducir longitudes, V15 bloquea temporalmente las cuatro tablas afectadas
y comprueba los registros. Un exceso aborta la transaccion con un mensaje sin
datos personales. No usa LEFT, SUBSTRING ni casts para truncar. Se mantienen
datos, hashes, PK, FK, CHECK, NULL permitidos y estados. Tiene timeouts de bloqueo
y ejecucion, y se crea un respaldo local antes de aplicarla a Database-1.

Las pruebas PostgreSQL comparan las columnas de las siete entidades vigentes
contra el esquema V16 y verifican instalacion nueva, upgrade, limites, rollback
ante datos largos y conservacion de datos. Las pruebas historicas de V15 siguen
comprobando los limites anteriores a retirar Personas. Los formularios Flutter
de cliente ya usan los limites vigentes.

## Retiro de modelos antiguos V16

El backend actual usa `clientes` y `cuentas`, no `personas` ni `accounts`.
V16 elimina las tablas antiguas solo si estan vacias; las que contienen registros
se renombran a `legacy_personas` / `legacy_accounts`, conservando columnas,
restricciones y relaciones para archivo. No hay entidades JPA ni endpoints para
ellas. El formato historico de cuenta archivada mantiene VARCHAR(20) tanto en
account_number como en status; no se transforma en una cuenta bancaria nueva.
La migracion es transaccional, no utiliza CASCADE y no modifica V1 a V15.

Verificacion local de V16: 162 pruebas Java aprobadas, sin fallos ni omitidas,
incluidas 14 pruebas de migracion PostgreSQL. Se aplico en Database-1 despues
de respaldar el schema public en `.local-db-backups/Database-1-before-V16-20261006-205844.dump`.
`personas` estaba vacia y se retiro; `accounts` contenia un registro y quedo
archivada como `legacy_accounts`. La huella del contenido de las tablas antes
y despues coincide; las FK siguen validas y se conservan los 900 productos.

Verificacion local de V15: 163 pruebas Java aprobadas, incluidas 13 pruebas de
migracion PostgreSQL, sin fallos ni omitidas. En Database-1 se comprobaron las
41 columnas de texto sin diferencias; los datos de las cuatro tablas afectadas
se conservaron y el catalogo externo mantuvo sus 900 productos. El respaldo
previo permanece en `.local-db-backups/`, excluido de Git. Flyway emitio una
advertencia porque su version incluida solo declara soporte probado hasta
PostgreSQL 16; la aplicacion local usa 18. La migracion fue exitosa, pero esa
compatibilidad de dependencias debe revisarse antes de un despliegue productivo.

## Memoria y fuentes

VARCHAR(n) limita caracteres, no reserva n bytes por registro. Disminuir su maximo
no reduce el espacio de un mismo texto corto ni garantiza que soporte mas carga.
No se usa CHAR(n) para introducir relleno innecesario.

Referencias: [tipos de texto en PostgreSQL](https://www.postgresql.org/docs/current/datatype-character.html),
[ALTER TABLE](https://www.postgresql.org/docs/current/sql-altertable.html),
[almacenamiento de contrasenas en Spring Security](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html).
