# Data Sources

El MVP usa datos mock en memoria por defecto. Las integraciones externas actuales son Open-Meteo para meteorologia general orientativa y AviationWeather.gov para METAR/TAF aeroportuario informativo.

## Datos Actuales

### Aeropuertos

Repositorio mock:

- Codigo ICAO.
- Nombre.
- Latitud y longitud.

Uso actual:

- Validar el aeropuerto de salida.
- Calcular distancias Haversine.
- Filtrar waypoints compatibles con el aeropuerto.

### Aviones

Repositorio mock:

- Identificador.
- Nombre.
- Velocidad de crucero.
- Consumo por hora.
- Tipo de combustible.
- Autonomia maxima.
- Reserva recomendada en minutos.

Uso actual:

- Resolver valores por defecto cuando la request no incluye velocidad o consumo.
- Calcular tiempo util restando reserva.
- Resolver precio mock por tipo de combustible.

### Rutas Predefinidas

Repositorio mock:

- Rutas circulares desde aeropuertos conocidos.
- Waypoints.
- Tags de preferencia.
- Score visual.

Uso actual:

- Forman parte de las candidatas junto con rutas generadas dinamicamente.
- Se puntuan con el mismo scoring que las rutas generadas.

### Waypoints Visuales

Repositorio mock:

- Identificador.
- Nombre.
- Coordenadas.
- Tags.
- Valor escenico.
- Aeropuertos de salida compatibles.
- Rumbo visual preferente opcional (`preferredViewingBearingDegrees`) y pista opcional de lado de vistas (`preferredViewingSideHint`) para representar costas, acantilados o referencias que se disfrutan mejor desde una direccion concreta.

Uso actual:

- Generar rutas circulares de uno o dos waypoints.
- Crear candidatas por bandas de duracion.
- Mantener variedad entre rutas costeras, de montana, panoramicas y alternativas.
- Calcular calidad visual/orientacion por tramo cuando se puede inferir el lado desde el que conviene observar un punto visual.
- En rutas interinsulares desde GCLP, varios puntos de Tenerife son compatibles como referencias visuales de destino o tramo final para que el sistema no proponga travesias excesivamente pobres en contenido visual.

### Combustible

Repositorio mock:

- Aeropuerto ICAO.
- Tipo de combustible.
- Precio por litro.
- Moneda.
- Fuente.
- Fecha de actualizacion mock.
- Indicador `isMock`.

Uso actual:

- Si la request no incluye `fuelPricePerLiter`, se usa el precio mock por aeropuerto y tipo de combustible del avion.
- Hay datos mock para `GCLP`, `GCTS` y `GCXO`, con `AVGAS_100LL`, `JET_A1` y `MOGAS`.
- Si la request incluye precio manual, la respuesta marca `fuelPriceSource` como `MANUAL` e `fuelPriceIsMock=false`.

### Meteorologia

Servicio mock y Open-Meteo opcional:

- Puntuacion meteorologica.
- Viento.
- Nubosidad.
- Probabilidad de precipitacion.
- Visibilidad.
- Temperatura.
- Proveedor e indicador mock.

Uso actual:

- Alimentar `RouteWeatherSummary` y `weatherScore`.
- Mostrar campos meteorologicos en la respuesta.
- Anadir warning indicando que la meteorologia es simulada cuando `isMock=true`.
- Consultar hasta 3 puntos por ruta: salida, waypoint principal/intermedio y ultimo waypoint antes de volver.
- Cache en memoria por coordenadas y hora redondeadas.
- Selector por request: `weatherProvider=open-meteo` para datos reales orientativos o `weatherProvider=mock` para simulacion.
- Esta meteorologia general alimenta visualizacion, `RouteWeatherSummary` y `weatherScore`; no representa un briefing aeronautico oficial.

### Meteorologia Aeronautica METAR/TAF

Integracion separada mediante AviationWeather.gov Data API:

- Endpoint oficial documentado: `https://aviationweather.gov/api/data/metar?ids=ICAO&format=json` para METAR.
- Endpoint oficial documentado: `https://aviationweather.gov/api/data/taf?ids=ICAO&format=json` para TAF.
- Coverage mundial segun la documentacion de AviationWeather.gov, con formatos JSON/raw/XML/otros.
- La documentacion actual indica limite de 100 requests/minuto, recomienda `User-Agent` propio y avisa de `204 No Content` cuando no hay datos.

Uso actual:

- `AviationWeatherClient` consulta METAR y TAF por codigo ICAO y conserva texto raw junto con campos estructurados.
- METAR expone observacion reciente, timestamp, edad, categoria de vuelo, viento, visibilidad, altimetro, temperatura, punto de rocio, fenomenos y nubes.
- TAF expone texto raw, emision, validez, edad y periodos decodificados cuando existen.
- `RouteAviationWeatherService` consulta el aeropuerto de salida y hasta 2 aeropuertos mock cercanos/relevantes para la ruta.
- Aeropuertos sin TAF se representan con `tafAvailable=false` y warning, sin romper recomendaciones.
- Errores de red o ausencia de datos se convierten en warnings de la seccion aeronautica y no afectan al scoring ni bloquean la respuesta.
- Cache en memoria: METAR 5 minutos y TAF 10 minutos por aeropuerto.
- Flight Discovery no debe presentarse como sustituto de planificacion/briefing oficial.

## Integraciones Futuras

### Aeropuertos y Espacio Aereo

- Fuentes de aeropuertos y aerodromos con coordenadas, pistas y metadatos.
- OpenAIP u otra fuente para espacio aereo y restricciones.
- PostGIS para consultas geoespaciales cuando exista persistencia.

### Meteorologia Real

- Open-Meteo ya esta preparado para una primera meteorologia basica orientativa.
- METAR/TAF ya esta integrado como informacion aeroportuaria separada, sin mezclarse con Open-Meteo ni con `weatherScore`.
- Reglas de evaluacion de viento, visibilidad, techo y fenomenos relevantes.

### Operacion y Seguridad

- NOTAM.
- Performance real por aeronave.
- Combustible utilizable y consumos por fase.
- Restricciones VFR, minimos personales y reglas locales.

### Persistencia

- Perfiles de avion del usuario.
- Preferencias de busqueda.
- Rutas guardadas.
- Historico de recomendaciones.
