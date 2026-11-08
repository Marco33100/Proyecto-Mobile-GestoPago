# Revision de los hallazgos de SonarQube

Las correcciones de mantenibilidad se aplican al codigo fuente. Las siguientes
advertencias se revisaron en su contexto. Por solicitud del usuario se excluyeron
los archivos indicados abajo; no se desactivaron sus reglas globalmente.

## Exclusiones solicitadas

Se configuraron en `.idea/sonarlint.xml` las rutas de `GeneratedPluginRegistrant.java`,
`flutter_lldb_helper.py`, `JwtTokenService.java`, `PublicSecurityConfiguration.java`,
`SecurityConfiguration.java` y `TotpCodes.java`. Se incluye expresamente
`.idea/sonarlint.xml` en el commit para compartir estas exclusiones; el resto de
la configuracion local de IntelliJ permanece ignorado. IntelliJ puede requerir reabrir el proyecto para
cargarlo; despues se debe ejecutar un analisis nuevo, no consultar el informe anterior.

El scanner de Gradle conserva las exclusiones existentes y agrega las de JWT y TOTP;
las configuraciones y los dos archivos generados ya estaban excluidos alli.
Estas son exclusiones de archivo completo: futuros hallazgos dentro de esos archivos
tampoco se reportaran. Las justificaciones que siguen documentan el motivo; excluir
un archivo no registra automaticamente una revision en el servidor de SonarQube.

## Correcciones aplicadas

| Regla | Cambio |
| --- | --- |
| S107 | Datos de cliente y domicilio agrupados por significado; busqueda recibe el request completo. |
| S3776 | Validaciones y transiciones extraidas en PagoLedger, conciliacion, gateway, clientes y segundo factor. |
| S5961 | Consulta de persistencia dividida en cuatro pruebas independientes, conservando sus aserciones. |
| S8745 | Un solo BeforeEach prepara la autenticacion y los datos de la prueba. |
| S5778 | El callback transaccional se prepara antes de la lambda que comprueba la excepcion. |
| S9142 | Un PreparedStatement reutilizado fuera de los recorridos de entidades y campos. |
| S1130 | SQLException queda en la configuracion JDBC de PostgreSQL; el metodo base ya no la declara. |
| S1141 | Confirmacion de pagos, renovacion del token y actualizacion de cache en metodos separados. |
| S3358 / S1659 | Modalidad de referencia extraida y variables de PagoLedger declaradas por separado. |
| S5852 | Validacion del correo sin busqueda con retroceso entre multiples puntos del dominio. |
| S2119 / S1192 / S1710 | SecureRandom reutilizado, roles constantes y ApiResponse repetibles. |
| kotlin:S7204 | R8 y reduccion de recursos habilitados para release. |

## Archivos generados por Flutter

- `mobile/gestopago_app/android/app/src/main/java/io/flutter/plugins/GeneratedPluginRegistrant.java`
- `mobile/gestopago_app/ios/Flutter/ephemeral/flutter_lldb_helper.py`

Ambos archivos indican que son generados. Flutter los regenera: agregar un
constructor privado o cambiar la firma del callback de LLDB perderia el cambio
o romperia el contrato del depurador. Se agregaron exclusiones especificas a
`sonar.exclusions` en el analisis de Gradle, conservando las exclusiones existentes.
Tambien estan excluidos en la configuracion local de SonarQube for IDE.
El IDE maneja sus exclusiones independientemente del scanner de Gradle.

## CSRF: SecurityConfiguration y PublicSecurityConfiguration (java:S4502)

El perfil con base de datos usa `STATELESS`. `JwtAuthenticationFilter` obtiene
las credenciales exclusivamente de `Authorization: Bearer`; consulta ademas que
la sesion siga vigente. No autentica a partir de cookies, parametros o HTTP Basic.
El navegador no adjunta automaticamente ese encabezado en una peticion ajena.
En el perfil sin base de datos solo son publicos salud y documentacion; el resto
de las rutas esta denegado. Estas son las condiciones que justifican la
configuracion actual. Si se incorpora autenticacion mediante cookies o Basic,
debe habilitarse y probarse la proteccion CSRF antes de liberar ese cambio.

Evidencia a revisar: las dos configuraciones de seguridad,
`JwtAuthenticationFilter` y las pruebas de autenticacion/autorizacion.
El estado del hotspot debe decidirse en SonarQube tras esa revision.
[Referencia de Spring Security](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).

## HMAC-SHA1 en TotpCodes (java:S4790)

Se usa `Mac` con una clave aleatoria de 20 bytes, no SHA-1 sin clave ni un hash
de contrasenas. El algoritmo coincide con el URI de registro
`algorithm=SHA1&digits=6&period=30` y los autenticadores ya registrados.
Cambiarlo unilateralmente invalidaria sus codigos. Se mantienen el control de
intentos, el bloqueo, la prevencion de reutilizacion y la comparacion constante
del codigo. `TotpCodesTest` comprueba vectores oficiales de HOTP/TOTP.
El hotspot requiere revisar esta justificacion; no se elimina cambiando el
nombre del algoritmo o suprimiendo la regla.
[RFC 6238](https://www.rfc-editor.org/rfc/rfc6238.html).

## Date en JwtTokenService (java:S2143)

La logica de expiracion se expresa con `Instant` y `Duration`. Las conversiones
`Date.from` permanecen en el limite con JJWT 0.12.6: `issuedAt` y `expiration`
reciben `java.util.Date`, y `Claims` devuelve ese tipo. Es interoperabilidad con
la API de la dependencia. Evaluar el aviso como excepcion justificada de esa API.
[API de JJWT](https://github.com/jwtk/jjwt/blob/0.12.6/api/src/main/java/io/jsonwebtoken/JwtBuilder.java).

## Android

El manifest principal ya contiene `allowBackup=false`, `fullBackupContent=false`,
reglas de extraccion y `usesCleartextTraffic=false`. Revisar el aviso contra el
archivo actual y la variante release; debug conserva su configuracion de desarrollo.
Se verifico tambien el manifest combinado de release generado por el build:
`allowBackup=false` y `usesCleartextTraffic=false`.
Se activo R8 (`isMinifyEnabled`) y reduccion de recursos (`isShrinkResources`)
en release, con el archivo de optimizacion estandar de Android.
[Documentacion de R8](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization).

## Verificacion final

Volver a ejecutar el analisis de SonarQube sobre el codigo actual. Las capturas
son de un informe anterior y sus numeros de linea cambian con los refactors.
Las pruebas PostgreSQL solo se ejecutan cuando esta configurada
`ONBOARDING_TEST_JDBC_URL`; una ejecucion local sin esa variable las omite.
