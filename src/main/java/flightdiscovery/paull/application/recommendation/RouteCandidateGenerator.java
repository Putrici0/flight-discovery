package flightdiscovery.paull.application.recommendation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
    private static final int MIN_TIME_FOR_THREE_OR_MORE_WAYPOINT_ROUTES = 90;
    private static final int MAX_THREE_OR_MORE_WAYPOINT_CANDIDATES = 220;
    private static final int MIN_TIME_FOR_EXTENDED_WAYPOINT_ROUTES = 180;
    private static final int MAX_EXTENDED_WAYPOINT_CANDIDATES = 260;
    private static final int MAX_CORRIDOR_ENRICHED_ROUTES = 140;
    private static final int MAX_CORRIDOR_WAYPOINTS = 5;
    private static final double MAX_CORRIDOR_DISTANCE_INCREASE_RATIO = 0.28;
    private static final double CORRIDOR_DETOUR_LIMIT_KM = 18.0;
    private static final double MIN_WAYPOINT_SEPARATION_KM = 3.0;
    private static final double MAX_LOW_VALUE_DETOUR_KM = 18.0;
    private static final List<String> LANDSCAPE_TAGS = List.of(
            "coast",
            "mountain",
            "ravine",
            "village",
            "historic",
            "beach",
            "forest",
            "volcanic",
            "panoramic"
    );
    private static final String TIME_DISCARD_REASON = "Estimated route time exceeds useful available time plus 25% tolerance";
    private static final String CANDIDATE_LIMIT_DISCARD_REASON = "Not selected after dynamic candidate preference and diversity limit";

    private final MockWaypointRepository waypointRepository;
    private final RouteCalculationService routeCalculationService;
    private final RouteCandidateSelectionService routeCandidateSelectionService;

    public RouteCandidateGenerator(
            MockWaypointRepository waypointRepository,
            RouteCalculationService routeCalculationService,
            RouteCandidateSelectionService routeCandidateSelectionService
    ) {
        this.waypointRepository = waypointRepository;
        this.routeCalculationService = routeCalculationService;
        this.routeCandidateSelectionService = routeCandidateSelectionService;
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
        List<VisualWaypoint> prioritizedWaypoints = removeNearDuplicateWaypoints(waypointRepository.findAll().stream()
                .filter(waypoint -> isCompatibleWithDepartureAirport(waypoint, departureAirport))
                .filter(waypoint -> isInterIslandPreference(preference) || !isInterIslandWaypoint(waypoint))
                .sorted(waypointComparator(preference))
                .toList());
        LOGGER.info("Recommendation diagnostics: visualWaypoints={} departureAirport={} usefulFlightTimeMinutes={}",
                prioritizedWaypoints.size(), departureAirport.code(), round(availableTimeMinutes));

        List<FlightRoute> candidates = new ArrayList<>();
        candidates.addAll(singleWaypointRoutes(departureAirport, prioritizedWaypoints));
        candidates.addAll(corridorEnrichedRoutes(departureAirport, prioritizedWaypoints, availableTimeMinutes, cruiseSpeedKmh));
        candidates.addAll(twoWaypointRoutes(departureAirport, prioritizedWaypoints));
        candidates.addAll(threeOrMoreWaypointRoutes(departureAirport, prioritizedWaypoints, availableTimeMinutes));
        candidates.addAll(extendedWaypointRoutes(departureAirport, prioritizedWaypoints, availableTimeMinutes, cruiseSpeedKmh, preference));
        candidates = uniqueRoutes(candidates);

        List<RouteCandidateDiscard> discardedCandidates = new ArrayList<>();
        List<GeneratedRouteCandidate> timeViableCandidates = new ArrayList<>();
        for (FlightRoute candidate : candidates) {
            double estimatedTimeMinutes = estimatedTimeMinutes(candidate, cruiseSpeedKmh);
            double limitMinutes = availableTimeMinutes * MAX_ALLOWED_TIME_OVERRUN_RATIO;

            if (estimatedTimeMinutes <= limitMinutes) {
                timeViableCandidates.add(routeCandidateSelectionService.candidate(candidate, estimatedTimeMinutes, availableTimeMinutes));
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

        List<GeneratedRouteCandidate> limitedCandidates = routeCandidateSelectionService.limitCandidates(timeViableCandidates, preference, availableTimeMinutes);
        List<FlightRoute> limitedRoutes = limitedCandidates.stream()
                .map(GeneratedRouteCandidate::route)
                .toList();
        routeCandidateSelectionService.notSelected(timeViableCandidates, limitedCandidates).stream()
                .map(candidate -> new RouteCandidateDiscard(
                        candidate.route(),
                        routeCandidateSelectionService.isTooSimilarToSelected(candidate, limitedCandidates)
                                ? "Too similar to a selected generated candidate"
                                : CANDIDATE_LIMIT_DISCARD_REASON,
                        round(candidate.estimatedTimeMinutes()),
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

    private List<FlightRoute> corridorEnrichedRoutes(
            Airport departureAirport,
            List<VisualWaypoint> waypoints,
            double availableTimeMinutes,
            double cruiseSpeedKmh
    ) {
        List<FlightRoute> routes = new ArrayList<>();

        for (VisualWaypoint destination : waypoints) {
            List<VisualWaypoint> enrichedWaypoints = enrichedWaypointsForDestination(departureAirport, destination, waypoints);
            if (enrichedWaypoints.size() <= 1) {
                continue;
            }

            FlightRoute route = multiWaypointRoute(departureAirport, enrichedWaypoints, "generated-corridor-");
            if (coherentRoute(departureAirport, enrichedWaypoints)
                    && estimatedTimeMinutes(route, cruiseSpeedKmh) <= availableTimeMinutes * MAX_ALLOWED_TIME_OVERRUN_RATIO) {
                routes.add(route);
            }

            if (routes.size() >= MAX_CORRIDOR_ENRICHED_ROUTES) {
                return routes;
            }
        }

        return routes;
    }

    private List<VisualWaypoint> enrichedWaypointsForDestination(
            Airport departureAirport,
            VisualWaypoint destination,
            List<VisualWaypoint> waypoints
    ) {
        List<VisualWaypoint> selectedWaypoints = new ArrayList<>();
        selectedWaypoints.add(destination);

        double directDistanceKm = roundTripDistanceKm(departureAirport, List.of(destination));
        List<VisualWaypoint> nearbyWaypoints = waypoints.stream()
                .filter(waypoint -> !waypoint.id().equals(destination.id()))
                .filter(waypoint -> detourToOutboundOrReturnLegKm(departureAirport, destination, waypoint) <= CORRIDOR_DETOUR_LIMIT_KM)
                .sorted(Comparator
                        .comparingDouble((VisualWaypoint waypoint) -> detourToOutboundOrReturnLegKm(departureAirport, destination, waypoint))
                        .thenComparing(VisualWaypoint::scenicValue, Comparator.reverseOrder()))
                .toList();

        for (VisualWaypoint waypoint : nearbyWaypoints) {
            List<VisualWaypoint> candidateWaypoints = new ArrayList<>(selectedWaypoints);
            candidateWaypoints.add(waypoint);
            List<VisualWaypoint> orderedCandidateWaypoints = routeOrder(departureAirport, candidateWaypoints);
            double enrichedDistanceKm = roundTripDistanceKm(departureAirport, orderedCandidateWaypoints);

            if (enrichedDistanceKm <= directDistanceKm * (1.0 + MAX_CORRIDOR_DISTANCE_INCREASE_RATIO)) {
                selectedWaypoints = orderedCandidateWaypoints;
            }

            if (selectedWaypoints.size() >= MAX_CORRIDOR_WAYPOINTS) {
                break;
            }
        }

        return routeOrder(departureAirport, selectedWaypoints);
    }

    private double detourToOutboundOrReturnLegKm(Airport departureAirport, VisualWaypoint destination, VisualWaypoint waypoint) {
        double outboundDetourKm = routeCalculationService.haversineKm(
                departureAirport.latitude(),
                departureAirport.longitude(),
                waypoint.latitude(),
                waypoint.longitude()
        ) + routeCalculationService.haversineKm(
                waypoint.latitude(),
                waypoint.longitude(),
                destination.latitude(),
                destination.longitude()
        ) - routeCalculationService.haversineKm(
                departureAirport.latitude(),
                departureAirport.longitude(),
                destination.latitude(),
                destination.longitude()
        );

        return Math.max(0.0, outboundDetourKm);
    }

    private List<FlightRoute> twoWaypointRoutes(Airport departureAirport, List<VisualWaypoint> waypoints) {
        List<FlightRoute> routes = new ArrayList<>();

        for (int i = 0; i < waypoints.size(); i++) {
            for (int j = i + 1; j < waypoints.size(); j++) {
                List<VisualWaypoint> orderedWaypoints = routeOrder(departureAirport, List.of(waypoints.get(i), waypoints.get(j)));
                if (coherentRoute(departureAirport, orderedWaypoints)) {
                    routes.add(twoWaypointRoute(departureAirport, orderedWaypoints.get(0), orderedWaypoints.get(1)));
                }
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

        for (List<VisualWaypoint> waypointSet : geographicWaypointSets(departureAirport, waypoints, 3, 3, MAX_THREE_OR_MORE_WAYPOINT_CANDIDATES)) {
            if (coherentRoute(departureAirport, waypointSet)) {
                routes.add(threeWaypointRoute(departureAirport, waypointSet));
            }

            if (routes.size() >= MAX_THREE_OR_MORE_WAYPOINT_CANDIDATES) {
                return routes;
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

        for (List<VisualWaypoint> waypointSet : geographicWaypointSets(departureAirport, waypoints, 4, 5, MAX_EXTENDED_WAYPOINT_CANDIDATES * 3)) {
            if (coherentRoute(departureAirport, waypointSet)) {
                FlightRoute route = multiWaypointRoute(departureAirport, waypointSet);
                double estimatedTimeMinutes = estimatedTimeMinutes(route, cruiseSpeedKmh);
                routes.add(new TimedRouteCandidate(route, estimatedTimeMinutes, bandFor(estimatedTimeMinutes, availableTimeMinutes)));
            }
        }

        return routes.stream()
                .filter(route -> route.estimatedTimeMinutes() <= availableTimeMinutes * MAX_ALLOWED_TIME_OVERRUN_RATIO)
                .sorted(routeComparator(preference, availableTimeMinutes))
                .limit(MAX_EXTENDED_WAYPOINT_CANDIDATES)
                .map(TimedRouteCandidate::route)
                .toList();
    }

    private List<VisualWaypoint> routeOrder(Airport departureAirport, List<VisualWaypoint> waypoints) {
        List<VisualWaypoint> remainingWaypoints = new ArrayList<>(waypoints);
        List<VisualWaypoint> orderedWaypoints = new ArrayList<>();
        double currentLatitude = departureAirport.latitude();
        double currentLongitude = departureAirport.longitude();

        while (!remainingWaypoints.isEmpty()) {
            double fromLatitude = currentLatitude;
            double fromLongitude = currentLongitude;
            VisualWaypoint nextWaypoint = remainingWaypoints.stream()
                    .min(Comparator
                            .comparingDouble((VisualWaypoint waypoint) -> routeCalculationService.haversineKm(
                                    fromLatitude,
                                    fromLongitude,
                                    waypoint.latitude(),
                                    waypoint.longitude()
                            ))
                            .thenComparing(VisualWaypoint::scenicValue, Comparator.reverseOrder()))
                    .orElseThrow();

            orderedWaypoints.add(nextWaypoint);
            remainingWaypoints.remove(nextWaypoint);
            currentLatitude = nextWaypoint.latitude();
            currentLongitude = nextWaypoint.longitude();
        }

        return orderedWaypoints;
    }

    private List<List<VisualWaypoint>> geographicWaypointSets(
            Airport departureAirport,
            List<VisualWaypoint> waypoints,
            int minWaypointCount,
            int maxWaypointCount,
            int limit
    ) {
        List<List<VisualWaypoint>> waypointSets = new ArrayList<>();
        Set<String> signatures = new LinkedHashSet<>();

        for (String tag : LANDSCAPE_TAGS) {
            List<VisualWaypoint> taggedWaypoints = waypoints.stream()
                    .filter(waypoint -> waypoint.tags().contains(tag))
                    .sorted(Comparator
                            .comparingDouble((VisualWaypoint waypoint) -> bearingFromDepartureAirport(departureAirport, waypoint))
                            .thenComparing(VisualWaypoint::scenicValue, Comparator.reverseOrder()))
                    .toList();
            addSlidingWindowSets(departureAirport, taggedWaypoints, minWaypointCount, maxWaypointCount, limit, waypointSets, signatures);
        }

        List<VisualWaypoint> byBearing = waypoints.stream()
                .sorted(Comparator.comparingDouble(waypoint -> bearingFromDepartureAirport(departureAirport, waypoint)))
                .toList();
        addSlidingWindowSets(departureAirport, byBearing, minWaypointCount, maxWaypointCount, limit, waypointSets, signatures);

        List<VisualWaypoint> byDistance = waypoints.stream()
                .sorted(Comparator.comparingDouble(waypoint -> distanceFromDepartureAirport(departureAirport, waypoint)))
                .toList();
        addSlidingWindowSets(departureAirport, byDistance, minWaypointCount, maxWaypointCount, limit, waypointSets, signatures);

        return waypointSets;
    }

    private void addSlidingWindowSets(
            Airport departureAirport,
            List<VisualWaypoint> waypoints,
            int minWaypointCount,
            int maxWaypointCount,
            int limit,
            List<List<VisualWaypoint>> waypointSets,
            Set<String> signatures
    ) {
        if (waypoints.size() < minWaypointCount) {
            return;
        }

        for (int waypointCount = minWaypointCount; waypointCount <= maxWaypointCount; waypointCount++) {
            if (waypoints.size() < waypointCount) {
                continue;
            }

            for (int start = 0; start <= waypoints.size() - waypointCount; start++) {
                List<VisualWaypoint> orderedWaypoints = routeOrder(
                        departureAirport,
                        waypoints.subList(start, start + waypointCount)
                );
                String signature = waypointSignature(orderedWaypoints);
                if (signatures.add(signature) && coherentRoute(departureAirport, orderedWaypoints)) {
                    waypointSets.add(orderedWaypoints);
                }

                if (waypointSets.size() >= limit) {
                    return;
                }
            }
        }
    }

    private List<VisualWaypoint> removeNearDuplicateWaypoints(List<VisualWaypoint> waypoints) {
        List<VisualWaypoint> selectedWaypoints = new ArrayList<>();

        for (VisualWaypoint waypoint : waypoints) {
            boolean nearDuplicate = selectedWaypoints.stream()
                    .anyMatch(selectedWaypoint -> routeCalculationService.haversineKm(
                            waypoint.latitude(),
                            waypoint.longitude(),
                            selectedWaypoint.latitude(),
                            selectedWaypoint.longitude()
                    ) < MIN_WAYPOINT_SEPARATION_KM
                            && sharesLandscapeTag(waypoint, selectedWaypoint));
            if (!nearDuplicate) {
                selectedWaypoints.add(waypoint);
            }
        }

        return selectedWaypoints;
    }

    private boolean coherentRoute(Airport departureAirport, List<VisualWaypoint> waypoints) {
        if (waypoints.size() <= 1) {
            return true;
        }

        if (hasNearDuplicateWaypoints(waypoints)) {
            return false;
        }

        double routeDistanceKm = roundTripDistanceKm(departureAirport, waypoints);
        double farthestRoundTripKm = waypoints.stream()
                .mapToDouble(waypoint -> distanceFromDepartureAirport(departureAirport, waypoint))
                .max()
                .orElse(0.0) * 2.0;
        double scenicScore = waypoints.stream()
                .mapToDouble(VisualWaypoint::scenicValue)
                .average()
                .orElse(0.0);
        double allowedDistanceKm = farthestRoundTripKm * 1.45
                + (scenicScore >= 85.0 ? 28.0 : MAX_LOW_VALUE_DETOUR_KM);

        return routeDistanceKm <= allowedDistanceKm;
    }

    private boolean hasNearDuplicateWaypoints(List<VisualWaypoint> waypoints) {
        for (int i = 0; i < waypoints.size(); i++) {
            for (int j = i + 1; j < waypoints.size(); j++) {
                if (routeCalculationService.haversineKm(
                        waypoints.get(i).latitude(),
                        waypoints.get(i).longitude(),
                        waypoints.get(j).latitude(),
                        waypoints.get(j).longitude()
                ) < MIN_WAYPOINT_SEPARATION_KM) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean sharesLandscapeTag(VisualWaypoint firstWaypoint, VisualWaypoint secondWaypoint) {
        return firstWaypoint.tags().stream()
                .filter(LANDSCAPE_TAGS::contains)
                .anyMatch(secondWaypoint.tags()::contains);
    }

    private List<FlightRoute> uniqueRoutes(List<FlightRoute> routes) {
        Set<String> signatures = new LinkedHashSet<>();

        return routes.stream()
                .filter(route -> signatures.add(route.waypoints().stream()
                        .map(waypoint -> waypoint.name().toLowerCase())
                        .reduce((first, second) -> first + "|" + second)
                        .orElse(route.id())))
                .toList();
    }

    private String waypointSignature(List<VisualWaypoint> waypoints) {
        return waypoints.stream()
                .map(VisualWaypoint::id)
                .sorted()
                .reduce((first, second) -> first + "|" + second)
                .orElse("empty");
    }

    private FlightRoute singleWaypointRoute(Airport departureAirport, VisualWaypoint waypoint) {
        return new FlightRoute(
                "generated-one-" + departureAirport.code().toLowerCase() + "-" + waypoint.id(),
                "Ruta visual a " + waypoint.name(),
                "Ruta visual generada desde " + departureAirport.code()
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
                "Ruta visual por " + firstWaypoint.name() + " y " + secondWaypoint.name(),
                "Ruta visual generada desde " + departureAirport.code()
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
        return multiWaypointRoute(departureAirport, waypoints, "generated-three-plus-");
    }

    private FlightRoute multiWaypointRoute(Airport departureAirport, List<VisualWaypoint> waypoints) {
        return multiWaypointRoute(departureAirport, waypoints, "generated-three-plus-");
    }

    private FlightRoute multiWaypointRoute(Airport departureAirport, List<VisualWaypoint> waypoints, String idPrefix) {
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
                idPrefix + departureAirport.code().toLowerCase() + "-" + waypointIds,
                routeNamePrefix(idPrefix, waypoints.size()) + waypointNames,
                "Ruta visual generada desde " + departureAirport.code()
                        + " pasando por " + waypointNames + " y regreso al aeropuerto de salida.",
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

    private String routeNamePrefix(String idPrefix, int waypointCount) {
        if (idPrefix.equals("generated-corridor-")) {
            return "Ruta escenica por corredor: ";
        }

        return waypointCount > 3 ? "Ruta escenica extendida: " : "Ruta escenica: ";
    }

    private double roundTripDistanceKm(Airport departureAirport, List<VisualWaypoint> waypoints) {
        FlightRoute route = new FlightRoute(
                "distance-check",
                "Distance check",
                "Distance check",
                RouteType.GENERATED_THREE_OR_MORE_WAYPOINTS,
                departureAirport,
                waypoints.stream()
                        .map(waypoint -> new Waypoint(waypoint.name(), waypoint.latitude(), waypoint.longitude()))
                        .toList(),
                List.of(),
                0.0,
                0.0,
                0.0
        );

        return routeCalculationService.totalDistanceKm(route);
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

    private double bearingFromDepartureAirport(Airport departureAirport, VisualWaypoint waypoint) {
        double fromLatitude = Math.toRadians(departureAirport.latitude());
        double toLatitude = Math.toRadians(waypoint.latitude());
        double longitudeDelta = Math.toRadians(waypoint.longitude() - departureAirport.longitude());
        double y = Math.sin(longitudeDelta) * Math.cos(toLatitude);
        double x = Math.cos(fromLatitude) * Math.sin(toLatitude)
                - Math.sin(fromLatitude) * Math.cos(toLatitude) * Math.cos(longitudeDelta);

        return (Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0;
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

}
