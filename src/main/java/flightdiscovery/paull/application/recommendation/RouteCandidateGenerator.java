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
    private static final double TARGET_DURATION_RATIO = 0.85;
    private static final int MAX_GENERATED_ROUTES = 30;
    private static final int MIN_TIME_FOR_THREE_OR_MORE_WAYPOINT_ROUTES = 90;
    private static final int MAX_THREE_OR_MORE_WAYPOINT_CANDIDATES = 40;
    private static final int MIN_TIME_FOR_EXTENDED_WAYPOINT_ROUTES = 180;
    private static final int MAX_EXTENDED_WAYPOINT_CANDIDATES = 80;
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
                .filter(waypoint -> isInterIslandPreference(preference) || !isInterIslandWaypoint(waypoint))
                .sorted(waypointComparator(preference))
                .toList();
        LOGGER.info("Recommendation diagnostics: visualWaypoints={} departureAirport={} usefulFlightTimeMinutes={}",
                prioritizedWaypoints.size(), departureAirport.code(), round(availableTimeMinutes));

        List<FlightRoute> candidates = new ArrayList<>();
        candidates.addAll(singleWaypointRoutes(departureAirport, prioritizedWaypoints));
        candidates.addAll(twoWaypointRoutes(departureAirport, prioritizedWaypoints));
        candidates.addAll(threeOrMoreWaypointRoutes(departureAirport, prioritizedWaypoints, availableTimeMinutes));
        candidates.addAll(extendedWaypointRoutes(departureAirport, prioritizedWaypoints, availableTimeMinutes, cruiseSpeedKmh, preference));

        List<RouteCandidateDiscard> discardedCandidates = new ArrayList<>();
        List<TimedRouteCandidate> timeViableCandidates = new ArrayList<>();
        for (FlightRoute candidate : candidates) {
            double estimatedTimeMinutes = estimatedTimeMinutes(candidate, cruiseSpeedKmh);
            double limitMinutes = availableTimeMinutes * MAX_ALLOWED_TIME_OVERRUN_RATIO;

            if (estimatedTimeMinutes <= limitMinutes) {
                timeViableCandidates.add(new TimedRouteCandidate(candidate, estimatedTimeMinutes, bandFor(estimatedTimeMinutes, availableTimeMinutes)));
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

        List<TimedRouteCandidate> limitedCandidates = limitCandidates(timeViableCandidates, preference, availableTimeMinutes);
        List<FlightRoute> limitedRoutes = limitedCandidates.stream()
                .map(TimedRouteCandidate::route)
                .toList();
        SetUtils.notSelected(timeViableCandidates, limitedCandidates).stream()
                .map(route -> new RouteCandidateDiscard(
                        route.route(),
                        CANDIDATE_LIMIT_DISCARD_REASON,
                        round(route.estimatedTimeMinutes()),
                        round(availableTimeMinutes * MAX_ALLOWED_TIME_OVERRUN_RATIO)
                ))
                .forEach(discardedCandidates::add);
        LOGGER.info("Recommendation diagnostics: returnedGeneratedCandidatesAfterLimit={}", limitedRoutes.size());

        return new RouteGenerationResult(
                limitedRoutes,
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

    private List<FlightRoute> threeOrMoreWaypointRoutes(
            Airport departureAirport,
            List<VisualWaypoint> waypoints,
            double availableTimeMinutes
    ) {
        if (availableTimeMinutes < MIN_TIME_FOR_THREE_OR_MORE_WAYPOINT_ROUTES) {
            return List.of();
        }

        List<FlightRoute> routes = new ArrayList<>();

        for (int i = 0; i < waypoints.size(); i++) {
            for (int j = i + 1; j < waypoints.size(); j++) {
                for (int k = j + 1; k < waypoints.size(); k++) {
                    routes.add(threeWaypointRoute(departureAirport, List.of(waypoints.get(i), waypoints.get(j), waypoints.get(k))));

                    if (routes.size() >= MAX_THREE_OR_MORE_WAYPOINT_CANDIDATES) {
                        return routes;
                    }
                }
            }
        }

        return routes;
    }

    private List<FlightRoute> extendedWaypointRoutes(
            Airport departureAirport,
            List<VisualWaypoint> waypoints,
            double availableTimeMinutes,
            double cruiseSpeedKmh,
            String preference
    ) {
        if (availableTimeMinutes < MIN_TIME_FOR_EXTENDED_WAYPOINT_ROUTES || waypoints.size() < 4) {
            return List.of();
        }

        List<TimedRouteCandidate> routes = new ArrayList<>();

        for (int i = 0; i < waypoints.size(); i++) {
            for (int j = i + 1; j < waypoints.size(); j++) {
                for (int k = j + 1; k < waypoints.size(); k++) {
                    for (int l = k + 1; l < waypoints.size(); l++) {
                        List<VisualWaypoint> orderedWaypoints = extendedRouteOrder(
                                departureAirport,
                                List.of(waypoints.get(i), waypoints.get(j), waypoints.get(k), waypoints.get(l))
                        );
                        FlightRoute route = multiWaypointRoute(departureAirport, orderedWaypoints);
                        double estimatedTimeMinutes = estimatedTimeMinutes(route, cruiseSpeedKmh);
                        routes.add(new TimedRouteCandidate(route, estimatedTimeMinutes, bandFor(estimatedTimeMinutes, availableTimeMinutes)));
                    }
                }
            }
        }

        return routes.stream()
                .filter(route -> route.estimatedTimeMinutes() <= availableTimeMinutes * MAX_ALLOWED_TIME_OVERRUN_RATIO)
                .sorted(routeComparator(preference, availableTimeMinutes))
                .limit(MAX_EXTENDED_WAYPOINT_CANDIDATES)
                .map(TimedRouteCandidate::route)
                .toList();
    }

    private List<VisualWaypoint> extendedRouteOrder(Airport departureAirport, List<VisualWaypoint> waypoints) {
        List<VisualWaypoint> byDistanceDescending = waypoints.stream()
                .sorted(Comparator.comparingDouble(
                        (VisualWaypoint waypoint) -> distanceFromDepartureAirport(departureAirport, waypoint)
                ).reversed())
                .toList();
        List<VisualWaypoint> byDistanceAscending = byDistanceDescending.reversed();
        List<VisualWaypoint> orderedWaypoints = new ArrayList<>();

        for (int i = 0; i < byDistanceDescending.size(); i++) {
            VisualWaypoint waypoint = i % 2 == 0
                    ? byDistanceDescending.get(i / 2)
                    : byDistanceAscending.get(i / 2);

            if (!orderedWaypoints.contains(waypoint)) {
                orderedWaypoints.add(waypoint);
            }
        }

        byDistanceDescending.stream()
                .filter(waypoint -> !orderedWaypoints.contains(waypoint))
                .forEach(orderedWaypoints::add);

        return orderedWaypoints;
    }

    private List<TimedRouteCandidate> limitCandidates(
            List<TimedRouteCandidate> candidates,
            String preference,
            double availableTimeMinutes
    ) {
        if (preference == null || preference.isBlank()) {
            return balancedCandidates(candidates, preference, availableTimeMinutes);
        }

        List<TimedRouteCandidate> preferredRoutes = candidates.stream()
                .filter(candidate -> routeMatchesPreference(candidate.route(), preference))
                .toList();
        List<TimedRouteCandidate> alternativeRoutes = candidates.stream()
                .filter(candidate -> !routeMatchesPreference(candidate.route(), preference))
                .toList();
        int preferredTarget = (int) Math.round(MAX_GENERATED_ROUTES * PREFERRED_ROUTE_RATIO);
        int alternativeTarget = MAX_GENERATED_ROUTES - preferredTarget;

        List<TimedRouteCandidate> selectedRoutes = new ArrayList<>();
        addRoutes(selectedRoutes, balancedCandidates(preferredRoutes, preference, availableTimeMinutes), preferredTarget);
        addRoutes(selectedRoutes, balancedCandidates(alternativeRoutes, preference, availableTimeMinutes), alternativeTarget);
        addRoutes(selectedRoutes, balancedCandidates(preferredRoutes, preference, availableTimeMinutes), MAX_GENERATED_ROUTES - selectedRoutes.size());
        addRoutes(selectedRoutes, balancedCandidates(alternativeRoutes, preference, availableTimeMinutes), MAX_GENERATED_ROUTES - selectedRoutes.size());

        return ensureRouteTypeVariety(selectedRoutes, candidates, preference, availableTimeMinutes);
    }

    private List<TimedRouteCandidate> balancedCandidates(
            List<TimedRouteCandidate> candidates,
            String preference,
            double availableTimeMinutes
    ) {
        List<TimedRouteCandidate> selectedRoutes = new ArrayList<>();
        int longTarget = Math.max(1, (int) Math.round(MAX_GENERATED_ROUTES * 0.40));
        int mediumTarget = Math.max(1, (int) Math.round(MAX_GENERATED_ROUTES * 0.25));
        int extendedTarget = Math.max(1, (int) Math.round(MAX_GENERATED_ROUTES * 0.20));
        int shortTarget = MAX_GENERATED_ROUTES - longTarget - mediumTarget - extendedTarget;

        addBandRoutes(selectedRoutes, candidates, DurationBand.LONG, longTarget, preference, availableTimeMinutes);
        addBandRoutes(selectedRoutes, candidates, DurationBand.MEDIUM, mediumTarget, preference, availableTimeMinutes);
        addBandRoutes(selectedRoutes, candidates, DurationBand.EXTENDED, extendedTarget, preference, availableTimeMinutes);
        addBandRoutes(selectedRoutes, candidates, DurationBand.SHORT, shortTarget, preference, availableTimeMinutes);
        addRoutes(
                selectedRoutes,
                sortedCandidates(candidates, preference, availableTimeMinutes),
                MAX_GENERATED_ROUTES - selectedRoutes.size()
        );

        return ensureRouteTypeVariety(selectedRoutes, candidates, preference, availableTimeMinutes);
    }

    private List<TimedRouteCandidate> ensureRouteTypeVariety(
            List<TimedRouteCandidate> selectedRoutes,
            List<TimedRouteCandidate> candidates,
            String preference,
            double availableTimeMinutes
    ) {
        List<TimedRouteCandidate> variedRoutes = new ArrayList<>(selectedRoutes);
        ensureBand(variedRoutes, candidates, DurationBand.LONG, preference, availableTimeMinutes);
        ensureBand(variedRoutes, candidates, DurationBand.MEDIUM, preference, availableTimeMinutes);
        ensureBand(variedRoutes, candidates, DurationBand.EXTENDED, preference, availableTimeMinutes);
        ensureBand(variedRoutes, candidates, DurationBand.SHORT, preference, availableTimeMinutes);
        ensureRouteType(variedRoutes, candidates, RouteType.GENERATED_ONE_WAYPOINT, preference, availableTimeMinutes);
        ensureRouteType(variedRoutes, candidates, RouteType.GENERATED_TWO_WAYPOINTS, preference, availableTimeMinutes);
        ensureRouteType(variedRoutes, candidates, RouteType.GENERATED_THREE_OR_MORE_WAYPOINTS, preference, availableTimeMinutes);

        return variedRoutes;
    }

    private void ensureBand(
            List<TimedRouteCandidate> selectedRoutes,
            List<TimedRouteCandidate> candidates,
            DurationBand band,
            String preference,
            double availableTimeMinutes
    ) {
        boolean alreadySelected = selectedRoutes.stream()
                .anyMatch(candidate -> candidate.band() == band);
        if (alreadySelected) {
            return;
        }

        TimedRouteCandidate replacement = sortedCandidates(candidates, preference, availableTimeMinutes).stream()
                .filter(candidate -> candidate.band() == band)
                .findFirst()
                .orElse(null);
        if (replacement == null) {
            return;
        }

        addOrReplaceLast(selectedRoutes, replacement);
    }

    private void ensureRouteType(
            List<TimedRouteCandidate> selectedRoutes,
            List<TimedRouteCandidate> candidates,
            RouteType routeType,
            String preference,
            double availableTimeMinutes
    ) {
        boolean alreadySelected = selectedRoutes.stream()
                .anyMatch(candidate -> candidate.route().routeType() == routeType);
        if (alreadySelected) {
            return;
        }

        TimedRouteCandidate replacement = sortedCandidates(candidates, preference, availableTimeMinutes).stream()
                .filter(candidate -> candidate.route().routeType() == routeType)
                .findFirst()
                .orElse(null);
        if (replacement == null) {
            return;
        }

        addOrReplaceLast(selectedRoutes, replacement);
    }

    private void addOrReplaceLast(List<TimedRouteCandidate> selectedRoutes, TimedRouteCandidate replacement) {
        if (selectedRoutes.stream().anyMatch(selected -> selected.route().id().equals(replacement.route().id()))) {
            return;
        }

        if (selectedRoutes.size() < MAX_GENERATED_ROUTES) {
            selectedRoutes.add(replacement);
            return;
        }
        selectedRoutes.removeLast();
        selectedRoutes.add(replacement);
    }

    private void addBandRoutes(
            List<TimedRouteCandidate> selectedRoutes,
            List<TimedRouteCandidate> candidates,
            DurationBand band,
            int limit,
            String preference,
            double availableTimeMinutes
    ) {
        addRoutes(
                selectedRoutes,
                sortedCandidates(candidates, preference, availableTimeMinutes).stream()
                        .filter(candidate -> candidate.band() == band)
                        .toList(),
                limit
        );
    }

    private void addRoutes(List<TimedRouteCandidate> selectedRoutes, List<TimedRouteCandidate> candidates, int limit) {
        if (limit <= 0) {
            return;
        }

        candidates.stream()
                .filter(candidate -> selectedRoutes.stream().noneMatch(selected -> selected.route().id().equals(candidate.route().id())))
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

    private FlightRoute threeWaypointRoute(Airport departureAirport, List<VisualWaypoint> waypoints) {
        return multiWaypointRoute(departureAirport, waypoints);
    }

    private FlightRoute multiWaypointRoute(Airport departureAirport, List<VisualWaypoint> waypoints) {
        List<String> tags = StreamUtils.distinctTags(waypoints);
        double scenicScore = waypoints.stream()
                .mapToDouble(VisualWaypoint::scenicValue)
                .average()
                .orElse(0.0);
        String waypointIds = waypoints.stream()
                .map(VisualWaypoint::id)
                .reduce((first, second) -> first + "-" + second)
                .orElse("route");
        String waypointNames = waypoints.stream()
                .map(VisualWaypoint::name)
                .reduce((first, second) -> first + ", " + second)
                .orElse("");

        return new FlightRoute(
                "generated-three-plus-" + departureAirport.code().toLowerCase() + "-" + waypointIds,
                (waypoints.size() > 3 ? "Circular extendida a " : "Circular larga a ") + waypointNames,
                "Ruta circular generada desde " + departureAirport.code()
                        + " hacia " + waypointNames + " y regreso al aeropuerto de salida.",
                RouteType.GENERATED_THREE_OR_MORE_WAYPOINTS,
                departureAirport,
                waypoints.stream()
                        .map(waypoint -> new Waypoint(waypoint.name(), waypoint.latitude(), waypoint.longitude()))
                        .toList(),
                tags,
                0.0,
                0.0,
                scenicScore
        );
    }


    private boolean fitsAvailableTime(FlightRoute route, double availableTimeMinutes, double cruiseSpeedKmh) {
        return estimatedTimeMinutes(route, cruiseSpeedKmh) <= availableTimeMinutes * MAX_ALLOWED_TIME_OVERRUN_RATIO;
    }

    private double distanceFromDepartureAirport(Airport departureAirport, VisualWaypoint waypoint) {
        return routeCalculationService.haversineKm(
                departureAirport.latitude(),
                departureAirport.longitude(),
                waypoint.latitude(),
                waypoint.longitude()
        );
    }

    private DurationBand bandFor(double estimatedTimeMinutes, double availableTimeMinutes) {
        if (availableTimeMinutes <= 0.0) {
            return DurationBand.TOO_SHORT;
        }

        double usageRatio = estimatedTimeMinutes / availableTimeMinutes;

        if (usageRatio >= 0.3 && usageRatio < 0.5) {
            return DurationBand.SHORT;
        }

        if (usageRatio >= 0.5 && usageRatio < 0.75) {
            return DurationBand.MEDIUM;
        }

        if (usageRatio >= 0.75 && usageRatio <= 1.0) {
            return DurationBand.LONG;
        }

        if (usageRatio > 1.0 && usageRatio <= MAX_ALLOWED_TIME_OVERRUN_RATIO) {
            return DurationBand.EXTENDED;
        }

        return DurationBand.TOO_SHORT;
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

    private List<TimedRouteCandidate> sortedCandidates(
            List<TimedRouteCandidate> candidates,
            String preference,
            double availableTimeMinutes
    ) {
        return candidates.stream()
                .sorted(routeComparator(preference, availableTimeMinutes))
                .toList();
    }

    private Comparator<TimedRouteCandidate> routeComparator(String preference, double availableTimeMinutes) {
        return Comparator
                .comparingInt((TimedRouteCandidate candidate) -> routeExperiencePriority(candidate.route(), preference))
                .thenComparing(candidate -> candidate.band().priority())
                .thenComparingDouble(candidate -> timeFitDistance(candidate, availableTimeMinutes))
                .thenComparing(Comparator.comparing(
                        (TimedRouteCandidate candidate) -> routeMatchesPreference(candidate.route(), preference)
                ).reversed())
                .thenComparing(candidate -> candidate.route().scenicScore(), Comparator.reverseOrder())
                .thenComparing(candidate -> candidate.route().waypoints().size());
    }

    private int routeExperiencePriority(FlightRoute route, String preference) {
        if (!isInterIslandRoute(route) || isInterIslandPreference(preference)) {
            return 0;
        }

        return 1;
    }

    private boolean isInterIslandRoute(FlightRoute route) {
        return route.tags().stream()
                .anyMatch(tag -> tag.equalsIgnoreCase("inter-island") || tag.equalsIgnoreCase("islands"));
    }

    private boolean isInterIslandWaypoint(VisualWaypoint waypoint) {
        return waypoint.tags().stream()
                .anyMatch(tag -> tag.equalsIgnoreCase("inter-island") || tag.equalsIgnoreCase("islands"));
    }

    private boolean isInterIslandPreference(String preference) {
        if (preference == null || preference.isBlank()) {
            return false;
        }

        String normalizedPreference = preference.trim().toLowerCase();

        return normalizedPreference.equals("inter-island")
                || normalizedPreference.equals("islands")
                || normalizedPreference.equals("cross-country")
                || normalizedPreference.equals("adventure");
    }

    private double timeFitDistance(TimedRouteCandidate candidate, double availableTimeMinutes) {
        if (availableTimeMinutes <= 0.0) {
            return Double.MAX_VALUE;
        }

        return Math.abs(candidate.estimatedTimeMinutes() / availableTimeMinutes - TARGET_DURATION_RATIO);
    }

    private enum DurationBand {
        LONG(0),
        MEDIUM(1),
        EXTENDED(2),
        SHORT(3),
        TOO_SHORT(4);

        private final int priority;

        DurationBand(int priority) {
            this.priority = priority;
        }

        private int priority() {
            return priority;
        }
    }

    private record TimedRouteCandidate(
            FlightRoute route,
            double estimatedTimeMinutes,
            DurationBand band
    ) {
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

        private static List<String> distinctTags(List<VisualWaypoint> waypoints) {
            List<String> tags = new ArrayList<>();
            waypoints.stream()
                    .flatMap(waypoint -> waypoint.tags().stream())
                    .filter(tag -> !tags.contains(tag))
                    .forEach(tags::add);

            return tags;
        }
    }

    private static final class SetUtils {

        private SetUtils() {
        }

        private static List<TimedRouteCandidate> notSelected(
                List<TimedRouteCandidate> candidates,
                List<TimedRouteCandidate> selectedRoutes
        ) {
            return candidates.stream()
                    .filter(candidate -> selectedRoutes.stream().noneMatch(selected -> selected.route().id().equals(candidate.route().id())))
                    .toList();
        }
    }
}
