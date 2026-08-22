package flightdiscovery.paull.application.recommendation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import flightdiscovery.paull.domain.calculation.RouteCalculationService;
import flightdiscovery.paull.domain.model.Airport;
import flightdiscovery.paull.domain.model.FlightRoute;
import flightdiscovery.paull.domain.model.RouteType;
import flightdiscovery.paull.domain.model.VisualWaypoint;
import flightdiscovery.paull.domain.model.Waypoint;
import flightdiscovery.paull.domain.repository.MockWaypointRepository;

@Service
public class RouteCandidateGenerator {

    private static final double MAX_ALLOWED_TIME_OVERRUN_RATIO = 1.25;
    private static final int MAX_GENERATED_ROUTES = 30;

    private final MockWaypointRepository waypointRepository;
    private final RouteCalculationService routeCalculationService;

    public RouteCandidateGenerator(
            MockWaypointRepository waypointRepository,
            RouteCalculationService routeCalculationService
    ) {
        this.waypointRepository = waypointRepository;
        this.routeCalculationService = routeCalculationService;
    }

    public List<FlightRoute> generate(
            Airport departureAirport,
            int availableTimeMinutes,
            double cruiseSpeedKmh,
            String preference
    ) {
        List<VisualWaypoint> prioritizedWaypoints = waypointRepository.findAll().stream()
                .sorted(waypointComparator(preference))
                .toList();

        List<FlightRoute> candidates = new ArrayList<>();

        prioritizedWaypoints.stream()
                .map(waypoint -> singleWaypointRoute(departureAirport, waypoint))
                .forEach(candidates::add);

        for (int i = 0; i < prioritizedWaypoints.size(); i++) {
            for (int j = i + 1; j < prioritizedWaypoints.size(); j++) {
                candidates.add(twoWaypointRoute(departureAirport, prioritizedWaypoints.get(i), prioritizedWaypoints.get(j)));
            }
        }

        return candidates.stream()
                .filter(route -> fitsAvailableTime(route, availableTimeMinutes, cruiseSpeedKmh))
                .sorted(routeComparator(preference))
                .limit(MAX_GENERATED_ROUTES)
                .toList();
    }

    private FlightRoute singleWaypointRoute(Airport departureAirport, VisualWaypoint waypoint) {
        return new FlightRoute(
                "generated-" + waypoint.id(),
                "Circular a " + waypoint.name(),
                "Ruta circular generada desde " + departureAirport.code()
                        + " hacia " + waypoint.name() + " y regreso al aeropuerto de salida.",
                RouteType.GENERATED_ONE_WAYPOINT,
                departureAirport,
                List.of(new Waypoint(waypoint.name(), waypoint.latitude(), waypoint.longitude())),
                waypoint.tags(),
                0.0,
                0.0,
                waypoint.scenicValue()
        );
    }

    private FlightRoute twoWaypointRoute(Airport departureAirport, VisualWaypoint firstWaypoint, VisualWaypoint secondWaypoint) {
        List<String> tags = StreamUtils.distinctTags(firstWaypoint.tags(), secondWaypoint.tags());
        double scenicScore = (firstWaypoint.scenicValue() + secondWaypoint.scenicValue()) / 2.0;

        return new FlightRoute(
                "generated-" + firstWaypoint.id() + "-" + secondWaypoint.id(),
                "Circular a " + firstWaypoint.name() + " y " + secondWaypoint.name(),
                "Ruta circular generada desde " + departureAirport.code()
                        + " hacia " + firstWaypoint.name() + ", " + secondWaypoint.name()
                        + " y regreso al aeropuerto de salida.",
                RouteType.GENERATED_TWO_WAYPOINTS,
                departureAirport,
                List.of(
                        new Waypoint(firstWaypoint.name(), firstWaypoint.latitude(), firstWaypoint.longitude()),
                        new Waypoint(secondWaypoint.name(), secondWaypoint.latitude(), secondWaypoint.longitude())
                ),
                tags,
                0.0,
                0.0,
                scenicScore
        );
    }

    private boolean fitsAvailableTime(FlightRoute route, int availableTimeMinutes, double cruiseSpeedKmh) {
        double distanceKm = routeCalculationService.totalDistanceKm(route);
        double estimatedTimeHours = routeCalculationService.estimatedTimeHours(distanceKm, cruiseSpeedKmh);
        double estimatedTimeMinutes = routeCalculationService.estimatedTimeMinutes(estimatedTimeHours);

        return estimatedTimeMinutes <= availableTimeMinutes * MAX_ALLOWED_TIME_OVERRUN_RATIO;
    }

    private boolean matchesPreference(VisualWaypoint waypoint, String preference) {
        if (preference == null || preference.isBlank()) {
            return false;
        }

        return waypoint.tags().stream()
                .anyMatch(tag -> tag.equalsIgnoreCase(preference.trim()));
    }

    private boolean routeMatchesPreference(FlightRoute route, String preference) {
        if (preference == null || preference.isBlank()) {
            return false;
        }

        return route.tags().stream()
                .anyMatch(tag -> tag.equalsIgnoreCase(preference.trim()));
    }

    private Comparator<VisualWaypoint> waypointComparator(String preference) {
        return Comparator
                .comparing((VisualWaypoint waypoint) -> matchesPreference(waypoint, preference)).reversed()
                .thenComparing(VisualWaypoint::scenicValue, Comparator.reverseOrder());
    }

    private Comparator<FlightRoute> routeComparator(String preference) {
        return Comparator
                .comparing((FlightRoute route) -> routeMatchesPreference(route, preference)).reversed()
                .thenComparing(FlightRoute::scenicScore, Comparator.reverseOrder())
                .thenComparing(route -> route.waypoints().size());
    }

    private static final class StreamUtils {

        private StreamUtils() {
        }

        private static List<String> distinctTags(List<String> firstTags, List<String> secondTags) {
            List<String> tags = new ArrayList<>(firstTags);
            secondTags.stream()
                    .filter(tag -> !tags.contains(tag))
                    .forEach(tags::add);

            return tags;
        }
    }
}
