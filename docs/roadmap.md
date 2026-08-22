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

## Siguiente Iteracion

- Mejorar UX del frontend para explicar bandas de duracion, warnings y desglose de scoring.
- Mostrar diagnostico de candidatas en una vista de desarrollo o panel oculto.
- Ampliar catalogo mock de aeropuertos, aviones, rutas y waypoints.
- Revisar textos de explicacion para que sean mas claros para pilotos recreativos.
- Anadir fixtures o snapshots de recomendaciones para detectar regresiones de ranking.

## Datos Reales

- Integrar Open-Meteo para meteorologia basica.
- Evaluar METAR/TAF para aeropuertos con cobertura.
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
