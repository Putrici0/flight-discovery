# Flight Discovery

Flight Discovery es un MVP para recomendar rutas aereas recreativas a pilotos privados usando datos mock por defecto. Parte de un aeropuerto de salida y combina tiempo disponible, avion, consumo, precio de combustible, meteorologia y preferencia de ruta para proponer rutas viables y atractivas.

El tiempo disponible se usa como limite operativo y orientacion, no como una obligacion de rellenar minutos. El backend calcula un tiempo util, prioriza rutas recreativas locales con valor visual y solo promueve travesias entre islas cuando el usuario las pide explicitamente o faltan alternativas locales.

Flight Discovery es una herramienta orientativa de recomendacion recreativa. No sustituye la planificacion aeronautica oficial, documentacion operacional, NOTAM, METAR/TAF ni la responsabilidad del piloto.

## Requisitos

- Java 21
- Maven 3.9 o superior
- Node.js 20 o superior
- npm

## Estructura

- `src/main/java/flightdiscovery/paull/`: backend Spring Boot.
- `src/test/java/flightdiscovery/paull/`: tests del backend.
- `frontend/`: aplicacion Angular con Leaflet para buscar y visualizar recomendaciones.
- `docs/`: documentacion de arquitectura, datos y proximos pasos.
- `pom.xml`: configuracion Maven del backend.

## Ejecutar Backend

```bash
mvn spring-boot:run
```

El backend queda disponible en:

```text
http://localhost:8080
```

Por defecto el backend usa meteorologia mock si la request no indica proveedor:

```properties
weather.provider=mock
```

Para activar meteorologia real orientativa via Open-Meteo:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--weather.provider=open-meteo"
```

Tambien puede configurarse en `src/main/resources/application.properties`:

```properties
weather.provider=open-meteo
```

Valores admitidos:

- `mock`: usa `MockWeatherService`. Es el valor por defecto.
- `open-meteo`: usa `OpenMeteoWeatherService` y consulta Open-Meteo con `latitude`, `longitude` y `plannedDepartureDateTime`.

Para cada recomendacion se consultan hasta 3 puntos de la ruta: aeropuerto de salida, waypoint principal/intermedio y ultimo waypoint antes de volver. Open-Meteo usa una cache simple en memoria con latitud/longitud y hora redondeadas para evitar llamadas repetidas. Si una consulta Open-Meteo falla durante la recomendacion, se usa una respuesta mock controlada para no romper todo el calculo.

El frontend incluye un selector `Meteorologia`:

- `Open-Meteo real orientativa`: envia `weatherProvider=open-meteo` en la request y usa datos reales orientativos cuando la API responde.
- `Simulada/mock`: envia `weatherProvider=mock` para desarrollo, demos sin red o fallback controlado.

## Ejecutar Frontend

```bash
cd frontend
npm install
npm start
```

El frontend queda disponible en:

```text
http://localhost:4200
```

Angular usa `frontend/proxy.conf.json` para reenviar `/api` al backend en `http://localhost:8080`.

## Tests y Build

Backend:

```bash
mvn test
```

Frontend:

```bash
cd frontend
npm run build
```

## Endpoints

### GET /api/health

Respuesta:

```json
{
  "status": "ok"
}
```

### POST /api/recommendations

Ejemplo de peticion:

```json
{
  "departureAirport": "GCLP",
  "availableFlightTimeMinutes": 120,
  "aircraftId": "cessna-172",
  "cruiseSpeedKmh": 226,
  "fuelBurnLitersPerHour": 34,
  "fuelPricePerLiter": 2.3,
  "preference": "coast",
  "safetyMarginPercent": 15,
  "plannedDepartureDateTime": "2026-08-25T10:00",
  "weatherProvider": "open-meteo"
}
```

Validaciones basicas:

- `departureAirport` obligatorio.
- `availableFlightTimeMinutes` mayor que 0.
- `aircraftId` debe coincidir con un avion mock conocido.
- `cruiseSpeedKmh` opcional; si se omite se usa el valor del avion.
- `fuelBurnLitersPerHour` opcional; si se omite se usa el valor del avion.
- `fuelPricePerLiter` mayor o igual que 0.
- Si `fuelPricePerLiter` se omite o llega como `null`, el backend busca precio mock por `departureAirport` y `aircraft.fuelType`.
- `preference` obligatoria.
- `safetyMarginPercent` opcional; si se omite se usa 15%.
- `plannedDepartureDateTime` opcional, fecha/hora local prevista de salida en formato `yyyy-MM-ddTHH:mm`; si se omite se usa la fecha/hora actual.
- `weatherProvider` opcional: `open-meteo` o `mock`. Si se omite se usa el proveedor configurado en backend.

Respuesta resumida:

