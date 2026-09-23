# Architecture

Flight Discovery es un proyecto full stack con backend Spring Boot y frontend Angular. El backend concentra la logica de recomendacion y el frontend consume la API para mostrar rutas y mapas.

## Backend

El codigo del backend vive bajo `src/main/java/flightdiscovery/paull`.

Capas principales:

- `flightdiscovery.paull.Main`: arranque de Spring Boot.
- `flightdiscovery.paull.api`: controladores REST y DTOs de entrada/salida.
- `flightdiscovery.paull.api.health`: endpoint de salud.
- `flightdiscovery.paull.api.recommendation`: endpoints de recomendaciones y diagnostico.
- `flightdiscovery.paull.application.recommendation`: orquestacion de recomendaciones, generacion de candidatas, tiempo/duracion, seleccion final, sightseeing, exposicion solar, resumen meteorologico de ruta, diversidad y descartes.
- `flightdiscovery.paull.domain.calculation`: calculos de distancia, tiempo, combustible y coste.
- `flightdiscovery.paull.domain.model`: modelos de aeropuerto, avion, ruta, waypoint, meteorologia, precio y score.
- `flightdiscovery.paull.domain.repository`: repositorios mock en memoria.
- `flightdiscovery.paull.domain.scoring`: scoring de rutas.
- `flightdiscovery.paull.domain.weather`: meteorologia mock y proveedor Open-Meteo opcional.

## Flujo de Recomendacion

1. `RecommendationController` valida la request.
2. `RecommendationService` resuelve aeropuerto, avion, velocidad, consumo y precio de combustible, y coordina servicios especializados sin cambiar el contrato publico.
3. `RecommendationTimeService` calcula `usefulAvailableTimeMinutes` restando la reserva recomendada del avion y aplicando el margen de seguridad. Este valor funciona como limite operativo y orientacion, no como obligacion de rellenar minutos.
4. `targetDurationMinutes` es una referencia interna para scoring temporal. Actualmente equivale a `usefulAvailableTimeMinutes * 0.85`, pero la seleccion final puede preferir rutas locales mas cortas cuando tienen mas sentido recreativo.
5. `RouteCandidateGenerator` crea rutas circulares generadas con 1, 2 y, cuando hay al menos 90 minutos utiles, 3 o mas waypoints visuales compatibles con el aeropuerto. Para rutas multi-waypoint usa conjuntos geograficos por paisaje, rumbo y distancia, y ordena puntos por vecino cercano para evitar zigzags.
6. Las rutas generadas se filtran por tolerancia maxima de 125% del tiempo util.
7. Las candidatas se clasifican por bandas de duracion:
   - `short`: 30% - 50%.
   - `medium`: 50% - 75%.
   - `long`: 75% - 100%.
   - `extended`: 100% - 125%.
8. `RouteCandidateSelectionService` selecciona hasta 80 candidatas buscando variedad de bandas, tipos de ruta y baja similitud entre rutas. Cuando la preferencia no es interinsular, las rutas locales se ordenan por delante de travesias entre islas. El generador conserva tambien variantes inversas de rutas multi-waypoint para que el scoring pueda comparar orientacion solar y visual.
9. Para cada ruta `RouteWeatherSummaryService` consulta hasta 3 puntos meteorologicos y crea `RouteWeatherSummary`.
10. `SunExposureService` calcula azimut solar, rumbo de cada tramo, angulo relativo, lado del sol, penalizacion frontal, lado visual recomendado y `orientationScore`.
10. `RouteScoringService` calcula `weatherScore`, `timeFitScore`, `preferenceScore`, `scenicScore`, `visualOrientationScore`, `costScore` y `totalScore`.
10. `SightseeingService` calcula tiempo de observacion escenica local, maniobras y `flightPath`.
10. `RecommendationSelectionService` aplica la seleccion final diversa, descarta rutas demasiado similares y devuelve hasta 5 recomendaciones.
11. Las rutas que superan el tiempo util pero no el 125% se permiten con warning.

## Scoring

El `totalScore` se mantiene entre 0 y 100.

Pesos actuales:

- Meteorologia multi-punto: 23%.
- Encaje temporal: 30%.
- Interes visual base: 22%.
- Calidad visual/orientacion: 12%.
- Preferencia del usuario: 8%.
- Coste: 5%.

