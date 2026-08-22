# TODO

## Backend

- Anadir tests de regresion para rankings representativos por preferencia y tiempo disponible.
- Exponer en la respuesta, si hace falta en frontend, la banda de duracion de cada ruta.
- Revisar si `extended` debe tener un warning especifico distinto de "supera ligeramente el tiempo disponible".
- Parametrizar pesos de scoring y cuotas de bandas cuando haya mas datos.
- Mejorar logging para diferenciar descartes por tiempo, limite de candidatas y seleccion final.

## Datos y Fuentes

- Ampliar aeropuertos mock.
- Ampliar aviones mock con mas tipos de combustible y perfiles realistas.
- Ampliar waypoints visuales por aeropuerto.
- Integrar Open-Meteo para meteorologia basica.
- Anadir METAR/TAF.
- Evaluar OpenAIP para espacio aereo.
- Anadir PostGIS cuando haga falta persistencia geoespacial.
- Mejorar el modelo de aeronaves con autonomia, combustible utilizable, reserva y consumos por fase.

## Frontend

- Mostrar mejor el desglose de scoring.
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
