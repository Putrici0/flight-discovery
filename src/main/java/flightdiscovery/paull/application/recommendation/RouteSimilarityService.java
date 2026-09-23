package flightdiscovery.paull.application.recommendation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import flightdiscovery.paull.domain.calculation.RouteCalculationService;
import flightdiscovery.paull.domain.model.FlightRoute;
import flightdiscovery.paull.domain.model.Waypoint;

@Service
public class RouteSimilarityService {

    private static final double SIMILARITY_THRESHOLD = 0.62;
    private static final double NEARBY_WAYPOINT_DISTANCE_KM = 8.0;
    private static final double CLOSE_GEOMETRY_DISTANCE_KM = 14.0;

    private final RouteCalculationService routeCalculationService;

    public RouteSimilarityService(RouteCalculationService routeCalculationService) {
        this.routeCalculationService = routeCalculationService;
    }

    public boolean areTooSimilar(FlightRoute firstRoute, FlightRoute secondRoute) {
        return similarity(firstRoute, secondRoute) >= SIMILARITY_THRESHOLD;
    }

    public double similarity(FlightRoute firstRoute, FlightRoute secondRoute) {
        if (firstRoute.id().equals(secondRoute.id())) {
            return 1.0;
        }

        double sharedWaypointScore = sharedWaypointScore(firstRoute, secondRoute);
        double nearbyWaypointScore = nearbyWaypointScore(firstRoute.waypoints(), secondRoute.waypoints());
        double geometryScore = geometryScore(firstRoute.waypoints(), secondRoute.waypoints());

        return clampScore(sharedWaypointScore * 0.50 + nearbyWaypointScore * 0.30 + geometryScore * 0.20);
    }

    private double sharedWaypointScore(FlightRoute firstRoute, FlightRoute secondRoute) {
        Set<String> firstWaypoints = waypointNames(firstRoute);
        Set<String> secondWaypoints = waypointNames(secondRoute);
        if (firstWaypoints.isEmpty() || secondWaypoints.isEmpty()) {
            return 0.0;
        }

        Set<String> intersection = new HashSet<>(firstWaypoints);
        intersection.retainAll(secondWaypoints);

        Set<String> union = new HashSet<>(firstWaypoints);
        union.addAll(secondWaypoints);

        return (double) intersection.size() / union.size();
    }

    private Set<String> waypointNames(FlightRoute route) {
        Set<String> names = new HashSet<>();
        route.waypoints().stream()
                .map(waypoint -> waypoint.name().trim().toLowerCase())
                .forEach(names::add);

        return names;
    }

    private double nearbyWaypointScore(List<Waypoint> firstWaypoints, List<Waypoint> secondWaypoints) {
        if (firstWaypoints.isEmpty() || secondWaypoints.isEmpty()) {
            return 0.0;
        }

        long nearbyMatches = firstWaypoints.stream()
                .filter(first -> secondWaypoints.stream()
                        .anyMatch(second -> distanceKm(first, second) <= NEARBY_WAYPOINT_DISTANCE_KM))
                .count();

        return (double) nearbyMatches / Math.max(firstWaypoints.size(), secondWaypoints.size());
    }

    private double geometryScore(List<Waypoint> firstWaypoints, List<Waypoint> secondWaypoints) {
        if (firstWaypoints.isEmpty() || secondWaypoints.isEmpty()) {
            return 0.0;
        }

        double firstToSecond = averageNearestDistanceKm(firstWaypoints, secondWaypoints);
        double secondToFirst = averageNearestDistanceKm(secondWaypoints, firstWaypoints);
        double averageNearestDistanceKm = (firstToSecond + secondToFirst) / 2.0;

        return Math.max(0.0, 1.0 - averageNearestDistanceKm / CLOSE_GEOMETRY_DISTANCE_KM);
    }

    private double averageNearestDistanceKm(List<Waypoint> sourceWaypoints, List<Waypoint> targetWaypoints) {
        return sourceWaypoints.stream()
                .mapToDouble(source -> targetWaypoints.stream()
                        .mapToDouble(target -> distanceKm(source, target))
                        .min()
                        .orElse(CLOSE_GEOMETRY_DISTANCE_KM))
                .average()
                .orElse(CLOSE_GEOMETRY_DISTANCE_KM);
    }

    private double distanceKm(Waypoint first, Waypoint second) {
        return routeCalculationService.haversineKm(
                first.latitude(),
                first.longitude(),
                second.latitude(),
                second.longitude()
        );
    }

    private double clampScore(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
