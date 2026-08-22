# Flight Discovery

Flight Discovery es un MVP para recomendar rutas aereas recreativas a pilotos privados usando datos mock. Parte de un aeropuerto de salida y combina tiempo disponible, avion, consumo, precio de combustible y preferencia de ruta para proponer rutas viables y atractivas.

Este proyecto no debe usarse para planificacion aeronautica profesional. Todavia no integra meteorologia real, espacio aereo, NOTAM, performance real de aeronaves ni navegacion en tiempo real.

## Requisitos

- Java 21
- Maven 3.9 o superior
- Node.js 20 o superior
- npm

## Estructura

- `src/main/java/flightdiscovery/paull/`: backend Spring Boot.
- `src/test/java/flightdiscovery/paull/`: tests del backend.
- `frontend/`: aplicacion Angular con Leaflet.
- `docs/`: documentacion y proximos pasos.
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
  "preference": "coast"
}
```

Validaciones basicas:

- `departureAirport` obligatorio.
- `availableFlightTimeMinutes` mayor que 0.
- `cruiseSpeedKmh` mayor que 0.
- `fuelBurnLitersPerHour` mayor que 0.
- `fuelPricePerLiter` mayor o igual que 0.
- `preference` obligatoria.

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
      "estimatedCost": 41.63,
      "totalScore": 88.2,
      "scoreBreakdown": {
        "weatherScore": 80.0,
        "timeFitScore": 100.0,
        "scenicScore": 88.0,
        "costScore": 100.0,
        "totalScore": 88.2
      },
      "explanation": "Esta ruta encaja bien con el tiempo disponible..."
    }
  ]
}
```

## Calculos Actuales

- Distancia: Haversine desde el aeropuerto de salida, pasando por waypoints y cerrando de vuelta al aeropuerto.
- Tiempo: distancia / velocidad de crucero, expresado en minutos y horas.
- Combustible: tiempo en horas * consumo por hora.
- Coste: combustible estimado * precio por litro.
- Scoring: combina `weatherScore` mockeado, encaje de tiempo, interes visual y coste. El `totalScore` esta entre 0 y 100 y las rutas se ordenan de mayor a menor puntuacion.

## Datos Mock

El MVP usa datos mock en memoria para:

- aeropuertos
- aviones
- rutas
- puntuacion meteorologica

No hay base de datos ni integraciones externas reales todavia.

## Limitaciones Actuales

- Sin Open-Meteo, METAR/TAF, OpenAIP ni PostGIS.
- Sin persistencia.
- Sin restricciones reales de espacio aereo.
- Sin validacion aeronautica profesional.
- Sin navegacion ni planificacion operacional.
- Catalogo muy pequeno de aeropuertos, aviones y rutas.

## Proximos Pasos

Ver [docs/todo.md](docs/todo.md).

