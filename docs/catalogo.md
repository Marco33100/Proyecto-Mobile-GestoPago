# Catálogo de productos en la app (web y móvil)

La pestaña **Servicios** muestra el catálogo de Gestopago. No hay endpoint nuevo: la app usa el existente.

## Endpoint

`GET /api/products` (rol CLIENTE, `Authorization: Bearer` en móvil o cookie en web).

Respuesta `GenericResponse`: `codigo` 1 con `datos.productos.productos` (lista); `codigo` 0 si no hay productos. Errores: 502 si Gestopago no responde y no hay copia, 500 si falla la base. El backend sirve el catálogo desde Redis, luego PostgreSQL y por último Gestopago.

| Campo | Uso en la app |
|---|---|
| `servicio` | Agrupa los productos en servicios (Telcel, CFE, Amazon…) |
| `producto` | Nombre del producto |
| `idCatTipoServicio` | Categoría (ver tabla) |
| `tipoFront` | Forma de pago y disponibilidad |
| `precio` | Precio, solo cuando `tipoFront` = 1 |
| `legend` | Descripción: instrucciones de uso y atención a clientes |
| `idProducto` | Abre el pago con ese producto ya elegido |

## Precio y disponibilidad

| `tipoFront` | Se muestra | ¿Se puede comprar? |
|---|---|---|
| 1 | Precio fijo (`precio`) | Sí: recargas, tarjetas de regalo |
| 2 | "Tú indicas el importe" | Sí |
| 4 | "Importe según tu recibo" | Sí (se valida la referencia) |
| otros (5, 30, 40) | "No disponible en la app" | No: aparece como *Solo consulta* |

En los tipos 2 y 4, Gestopago envía un precio de referencia (0 o 10) que no es el importe real; por eso no se muestra. El backend vuelve a validar el tipo al preparar el pago.

## Categorías

Gestopago no envía nombres ni imágenes de categoría. Los grupos salen de los productos reales:

| `idCatTipoServicio` | Categoría |
|---|---|
| 1, 2, 3, 4, 12, 13 | Recargas y paquetes (Movistar, Telcel, AT&T, Unefon, Virgin, otras móviles) |
| 5 | Luz, gas y TV |
| 19 | Internet |
| 15 | Agua |
| 10 | Tarjetas de regalo |
| 11 | Entretenimiento y juegos |
| 7 | Gobierno e impuestos |
| 8 | Créditos y finanzas |
| 9 | Venta por catálogo |
| 18 | Peaje y transporte |
| 16 | Apuestas |
| otro | Otros |

## Logos

El catálogo no incluye imágenes. La app arma la dirección del logo con `idServicio`:

`https://gestopago.portalventas.net/sistema/images/gestopago/servicios/{idServicio}.png`

La descarga la hace la app, sin credenciales y sin pasar por el backend, Redis ni la base de datos. La dirección no aparece en la documentación del proyecto sobre la API: se comprobó con peticiones directas (133 Telcel y 71 Amazon responden la imagen). Algunos servicios no tienen logo (404); en ese caso, y mientras carga, se muestran las iniciales. El icono de la categoría se conserva en ambos casos.

En web el logo se muestra con un elemento `<img>`, porque ese servidor no envía encabezados CORS.

## Flujo de compra

Catálogo → servicio → detalle del producto (precio, qué se necesita, descripción) → **Comprar** → formulario de pago con el producto elegido. Si hay un pago sin resolver, el catálogo lo avisa antes de otra compra.

El código está en `frontend/lib/models/catalog.dart` (reglas) y `frontend/lib/screens/catalog_page.dart` (pantallas).
