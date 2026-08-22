# Architecture

Flight Discovery es un proyecto full stack con backend Spring Boot y frontend Angular. El backend concentra la logica de recomendacion y el frontend consume la API para mostrar rutas y mapas.

## Backend

El codigo del backend vive bajo `src/main/java/flightdiscovery/paull`.

Capas principales:

- `flightdiscovery.paull.Main`: arranque de Spring Boot.
- `flightdiscovery.paull.api`: controladores REST y DTOs de entrada/salida.
- `flightdiscovery.paull.api.health`: endpoint de salud.
- `flightdiscovery.paull.api.recommendation`: endpoints de recomendaciones y diagnostico.
- `flightdiscovery.paull.application.recommendation`: orquestacion de recomendaciones, generacion de candidatas, diversidad y descartes.
- `flightdiscovery.paull.domain.calculation`: calculos de distancia, tiempo, combustible y coste.
- `flightdiscovery.paull.domain.model`: modelos de aeropuerto, avion, ruta, waypoint, meteorologia, precio y score.
- `flightdiscovery.paull.domain.repository`: repositorios mock en memoria.
- `flightdiscovery.paull.domain.scoring`: scoring de rutas.
- `flightdiscovery.paull.domain.weather`: meteorologia simulada.

## Flujo de Recomendacion

1. `RecommendationController` valida la request.
2. `RecommendationService` resuelve aeropuerto, avion, velocidad, consumo, precio de combustible y tiempo util.
3. El tiempo util se calcula restando la reserva recomendada del avion y aplicando el margen de seguridad.
4. `RouteCandidateGenerator` crea rutas circulares generadas con uno o dos waypoints visuales compatibles con el aeropuerto.
5. Las rutas generadas se filtran por tolerancia maxima de 125% del tiempo util.
6. Las candidatas se clasifican por bandas de duracion:
   - `short`: 30% - 50%.
   - `medium`: 50% - 75%.
   - `long`: 75% - 100%.
   - `extended`: 100% - 125%.
7. El generador selecciona hasta 30 candidatas buscando variedad de bandas y tipos de ruta, pero priorizando cercania al objetivo temporal del 85%.
8. `RouteScoringService` calcula `weatherScore`, `timeFitScore`, `preferenceScore`, `scenicScore`, `costScore` y `totalScore`.
9. `RecommendationService` aplica diversidad final, ordena por score y devuelve hasta 5 recomendaciones.
10. Las rutas que superan el tiempo util pero no el 125% se permiten con warning.

## Scoring

El `totalScore` se mantiene entre 0 y 100.

Pesos actuales:

- Meteorologia simulada: 20%.
- Encaje temporal: 30%.
- Preferencia del usuario: 20%.
- Interes visual: 25%.
- Coste: 5%.

`timeFitScore` usa una duracion objetivo:

```text
targetDurationMinutes = usefulAvailableTimeMinutes * 0.85
```

Las rutas entre 70% y 100% del tiempo util puntuan alto, con maximo cerca del 85%. Las rutas demasiado cortas se penalizan cuando la preferencia no es `short`. Las rutas entre 100% y 125% se permiten pero se penalizan. Por encima de 125% se descartan.

## Debug

`POST /api/recommendations/debug` devuelve informacion de desarrollo:

- Request recibida.
- Avion resuelto.
- Tiempo util.
- Waypoints compatibles.
- Numero total de candidatas generadas.
- Candidatas evaluadas con scores y motivos de descarte.
- Descartes por generacion, filtro temporal y seleccion final.
- Recomendaciones finales.

## Frontend

El frontend vive en `frontend/` y usa Angular con Leaflet.

Responsabilidades actuales:

- Capturar parametros de busqueda.
- Llamar al backend mediante proxy `/api`.
- Mostrar recomendaciones, desglose de score, costes, meteorologia simulada y avisos.
- Visualizar rutas sobre mapa.

## Datos

El proyecto no usa base de datos. Los datos se cargan desde repositorios mock en memoria:

- Aeropuertos.
- Aviones.
- Rutas predefinidas.
- Waypoints visuales.
- Precios de combustible.
- Meteorologia simulada.
