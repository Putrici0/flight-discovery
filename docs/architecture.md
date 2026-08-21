# Architecture

Arquitectura actual de un unico proyecto Spring Boot. El codigo del backend vive bajo `src/main/java/flightdiscovery/paull`.

## Backend

API REST encargada de validar entradas, consultar datos disponibles y calcular recomendaciones cuando se incorporen las rutas.

Estructura actual:

- `flightdiscovery.paull.Main`: arranque de Spring Boot.
- `flightdiscovery.paull.api`: capa REST.
- `flightdiscovery.paull.api.health`: endpoint de salud.

## Frontend Futuro

La web responsive se anadira mas adelante cuando el backend minimo este asentado. No hay frontend creado todavia.

Responsabilidades previstas:

- Formulario de preferencias de vuelo.
- Vista de rutas candidatas.
- Visualizacion en mapa.
- Detalle de restricciones y avisos.

## Dominio Futuro

La logica de rutas deberia separarse de la capa REST cuando se implemente.

Responsabilidades previstas:

- Gestion de aeropuertos y rutas.
- Estimacion de tiempos y distancias.
- Evaluacion basica de combustible y autonomia.
- Integracion con meteorologia basica.
- Ranking de recomendaciones.

## Datos Futuros

No hay base de datos ni carpeta de datos activa. Las fuentes externas se documentan en `docs/data-sources.md` hasta que exista una necesidad concreta de integracion.
