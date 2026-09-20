# Catálogo de productos Gestopago

Servicio Spring Boot que consulta y sincroniza el catálogo de Gestopago usando
PostgreSQL como almacenamiento permanente y Redis como caché de lectura.

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

