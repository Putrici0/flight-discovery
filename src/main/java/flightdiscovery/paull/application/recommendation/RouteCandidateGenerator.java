package flightdiscovery.paull.application.recommendation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(RouteCandidateGenerator.class);
    private static final double MAX_ALLOWED_TIME_OVERRUN_RATIO = 1.25;
    private static final int MAX_GENERATED_ROUTES = 30;
    private static final double PREFERRED_ROUTE_RATIO = 0.80;
    private static final String TIME_DISCARD_REASON = "Estimated route time exceeds useful available time plus 25% tolerance";
    private static final String CANDIDATE_LIMIT_DISCARD_REASON = "Not selected after dynamic candidate preference and diversity limit";

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
            double availableTimeMinutes,
            double cruiseSpeedKmh,
            String preference
    ) {
        return generateWithDebug(departureAirport, availableTimeMinutes, cruiseSpeedKmh, preference).routes();
    }

    public RouteGenerationResult generateWithDebug(
            Airport departureAirport,
            double availableTimeMinutes,
            double cruiseSpeedKmh,
            String preference
    ) {
        List<VisualWaypoint> prioritizedWaypoints = waypointRepository.findAll().stream()
                .filter(waypoint -> isCompatibleWithDepartureAirport(waypoint, departureAirport))
                .sorted(waypointComparator(preference))
                .toList();
        LOGGER.info("Recommendation diagnostics: visualWaypoints={} departureAirport={} usefulFlightTimeMinutes={}",
                prioritizedWaypoints.size(), departureAirport.code(), round(availableTimeMinutes));

        List<FlightRoute> candidates = new ArrayList<>();
        candidates.addAll(singleWaypointRoutes(departureAirport, prioritizedWaypoints));
        candidates.addAll(twoWaypointRoutes(departureAirport, prioritizedWaypoints));

        List<RouteCandidateDiscard> discardedCandidates = new ArrayList<>();
        List<FlightRoute> timeViableCandidates = new ArrayList<>();
        for (FlightRoute candidate : candidates) {
            double estimatedTimeMinutes = estimatedTimeMinutes(candidate, cruiseSpeedKmh);
            double limitMinutes = availableTimeMinutes * MAX_ALLOWED_TIME_OVERRUN_RATIO;

            if (estimatedTimeMinutes <= limitMinutes) {
                timeViableCandidates.add(candidate);
            } else {
                discardedCandidates.add(new RouteCandidateDiscard(
                        candidate,
                        TIME_DISCARD_REASON,
                        round(estimatedTimeMinutes),
                        round(limitMinutes)
                ));
            }
        }
        LOGGER.info("Recommendation diagnostics: generatedCandidates={} discardedGeneratedCandidatesByTime={} timeViableGeneratedCandidates={}",
                candidates.size(), candidates.size() - timeViableCandidates.size(), timeViableCandidates.size());

        List<FlightRoute> limitedCandidates = limitCandidates(timeViableCandidates, preference);
        SetUtils.notSelected(timeViableCandidates, limitedCandidates).stream()
                .map(route -> new RouteCandidateDiscard(
                        route,
                        CANDIDATE_LIMIT_DISCARD_REASON,
                        round(estimatedTimeMinutes(route, cruiseSpeedKmh)),
                        round(availableTimeMinutes * MAX_ALLOWED_TIME_OVERRUN_RATIO)
                ))
                .forEach(discardedCandidates::add);
        LOGGER.info("Recommendation diagnostics: returnedGeneratedCandidatesAfterLimit={}", limitedCandidates.size());

        return new RouteGenerationResult(
                limitedCandidates,
                prioritizedWaypoints.size(),
                candidates.size(),
                candidates.size() - timeViableCandidates.size(),
                discardedCandidates
        );
    }

    private List<FlightRoute> singleWaypointRoutes(Airport departureAirport, List<VisualWaypoint> waypoints) {
        return waypoints.stream()
                .map(waypoint -> singleWaypointRoute(departureAirport, waypoint))
                .toList();
    }

    private List<FlightRoute> twoWaypointRoutes(Airport departureAirport, List<VisualWaypoint> waypoints) {
        List<FlightRoute> routes = new ArrayList<>();

        for (int i = 0; i < waypoints.size(); i++) {
            for (int j = i + 1; j < waypoints.size(); j++) {
                routes.add(twoWaypointRoute(departureAirport, waypoints.get(i), waypoints.get(j)));
            }
        }

        return routes;
    }

    private List<FlightRoute> limitCandidates(List<FlightRoute> candidates, String preference) {
        if (preference == null || preference.isBlank()) {
            return candidates.stream()
                    .sorted(routeComparator(preference))
                    .limit(MAX_GENERATED_ROUTES)
                    .toList();
        }

        List<FlightRoute> preferredRoutes = candidates.stream()
                .filter(route -> routeMatchesPreference(route, preference))
                .sorted(routeComparator(preference))
                .toList();
        List<FlightRoute> alternativeRoutes = candidates.stream()
                .filter(route -> !routeMatchesPreference(route, preference))
                .sorted(routeComparator(preference))
                .toList();
        int preferredTarget = (int) Math.round(MAX_GENERATED_ROUTES * PREFERRED_ROUTE_RATIO);
        int alternativeTarget = MAX_GENERATED_ROUTES - preferredTarget;

        List<FlightRoute> selectedRoutes = new ArrayList<>();
        addRoutes(selectedRoutes, preferredRoutes, preferredTarget);
        addRoutes(selectedRoutes, alternativeRoutes, alternativeTarget);
        addRoutes(selectedRoutes, preferredRoutes, MAX_GENERATED_ROUTES - selectedRoutes.size());
        addRoutes(selectedRoutes, alternativeRoutes, MAX_GENERATED_ROUTES - selectedRoutes.size());

        return selectedRoutes;
    }

    private void addRoutes(List<FlightRoute> selectedRoutes, List<FlightRoute> candidates, int limit) {
        if (limit <= 0) {
            return;
        }

        candidates.stream()
                .filter(candidate -> selectedRoutes.stream().noneMatch(selected -> selected.id().equals(candidate.id())))
                .limit(limit)
                .forEach(selectedRoutes::add);
    }

    private FlightRoute singleWaypointRoute(Airport departureAirport, VisualWaypoint waypoint) {
        return new FlightRoute(
                "generated-one-" + departureAirport.code().toLowerCase() + "-" + waypoint.id(),
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
                "generated-two-" + departureAirport.code().toLowerCase()
                        + "-" + firstWaypoint.id() + "-" + secondWaypoint.id(),
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

    private boolean fitsAvailableTime(FlightRoute route, double availableTimeMinutes, double cruiseSpeedKmh) {
        return estimatedTimeMinutes(route, cruiseSpeedKmh) <= availableTimeMinutes * MAX_ALLOWED_TIME_OVERRUN_RATIO;
    }

    private double estimatedTimeMinutes(FlightRoute route, double cruiseSpeedKmh) {
        double distanceKm = routeCalculationService.totalDistanceKm(route);
        double estimatedTimeHours = routeCalculationService.estimatedTimeHours(distanceKm, cruiseSpeedKmh);

        return routeCalculationService.estimatedTimeMinutes(estimatedTimeHours);
    }

    private boolean isCompatibleWithDepartureAirport(VisualWaypoint waypoint, Airport departureAirport) {
        return waypoint.compatibleDepartureAirportCodes().stream()
                .anyMatch(airportCode -> airportCode.equalsIgnoreCase(departureAirport.code()));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
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

    private static final class SetUtils {

        private SetUtils() {
        }

        private static List<FlightRoute> notSelected(List<FlightRoute> candidates, List<FlightRoute> selectedRoutes) {
            return candidates.stream()
                    .filter(candidate -> selectedRoutes.stream().noneMatch(selected -> selected.id().equals(candidate.id())))
                    .toList();
        }
    }
}
