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
3. `usefulAvailableTimeMinutes` se calcula restando la reserva recomendada del avion y aplicando el margen de seguridad. Este valor no se usa solo como limite maximo: tambien define la duracion objetivo de las rutas.
4. `targetDurationMinutes` es el objetivo temporal interno para scoring y ordenacion. Actualmente equivale a `usefulAvailableTimeMinutes * 0.85`, de forma que una busqueda de 120 minutos utiles favorece rutas alrededor de 102 minutos.
5. `RouteCandidateGenerator` crea rutas circulares generadas con 1, 2 y, cuando hay al menos 90 minutos utiles, 3 o mas waypoints visuales compatibles con el aeropuerto.
6. Las rutas generadas se filtran por tolerancia maxima de 125% del tiempo util.
7. Las candidatas se clasifican por bandas de duracion:
   - `short`: 30% - 50%.
   - `medium`: 50% - 75%.
   - `long`: 75% - 100%.
   - `extended`: 100% - 125%.
8. El generador selecciona hasta 30 candidatas buscando variedad de bandas y tipos de ruta, pero priorizando cercania al objetivo temporal del 85%.
9. `RouteScoringService` calcula `weatherScore`, `timeFitScore`, `preferenceScore`, `scenicScore`, `costScore` y `totalScore`.
10. `RecommendationService` calcula `routeDurationCategory`, aplica diversidad final, ordena por prioridad temporal, preferencia y score, y devuelve hasta 5 recomendaciones.
11. Las rutas que superan el tiempo util pero no el 125% se permiten con warning.

## Scoring

El `totalScore` se mantiene entre 0 y 100.

Pesos actuales:

- Meteorologia simulada: 25%.
- Encaje temporal: 35%.
- Interes visual: 25%.
- Preferencia del usuario: 10%.
- Coste: 5%.

`timeFitScore` usa una duracion objetivo, no solo un filtro de maximo:

```text
targetDurationMinutes = usefulAvailableTimeMinutes * 0.85
```

Las rutas entre 70% y 100% del tiempo util puntuan alto, con maximo cerca del 85%. Las rutas entre 100% y 125% se permiten pero se penalizan. Por encima de 125% se descartan.

Las rutas demasiado cortas se tratan de forma explicita:

- Si usan menos del 40% de `usefulAvailableTimeMinutes`, su `routeDurationCategory` es `TOO_SHORT`.
- Si usan entre 40% y 70%, se clasifican como `SHORT`.
- Si `preference` no es `short`, las rutas por debajo del 40% reciben una penalizacion fuerte en `timeFitScore`, y las que estan entre 40% y 60% una penalizacion moderada.
- Si `preference` es `short`, las rutas cortas no se penalizan por duracion y reciben un `timeFitScore` alto.
- En la seleccion final, las rutas `TOO_SHORT` no deben dominar el top 5 cuando existen alternativas de mejor ajuste temporal.

`routeDurationCategory` resume como usa la ruta el tiempo util:

- `TOO_SHORT`: menos de 40%.
- `SHORT`: 40% - 70%.
- `GOOD_FIT`: 70% - 90%.
- `LONG`: 90% - 100%.
- `SLIGHTLY_OVER_TIME`: 100% - 125%.
- `TOO_LONG`: mas de 125% o sin tiempo util.

## Generacion de Rutas

`RouteCandidateGenerator` genera rutas circulares dinamicas desde el aeropuerto de salida:

- 1 waypoint: salida, waypoint visual y regreso.
- 2 waypoints: salida, primer waypoint, segundo waypoint y regreso.
- 3 o mas waypoints: disponibles cuando `usefulAvailableTimeMinutes` es al menos 90 minutos, con un limite de candidatos antes del filtrado temporal para contener la combinatoria.

La generacion usa bandas de duracion para que el conjunto de candidatas no quede sesgado hacia rutas muy cortas. Para cada busqueda intenta conservar rutas `long`, `medium`, `extended` y `short`, y despues rellena con las mejores candidatas restantes. Cuando hay una preferencia como `coast` o `mountain`, reserva la mayoria de las candidatas para rutas que coinciden con la preferencia, pero mantiene alternativas para diversidad.

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