```json
{
  "recommendations": [
    {
      "id": "gclp-panoramic-central",
      "name": "Panoramica central de Gran Canaria",
      "description": "Ruta panoramica hacia el interior...",
      "waypoints": [
        {
          "name": "Telde",
          "latitude": 27.9955,
          "longitude": -15.4174
        }
      ],
      "flightPath": [
        {
          "name": "GCLP",
          "latitude": 27.9319,
          "longitude": -15.3866
        },
        {
          "name": "Telde",
          "latitude": 27.9955,
          "longitude": -15.4174
        }
      ],
      "sightseeingManeuvers": [
        {
          "waypointName": "Telde",
          "maneuverType": "CLOCKWISE_ORBIT",
          "minutes": 6.0,
          "radiusKm": 3.0,
          "instruction": "Realizar una orbita visual alrededor de Telde durante 6.0 minutos, radio aproximado 3.0 km."
        }
      ],
      "approximateDistanceKm": 120.5,
      "baseFlightTimeMinutes": 32.0,
      "sightseeingTimeMinutes": 8.0,
      "plannedDepartureDateTime": "2026-08-25T10:00",
      "sunAzimuthDegrees": 132.0,
      "sunExposureScore": 85.0,
      "sunExposureSummary": "Buena orientacion solar: la ruta evita tramos largos con sol frontal.",
      "estimatedTimeMinutes": 40.0,
      "estimatedTimeHours": 0.53,
      "routeDurationCategory": "GOOD_FIT",
      "estimatedFuelLiters": 18.1,
      "fuelPricePerLiter": 2.3,
      "fuelPriceSource": "MANUAL",
      "fuelPriceIsMock": false,
      "fuelTypeUsed": "AVGAS_100LL",
      "fuelPriceAirportCode": "GCLP",
      "estimatedCost": 41.63,
      "totalScore": 88.2,
      "weatherScore": 80.0,
      "weatherProvider": "mock",
      "weatherIsMock": true,
      "windKmh": 12.0,
      "cloudCoverPercent": 35.0,
      "precipitationProbability": 5.0,
      "visibilityKm": 30.0,
      "temperatureCelsius": 23.0,
      "routeWeatherSummary": {
        "averageWindKmh": 14.0,
        "maxWindKmh": 18.0,
        "averageCloudCoverPercent": 42.0,
        "maxPrecipitationProbability": 15.0,
        "minVisibilityKm": 18.0,
        "averageTemperatureCelsius": 23.5,
        "weatherScore": 80.0,
        "provider": "mock",
        "isMock": true
      },
      "scoreBreakdown": {
        "weatherScore": 80.0,
        "timeFitScore": 100.0,
        "preferenceScore": 100.0,
        "scenicScore": 88.0,
        "costScore": 100.0,
        "totalScore": 88.2
      },
      "explanation": "Esta ruta aprovecha bien el tiempo disponible...",
      "warnings": [
        "La meteorologia usada es simulada/mock"
      ]
    }
  ],
  "warnings": [],
  "debugInfo": {
    "generatedCandidateRoutes": 210,
    "discardedByTimeRoutes": 0,
    "recommendedRoutes": 5
  }
}
```

### POST /api/recommendations/debug

Endpoint de diagnostico para desarrollo. Devuelve la request recibida, `plannedDepartureDateTime`, proveedor meteorologico usado, puntos consultados para weather, avion resuelto, fuel usado, precio usado, origen del precio, tiempo util disponible, objetivo temporal, numero de waypoints compatibles, candidatas generadas, descartes, candidatas puntuadas y recomendaciones finales.

Campos destacados:

- `usefulAvailableTimeMinutes`: tiempo disponible despues de reserva y margen de seguridad. Es la base para decidir si una ruta cabe y para orientar el encaje temporal sin forzar duraciones artificiales.
- `weatherLookupPoints`: puntos de salida/intermedios usados para el resumen meteorologico multi-punto.
- `fuelTypeUsed`, `fuelPriceUsed`, `fuelPriceSource`, `fuelPriceIsMock`, `fuelPriceAirportCode`: diagnostico del precio final usado para estimar coste.
- `candidates`: candidatas evaluadas y descartadas, con `totalScore`, `timeFitScore`, `costScore`, fase de descarte y motivo cuando aplica.
- `discards`: descartes por generacion, filtro de tiempo o seleccion final.
- `recommendations`: recomendaciones finales.

## Calculos Actuales