`timeFitScore` usa una duracion objetivo, no solo un filtro de maximo:

```text
targetDurationMinutes = usefulAvailableTimeMinutes * 0.85
```

Las rutas entre 70% y 100% del tiempo util puntuan alto, con maximo cerca del 85%. Las rutas entre 100% y 125% se permiten pero se penalizan. Por encima de 125% se descartan.

El `weatherScore` sale de `RouteWeatherSummary`: viento alto, precipitacion alta, nubosidad muy alta y visibilidad baja penalizan; condiciones suaves puntuan alto. El score se limita siempre a 0-100. El `totalScore` penaliza rutas con tag `inter-island` salvo que la preferencia sea `inter-island`, `islands`, `cross-country` o `adventure`.

La calidad visual/orientacion se calcula en `SunExposureService` con reglas graduales, no binarias. Para cada tramo se obtiene rumbo del avion, azimut solar, angulo relativo, posicion del sol (`FRONT`, `BEHIND`, `LEFT`, `RIGHT`), penalizacion por sol frontal y lado recomendado de vistas cuando puede inferirse desde `VisualWaypoint.preferredViewingBearingDegrees` o, como fallback, desde rutas de costa. `orientationScore` combina exposicion solar y calidad del lado visual; pesa un 12% en el total para que pueda decidir entre variantes parecidas sin dominar meteorologia, tiempo o valor escenico. La respuesta conserva `sunExposureScore` y `sunExposureSummary`, y anade `legOrientations`, `predominantSunPosition`, `recommendedViewingSide`, `frontalSunLegs` y `orientationFavorableReason`.

## Weather

`weather.provider=mock` es el valor backend por defecto y selecciona `MockWeatherService`. `weather.provider=open-meteo` selecciona `OpenMeteoWeatherService`.

La request tambien puede incluir `weatherProvider=mock` o `weatherProvider=open-meteo`. Ese valor permite que el frontend active o desactive meteorologia real sin cambiar `application.properties`; si no llega, se usa el proveedor configurado en backend.

`OpenMeteoWeatherService` consulta forecast horario con latitud, longitud y fecha/hora local planificada. Mantiene cache en memoria usando latitud, longitud y hora redondeadas. Si Open-Meteo falla durante una recomendacion, el servicio cae a datos mock controlados para conservar una respuesta explicable.

`RouteWeatherSummaryService` combina hasta 3 puntos por ruta: salida, waypoint principal/intermedio y ultimo waypoint antes del regreso. Incluye viento medio/maximo, nubosidad media, precipitacion maxima, visibilidad minima, temperatura media, provider e indicador `isMock`.

## Fuel

El modelo `AirportFuelPrice` representa precios por aeropuerto y tipo de combustible: `airportCode`, `fuelType`, `pricePerLiter`, `currency`, `source`, `lastUpdated` e `isMock`.

Si el usuario envia `fuelPricePerLiter`, ese valor manual tiene prioridad. Si lo omite, el backend busca el precio mock por `departureAirport + aircraft.fuelType`. La respuesta expone `fuelPricePerLiter`, `fuelPriceSource`, `fuelPriceIsMock`, `fuelTypeUsed` y `fuelPriceAirportCode`.

Las rutas demasiado cortas se tratan de forma explicita:

- Si usan menos del 40% de `usefulAvailableTimeMinutes`, su `routeDurationCategory` es `TOO_SHORT`.
- Si usan entre 40% y 70%, se clasifican como `SHORT`.
- Si `preference` no es `short`, las rutas por debajo del 40% reciben una penalizacion fuerte en `timeFitScore`, y las que estan entre 40% y 60% una penalizacion moderada.
- Si `preference` es `short`, las rutas cortas no se penalizan por duracion y reciben un `timeFitScore` alto.
- En la seleccion final, una ruta local corta puede superar a una travesia entre islas si la alternativa larga solo mejora el encaje temporal.

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
- Rutas por paisaje: ventanas coherentes de costa, montana, barrancos, pueblos, puntos historicos, bosques, volcanes y vistas panoramicas.
- Rutas por geometria: conjuntos ordenados por rumbo o distancia desde el aeropuerto, reordenados por vecino cercano para reducir vueltas sobre el mismo tramo.

