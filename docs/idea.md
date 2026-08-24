# Idea

Flight Discovery ayuda a pilotos recreativos a encontrar rutas de vuelo interesantes y razonables sin comparar manualmente tiempos, combustible, coste, meteorologia basica y preferencias personales.

## Problema

Planificar un vuelo recreativo sencillo exige equilibrar varias restricciones:

- Tiempo disponible real despues de reserva y margen de seguridad.
- Avion, velocidad de crucero y consumo.
- Coste estimado.
- Meteorologia.
- Interes visual de la ruta.
- Preferencias como costa, montana, panoramica o rutas cortas.

Si el sistema solo filtra por un maximo de tiempo, tiende a devolver rutas demasiado cortas. Si optimiza demasiado el tiempo objetivo, puede recomendar rutas lejanas sin valor recreativo suficiente. Para un vuelo recreativo, el objetivo es una ruta interesante que encaje en el bloque disponible, no quemar minutos a cualquier precio.

## Propuesta

Una web responsive donde el usuario introduce:

- Aeropuerto de salida.
- Tiempo disponible.
- Avion.
- Velocidad y consumo opcionales.
- Precio de combustible opcional.
- Preferencia de ruta.
- Margen de seguridad.

El sistema devuelve hasta 5 recomendaciones con:

- Rutas predefinidas y rutas generadas dinamicamente.
- Tiempo, distancia, combustible y coste estimados.
- Puntuacion total y desglose.
- Meteorologia simulada.
- Explicacion legible.
- Warnings de tiempo, coste y meteorologia simulada.
- Visualizacion sobre mapa.

## Enfoque de Tiempo

El tiempo disponible se trata como limite operativo y orientacion. No es una obligacion de rellenar minutos.

La duracion objetivo es:

```text
targetDurationMinutes = usefulAvailableTimeMinutes * 0.85
```

Las rutas que aprovechan entre 70% y 100% del tiempo util puntuan alto en `timeFitScore`, pero la seleccion final puede preferir una ruta local mas corta frente a una ruta lejana artificial. Las rutas que superan el tiempo util se permiten hasta 125%, pero con penalizacion y warning.

Las rutas locales de alto interes pueden anadir tiempo de observacion escenica explicito. Las travesias entre islas existen como categoria especial, pero solo suben por defecto si el usuario pide `inter-island`, `islands`, `cross-country` o `adventure`.

## Generacion de Rutas

El generador crea rutas circulares desde el aeropuerto de salida usando waypoints visuales compatibles.

Bandas de candidatas:

- `short`: 30% - 50% del tiempo util.
- `medium`: 50% - 75%.
- `long`: 75% - 100%.
- `extended`: 100% - 125%.

El objetivo es devolver opciones variadas sin caer en rutas siempre cortas ni en saltos lejanos artificiales. La seleccion prioriza rutas locales coherentes, despues encaje temporal, preferencia y variedad.

## Alcance del MVP

El MVP es una herramienta de descubrimiento, no una herramienta de planificacion operacional. Los datos son mock y no sustituyen documentacion aeronautica, meteorologia real, NOTAM ni calculos oficiales de performance.