- Distancia: Haversine desde el aeropuerto de salida, pasando por waypoints y cerrando de vuelta al aeropuerto.
- Tiempo base: distancia / velocidad de crucero, expresado en minutos.
- Tiempo escenico: minutos adicionales explicitos de observacion local sobre puntos de alto interes visual, con limites conservadores por ruta y por waypoint.
- Tiempo estimado: tiempo base + tiempo escenico.
- `flightPath`: trayectoria dibujable para el mapa. Incluye aeropuerto, waypoints, regreso y puntos intermedios de orbita cuando hay observacion escenica.
- `sightseeingManeuvers`: maniobras escenicas explicitas, con waypoint, duracion, radio e instruccion legible.
- `plannedDepartureDateTime`: fecha y hora local prevista de salida, opcional, en formato `yyyy-MM-ddTHH:mm`. Si no se envia, el backend usa la fecha/hora actual. Se usa para estimar azimut solar y penalizar rutas con tramos de sol frontal.
- `sunExposureScore`: puntuacion aproximada de orientacion solar. Se mezcla en el `totalScore` para que la misma ruta pueda subir o bajar segun la hora.
- Combustible: tiempo en horas * consumo por hora.
- Coste: combustible estimado * precio por litro.
- `usefulAvailableTimeMinutes`: tiempo disponible menos reserva recomendada del avion y margen de seguridad.
- `targetDurationMinutes`: referencia interna para `timeFitScore`. Se calcula como `usefulAvailableTimeMinutes * 0.85`, pero la seleccion final puede preferir rutas locales mas cortas si son recreativamente mas coherentes.
- Generacion dinamica: se crean rutas circulares con 1, 2 y, cuando hay al menos 90 minutos utiles, 3 o mas waypoints visuales compatibles con el aeropuerto de salida.
- Rutas entre islas: el catalogo mock incluye mas puntos de ambas islas para que una travesia pueda proponer referencias visuales cercanas durante el camino, no solo un salto directo.
- Bandas de duracion para candidatas generadas:
  - `short`: 30% a 50% del tiempo util.
  - `medium`: 50% a 75%.
  - `long`: 75% a 100%.
  - `extended`: 100% a 125%, permitidas con warning.
- Generacion por bandas: el generador intenta conservar candidatas `long`, `medium`, `extended` y `short` para evitar que la seleccion quede dominada por rutas muy cortas. Tambien mantiene variedad de tipos de ruta.
- Meteorologia por ruta recomendada: ademas del valor meteorologico representativo usado por el scoring, se calcula `routeWeatherSummary` consultando hasta 3 puntos: aeropuerto de salida, primer waypoint como waypoint principal y ultimo waypoint antes de volver. Si hay waypoints repetidos o menos puntos disponibles, se reducen las consultas.
- `routeWeatherSummary`: agrega viento medio y maximo, nubosidad media, probabilidad maxima de precipitacion, visibilidad minima, temperatura media, `weatherScore`, `provider` e `isMock`.
- `weatherScore`: se calcula desde el resumen de ruta multi-punto. Penaliza viento alto, precipitacion alta, nubosidad muy alta y visibilidad baja, y siempre se limita a 0-100.
- Scoring: combina `weatherScore`, `timeFitScore`, `preferenceScore`, interes visual y coste. Las rutas `inter-island` reciben una penalizacion por defecto salvo preferencia explicita.
- `timeFitScore`: puntua mejor las rutas cercanas a `targetDurationMinutes`, permite rutas hasta el 125% del tiempo util y descarta el encaje temporal por encima de ese margen.
- Penalizacion de rutas demasiado cortas: si `preference` no es `short`, las rutas por debajo del 40% del tiempo util penalizan mucho y las de 40%-60% penalizan moderadamente. Si `preference` es `short`, esas rutas no se penalizan por duracion.
- `routeDurationCategory`: clasifica cada recomendacion como `TOO_SHORT`, `SHORT`, `GOOD_FIT`, `LONG`, `SLIGHTLY_OVER_TIME` o `TOO_LONG` segun la proporcion entre `estimatedTimeMinutes` y `usefulAvailableTimeMinutes`.
- Seleccion final: prioriza primero rutas locales, despues encaje temporal, preferencia y score. Las rutas entre islas existen, pero no deben superar a buenas rutas locales salvo preferencia `inter-island`, `islands`, `cross-country` o `adventure`.
- Tolerancia de tiempo: se permiten rutas hasta 125% del tiempo util; por encima se descartan.
- Explicacion: indica si la ruta aprovecha poco, bien o demasiado el tiempo disponible, y muestra los minutos de observacion escenica local cuando se han anadido. La geometria operativa aparece en `flightPath`.

## Datos Mock

El MVP usa datos mock en memoria para:

- aeropuertos
- aviones
- rutas predefinidas
- waypoints visuales para rutas generadas
- precios de combustible
- precios mock por aeropuerto y tipo de combustible (`GCLP`, `GCTS`, `GCXO`; `AVGAS_100LL`, `JET_A1`, `MOGAS`)
- meteorologia simulada por defecto; opcionalmente Open-Meteo con `weather.provider=open-meteo`

No hay base de datos ni integraciones externas reales todavia.

## Limitaciones Actuales

- Sin METAR/TAF reales, OpenAIP ni PostGIS.
- Sin persistencia.
- Sin restricciones reales de espacio aereo.
- Sin validacion aeronautica profesional.
- Sin navegacion ni planificacion operacional.
- Catalogo pequeno de aeropuertos, aviones, rutas y waypoints visuales.
- Meteorologia mock por defecto; Open-Meteo es opcional y no debe usarse como fuente aeronautica operacional. Precios por aeropuerto simulados/mock salvo precio manual del usuario.

## METAR/TAF Futuro

METAR/TAF queda solo en roadmap. En una fase posterior se evaluara AviationWeather para obtener METAR del aeropuerto de salida, TAF del aeropuerto de salida si existe y METAR/TAF de aeropuertos cercanos o alternativos. No hay integracion METAR/TAF implementada en este MVP.

## Proximos Pasos

Ver [docs/todo.md](docs/todo.md).