La generacion usa bandas de duracion para que el conjunto de candidatas no quede sesgado hacia rutas muy cortas. Para cada busqueda intenta conservar rutas `long`, `medium`, `extended` y `short`, y despues rellena con las mejores candidatas restantes. Cuando hay una preferencia como `coast` o `mountain`, reserva la mayoria de las candidatas para rutas que coinciden con la preferencia, pero mantiene alternativas para diversidad.

Antes de que las candidatas lleguen al scoring, `RouteCandidateSelectionService` evita llenar el cupo con rutas casi iguales. `RouteSimilarityService` compara rutas por waypoints compartidos, waypoints cercanos y geometria aproximada. La seleccion final usa la misma metrica para evitar rutas practicamente identicas en el top 5. Los descartes por similitud aparecen en debug como `FINAL_SIMILARITY_FILTER` o con motivo `Too similar to a selected generated candidate`.

Para rutas interinsulares desde GCLP, el catalogo mock permite usar puntos de Tenerife como referencias visuales adicionales cuando la preferencia es `inter-island` o `cross-country`. La intencion es proponer rutas recreativas con puntos cercanos de ambas islas, sin convertir esos puntos en autorizaciones operacionales.

Las rutas locales de alto valor visual pueden recibir `sightseeingTimeMinutes`: minutos explicitos de observacion escenica. No se usan para alargar artificialmente el vuelo; aumentan el tiempo estimado, combustible y coste de forma transparente. Los limites actuales son 12 minutos por waypoint, 30 minutos por ruta y un maximo del 85% del tiempo base.

Cuando una ruta incluye observacion escenica, la respuesta devuelve `sightseeingManeuvers` con waypoint, duracion, radio e instruccion, y `flightPath` con puntos intermedios de orbita para que el mapa pinte la maniobra en vez de representar solo lineas rectas entre waypoints.

## Servicios internos de recomendacion

La API externa no cambia, pero la logica interna se divide en responsabilidades mas pequenas:

- `RecommendationService`: orquesta la request, fusiona rutas predefinidas/generadas y construye la respuesta.
- `RecommendationTimeService`: tiempo util, tolerancia del 125%, categoria de duracion y textos de encaje temporal.
- `RecommendationSelectionService`: seleccion final, diversidad de waypoints/tipos/tags y prioridad local frente a interinsular.
- `RouteCandidateGenerator`: crea rutas dinamicas y descarta las que no caben en tiempo.
- `RouteCandidateSelectionService`: limita candidatas generadas por preferencia, banda de duracion y tipo de ruta.
- `RouteSimilarityService`: similitud por waypoints compartidos, proximidad geografica y geometria aproximada.
- `SightseeingService`: tiempo de observacion, orbitas escenicas y `flightPath`.
- `SunExposureService`: azimut solar aproximado, orientacion por tramo, penalizacion frontal, lado visual recomendado y resumen textual.
- `RouteWeatherSummaryService`: puntos meteorologicos por ruta, fallback y agregado multipunto.

## Debug

`POST /api/recommendations/debug` devuelve informacion de desarrollo:

- Request recibida.
- `plannedDepartureDateTime`.
- Proveedor weather usado y puntos consultados.
- Tipo/precio/fuente de combustible usados.
- `targetDurationMinutes`.
- Avion resuelto.
- Tiempo util.
- Waypoints compatibles.
- Numero total de candidatas generadas.
- Candidatas evaluadas con scores y motivos de descarte.
- Descartes por generacion, filtro temporal, similitud y seleccion final.
- Recomendaciones finales.

## Frontend

El frontend vive en `frontend/` y usa Angular con Leaflet.

Responsabilidades actuales:

- Capturar parametros de busqueda.
- Llamar al backend mediante proxy `/api`.
- Mostrar recomendaciones, desglose de score, costes, selector de proveedor meteorologico y avisos.
- Visualizar rutas sobre mapa.

## Datos

El proyecto no usa base de datos. Los datos se cargan desde repositorios mock en memoria:

- Aeropuertos.
- Aviones.
- Rutas predefinidas.
- Waypoints visuales.
- Precios de combustible.
- Meteorologia simulada.
