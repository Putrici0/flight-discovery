# TODO

## Backend

- Seguir ampliando tests de regresion para rankings representativos por preferencia, tiempo disponible y aeropuerto.
- Exponer en la respuesta, si hace falta en frontend, la banda de duracion de cada ruta.
- Revisar si `extended` debe tener un warning especifico distinto de "supera ligeramente el tiempo disponible".
- Parametrizar pesos de scoring y cuotas de bandas cuando haya mas datos.
- Mejorar logging para diferenciar descartes por tiempo, similitud, limite de candidatas, seleccion final, fallback weather y cache weather.
- Modelar variantes de ruta locales mas ricas: vuelta parcial/completa a isla, tramos panoramicos lentos y limites configurables de observacion escenica.
- Afinar `preferredViewingBearingDegrees` de `VisualWaypoint` con datos costeros/orograficos reales para mejorar la recomendacion de lado de vistas.
- Ajustar umbrales de similitud con datos reales y feedback de uso.

## Datos y Fuentes

- Ampliar aeropuertos mock.
- Ampliar aviones mock con mas tipos de combustible y perfiles realistas.
- Ampliar waypoints visuales por aeropuerto fuera de GCLP.
- Seguir validando el catalogo mock ampliado de Gran Canaria con fuentes geograficas y feedback de pilotos locales.
- Mejorar la seleccion de puntos cercanos al camino con corredores geoespaciales reales cuando haya persistencia geoespacial.
- Validar Open-Meteo contra respuestas reales y documentar limites horarios.
- Integrar METAR/TAF en una fase posterior mediante AviationWeather: METAR/TAF del aeropuerto de salida y de aeropuertos cercanos o alternativos.
- Evaluar OpenAIP para espacio aereo.
- Anadir PostGIS cuando haga falta persistencia geoespacial.
- Mejorar el modelo de aeronaves con autonomia, combustible utilizable, reserva y consumos por fase.

## Frontend

- Mostrar mejor el desglose de scoring y orientacion cuando haya mas componentes.
- Explicar visualmente si una ruta aprovecha poco, bien o demasiado el tiempo disponible.
- Mostrar warnings de rutas `extended` de forma mas visible.
- Anadir vista de debug para candidatas y descartes.
- Mejorar estados de carga, error y resultados vacios.
- Convertir el frontend en PWA.

## Producto

- Anadir exportacion GPX/KML.
- Guardar perfiles de avion.
- Guardar preferencias del usuario.
- Guardar rutas favoritas.
- Anadir persistencia.

## Plataforma

- Preparar configuracion de despliegue.
- Anadir perfiles de entorno.
- Configurar observabilidad basica.
- Revisar configuracion de Mockito/Java agent para JDKs futuros.
