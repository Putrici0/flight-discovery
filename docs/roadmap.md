# Roadmap

## Hecho

- Base Spring Boot con endpoint de salud.
- API de recomendaciones.
- Modelos mock de aeropuertos, aviones, rutas, waypoints visuales, combustible y meteorologia.
- Calculos de distancia, tiempo, combustible y coste.
- Scoring con desglose por meteorologia, tiempo, preferencia, interes visual y coste.
- Tiempo util con reserva recomendada y margen de seguridad.
- Generacion dinamica de rutas circulares de uno o dos waypoints.
- Seleccion de candidatas por bandas de duracion.
- Tolerancia de rutas hasta 125% del tiempo util con warning.
- Endpoint de debug para inspeccionar candidatas, descartes y recomendaciones.
- Frontend Angular con Leaflet y proxy `/api`.
- Tests unitarios y de controlador para backend.
- `weather.provider=mock/open-meteo` con mock por defecto.
- `OpenMeteoWeatherService` basico con cache en memoria y fallback controlado a mock durante recomendaciones.
- `RouteWeatherSummary` multi-punto y `weatherScore` calculado desde resumen de ruta.
- `plannedDepartureDateTime` en request y formulario.
- `AirportFuelPrice` mock por aeropuerto y tipo de combustible.
- Precio de combustible manual vs automatico en backend y frontend.
- Aviso visible de uso no aeronautico.

## Siguiente Iteracion

- Mejorar UX del frontend para explicar bandas de duracion, warnings y desglose de scoring.
- Mostrar diagnostico de candidatas en una vista de desarrollo o panel oculto.
- Ampliar catalogo mock de aeropuertos, aviones, rutas y waypoints.
- Revisar textos de explicacion para que sean mas claros para pilotos recreativos.
- Anadir fixtures o snapshots de recomendaciones para detectar regresiones de ranking.

## Datos Reales

- Endurecer Open-Meteo con limites, metricas y tests de integracion opcionales.
- Evaluar METAR/TAF para aeropuertos con cobertura mediante AviationWeather.
- Evaluar OpenAIP u otra fuente de espacio aereo.
- Preparar persistencia para datos geoespaciales si el catalogo crece.

## Producto Usable

- Guardar perfiles de avion.
- Guardar preferencias del usuario.
- Exportar rutas en GPX/KML.
- Convertir frontend en PWA.
- Preparar despliegue y perfiles de entorno.

## Seguridad y Operacion

- Indicar con mas claridad que la herramienta no es planificacion operacional.
- Incorporar performance real de aeronave.
- Incorporar NOTAM y restricciones.
- Separar configuracion de minimos personales y reglas locales.
