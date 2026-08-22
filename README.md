# Flight Discovery

Flight Discovery es un MVP para recomendar rutas aereas recreativas a pilotos privados usando datos mock. Parte de un aeropuerto de salida y combina tiempo disponible, avion, consumo, precio de combustible, meteorologia simulada y preferencia de ruta para proponer rutas viables y atractivas.

Este proyecto no debe usarse para planificacion aeronautica profesional. Todavia no integra meteorologia real, espacio aereo, NOTAM, performance real de aeronaves ni navegacion en tiempo real.

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
  "safetyMarginPercent": 15
}
```

Validaciones basicas:

- `departureAirport` obligatorio.
- `availableFlightTimeMinutes` mayor que 0.
- `aircraftId` debe coincidir con un avion mock conocido.
- `cruiseSpeedKmh` opcional; si se omite se usa el valor del avion.
- `fuelBurnLitersPerHour` opcional; si se omite se usa el valor del avion.
- `fuelPricePerLiter` mayor o igual que 0.
- `preference` obligatoria.
- `safetyMarginPercent` opcional; si se omite se usa 15%.

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
      "approximateDistanceKm": 120.5,
      "estimatedTimeMinutes": 32.0,
      "estimatedTimeHours": 0.53,
      "estimatedFuelLiters": 18.1,
      "fuelPricePerLiter": 2.3,
      "fuelPriceSource": "MANUAL",
      "estimatedCost": 41.63,
      "totalScore": 88.2,
      "weatherScore": 80.0,
      "windKmh": 12.0,
      "cloudCoverPercent": 35.0,
      "precipitationProbability": 5.0,
      "visibilityKm": 30.0,
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
        "La meteorologia todavia es simulada"
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

Endpoint de diagnostico para desarrollo. Devuelve la request normalizada, avion resuelto, tiempo util disponible, numero de waypoints compatibles, candidatas generadas, descartes, candidatas puntuadas y recomendaciones finales.

Campos destacados:

- `usefulAvailableTimeMinutes`: tiempo disponible despues de reserva y margen de seguridad.
- `candidates`: candidatas evaluadas y descartadas, con `totalScore`, `timeFitScore`, `costScore`, fase de descarte y motivo cuando aplica.
- `discards`: descartes por generacion, filtro de tiempo o seleccion final.
- `recommendations`: recomendaciones finales.

## Calculos Actuales

- Distancia: Haversine desde el aeropuerto de salida, pasando por waypoints y cerrando de vuelta al aeropuerto.
- Tiempo: distancia / velocidad de crucero, expresado en minutos y horas.
- Combustible: tiempo en horas * consumo por hora.
- Coste: combustible estimado * precio por litro.
- Tiempo util: tiempo disponible menos reserva recomendada del avion y margen de seguridad.
- Generacion dinamica: se crean rutas circulares de uno o dos waypoints visuales compatibles con el aeropuerto de salida.
- Bandas de duracion para candidatas generadas:
  - `short`: 30% a 50% del tiempo util.
  - `medium`: 50% a 75%.
  - `long`: 75% a 100%.
  - `extended`: 100% a 125%, permitidas con warning.
- Scoring: combina `weatherScore` simulado, `timeFitScore`, `preferenceScore`, interes visual y coste. El `totalScore` esta entre 0 y 100 y las rutas se ordenan de mayor a menor puntuacion.
- `timeFitScore`: trata el tiempo util como duracion objetivo. El objetivo es `usefulAvailableTimeMinutes * 0.85`; las rutas cercanas a ese valor puntuan mejor.
- Penalizacion de rutas demasiado cortas: si `preference` no es `short`, las rutas por debajo del 40% del tiempo util penalizan mucho y las de 40%-60% penalizan moderadamente.
- Tolerancia de tiempo: se permiten rutas hasta 125% del tiempo util; por encima se descartan.
- Explicacion: indica si la ruta aprovecha poco, bien o demasiado el tiempo disponible.

## Datos Mock

El MVP usa datos mock en memoria para:

- aeropuertos
- aviones
- rutas predefinidas
- waypoints visuales para rutas generadas
- precios de combustible
- meteorologia simulada

No hay base de datos ni integraciones externas reales todavia.

## Limitaciones Actuales

- Sin Open-Meteo, METAR/TAF, OpenAIP ni PostGIS.
- Sin persistencia.
- Sin restricciones reales de espacio aereo.
- Sin validacion aeronautica profesional.
- Sin navegacion ni planificacion operacional.
- Catalogo pequeno de aeropuertos, aviones, rutas y waypoints visuales.
- Meteorologia y precios son simulados/mock.

## Proximos Pasos

Ver [docs/todo.md](docs/todo.md).
