# Data Sources

El MVP usa datos mock en memoria. No hay integraciones externas activas ni base de datos.

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

### Combustible

Repositorio mock:

- Tipo de combustible.
- Precio por litro.
- Fuente `MOCK`.

Uso actual:

- Si la request no incluye `fuelPricePerLiter`, se usa el precio mock del tipo de combustible del avion.
- Si la request incluye precio manual, la respuesta marca `fuelPriceSource` como `MANUAL`.

### Meteorologia

Servicio mock:

- Puntuacion meteorologica.
- Viento.
- Nubosidad.
- Probabilidad de precipitacion.
- Visibilidad.

Uso actual:

- Alimentar `weatherScore`.
- Mostrar campos meteorologicos en la respuesta.
- Anadir warning indicando que la meteorologia es simulada.

## Integraciones Futuras

### Aeropuertos y Espacio Aereo

- Fuentes de aeropuertos y aerodromos con coordenadas, pistas y metadatos.
- OpenAIP u otra fuente para espacio aereo y restricciones.
- PostGIS para consultas geoespaciales cuando exista persistencia.

### Meteorologia Real

- Open-Meteo para una primera meteorologia basica.
- METAR/TAF para aeropuertos que lo soporten.
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
