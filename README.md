# Flight Discovery

Flight Discovery sera una web responsive para recomendar rutas aereas recreativas a pilotos privados.

El sistema partira de un aeropuerto de salida y combinara tiempo disponible, avion, combustible, meteorologia basica y preferencias del usuario para proponer rutas viables y atractivas.

## Backend

El backend minimo esta en `src/main/java/flightdiscovery/paull`.

Para ejecutarlo:

```bash
mvn spring-boot:run
```

Endpoint disponible:

```http
GET /api/health
```

Respuesta:

```json
{"status":"ok"}
```

Tambien existe un endpoint de recomendaciones con datos mock:

```http
POST /api/recommendations
```

Ejemplo de peticion:

```json
{
  "departureAirport": "GCLP",
  "availableFlightTimeMinutes": 120,
  "aircraftId": "cessna-172",
  "cruiseSpeedKmh": 220,
  "fuelBurnLitersPerHour": 35,
  "fuelPricePerLiter": 2.3,
  "preference": "coast"
}
```

Devuelve hasta 3 rutas recomendadas con distancia, tiempo, combustible, coste, score y explicacion.

## Estructura inicial

- `src/main/java/flightdiscovery/paull/`: codigo principal del backend.
- `src/test/java/flightdiscovery/paull/`: pruebas automatizadas.
- `src/main/resources/`: recursos de Spring Boot.
- `docs/`: documentacion de producto y arquitectura.
- `pom.xml`: configuracion Maven del proyecto.

## Estado

Proyecto en fase inicial con una API REST minima y recomendaciones basadas en datos mock. No incluye base de datos, meteorologia real ni frontend.
