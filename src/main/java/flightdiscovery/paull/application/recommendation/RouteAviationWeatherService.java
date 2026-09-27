package flightdiscovery.paull.application.recommendation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import flightdiscovery.paull.domain.aviationweather.AviationWeatherClient;
import flightdiscovery.paull.domain.aviationweather.AviationWeatherException;
import flightdiscovery.paull.domain.model.Airport;
import flightdiscovery.paull.domain.model.AirportAviationWeather;
import flightdiscovery.paull.domain.model.FlightRoute;
import flightdiscovery.paull.domain.model.MetarReport;
import flightdiscovery.paull.domain.model.RouteAviationWeatherSummary;
import flightdiscovery.paull.domain.model.TafReport;
import flightdiscovery.paull.domain.model.Waypoint;
import flightdiscovery.paull.domain.repository.MockAirportRepository;

@Service
public class RouteAviationWeatherService {

    private static final double RELEVANT_AIRPORT_DISTANCE_KM = 55.0;
    private static final int MAX_AIRPORTS_PER_ROUTE = 3;
    private static final String SOURCE_URL = "https://aviationweather.gov/data/api/";

    private final AviationWeatherClient aviationWeatherClient;
    private final MockAirportRepository airportRepository;

    public RouteAviationWeatherService(
            AviationWeatherClient aviationWeatherClient,
            MockAirportRepository airportRepository
    ) {
        this.aviationWeatherClient = aviationWeatherClient;
        this.airportRepository = airportRepository;
    }

    public RouteAviationWeatherSummary aviationWeatherSummary(FlightRoute route, Airport departureAirport) {
        List<Airport> relevantAirports = relevantAirports(route, departureAirport);
        List<AirportAviationWeather> airportWeather = relevantAirports.stream()
                .map(airport -> airportWeather(route, departureAirport, airport))
                .toList();
        List<String> warnings = airportWeather.stream()
                .flatMap(airport -> airport.warnings().stream())
                .distinct()
                .toList();

        return new RouteAviationWeatherSummary(
                AviationWeatherClient.PROVIDER,
                SOURCE_URL,
                false,
                airportWeather,
                warnings
        );
    }

    private AirportAviationWeather airportWeather(FlightRoute route, Airport departureAirport, Airport airport) {
        List<String> warnings = new ArrayList<>();
        MetarReport metar = null;
        TafReport taf = null;

        try {
            metar = aviationWeatherClient.latestMetar(airport.code()).orElse(null);
            if (metar == null) {
                warnings.add("Sin METAR reciente para " + airport.code());
            }
        } catch (AviationWeatherException exception) {
            warnings.add("No se pudo consultar METAR para " + airport.code());
        }

        try {
            taf = aviationWeatherClient.latestTaf(airport.code()).orElse(null);
            if (taf == null) {
                warnings.add("Sin TAF disponible para " + airport.code());
            }
        } catch (AviationWeatherException exception) {
            warnings.add("No se pudo consultar TAF para " + airport.code());
        }

        return new AirportAviationWeather(
                airport.code(),
                airport.name(),
                airport.latitude(),
                airport.longitude(),
                round(distanceFromRouteKm(route, departureAirport, airport)),
                metar,
                taf,
                taf != null,
                warnings
        );
    }

    private List<Airport> relevantAirports(FlightRoute route, Airport departureAirport) {
        Map<String, Airport> airports = new LinkedHashMap<>();
        airports.put(departureAirport.code(), departureAirport);

        airportRepository.findAll().stream()
                .filter(airport -> !airport.code().equalsIgnoreCase(departureAirport.code()))
                .map(airport -> new AirportDistance(airport, distanceFromRouteKm(route, departureAirport, airport)))
                .filter(airportDistance -> airportDistance.distanceKm() <= RELEVANT_AIRPORT_DISTANCE_KM)
                .sorted(Comparator.comparingDouble(AirportDistance::distanceKm))
                .limit(MAX_AIRPORTS_PER_ROUTE - 1L)
                .forEach(airportDistance -> airports.put(airportDistance.airport().code(), airportDistance.airport()));

        return airports.values().stream().toList();
    }

    private double distanceFromRouteKm(FlightRoute route, Airport departureAirport, Airport airport) {
        List<Waypoint> routePoints = routePoints(route, departureAirport);

        return routePoints.stream()
                .mapToDouble(point -> distanceKm(point.latitude(), point.longitude(), airport.latitude(), airport.longitude()))
                .min()
                .orElse(distanceKm(departureAirport.latitude(), departureAirport.longitude(), airport.latitude(), airport.longitude()));
    }

    private List<Waypoint> routePoints(FlightRoute route, Airport departureAirport) {
        List<Waypoint> routePoints = new ArrayList<>();
        routePoints.add(new Waypoint(departureAirport.code(), departureAirport.latitude(), departureAirport.longitude()));
        routePoints.addAll(route.waypoints());

        return routePoints;
    }

    private double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double radiusKm = 6371.0;
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return radiusKm * c;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record AirportDistance(
            Airport airport,
            double distanceKm
    ) {
    }
}
