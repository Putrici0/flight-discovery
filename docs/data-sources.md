# Data Sources

El MVP usa datos mock en memoria por defecto. La unica integracion externa opcional es Open-Meteo para meteorologia orientativa cuando se activa `weather.provider=open-meteo` o cuando la request envia `weatherProvider=open-meteo`.

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

Uso actual:

- Generar rutas circulares de uno o dos waypoints.
- Crear candidatas por bandas de duracion.
- Mantener variedad entre rutas costeras, de montana, panoramicas y alternativas.
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

## Integraciones Futuras

### Aeropuertos y Espacio Aereo

- Fuentes de aeropuertos y aerodromos con coordenadas, pistas y metadatos.
- OpenAIP u otra fuente para espacio aereo y restricciones.
- PostGIS para consultas geoespaciales cuando exista persistencia.

### Meteorologia Real

- Open-Meteo ya esta preparado para una primera meteorologia basica orientativa.
- METAR/TAF para aeropuertos que lo soporten queda para una fase posterior.
- Reglas de evaluacion de viento, visibilidad, techo y fenomenos relevantes.

### METAR/TAF Roadmap

En una fase posterior se integrara AviationWeather para obtener:

- METAR del aeropuerto de salida.
- TAF del aeropuerto de salida si existe.
- METAR/TAF de aeropuertos cercanos o alternativos.

No hay integracion METAR/TAF real en esta fase.

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
