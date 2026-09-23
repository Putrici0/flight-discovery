package flightdiscovery.paull.application.recommendation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import flightdiscovery.paull.api.recommendation.SightseeingManeuverResponse;
import flightdiscovery.paull.domain.model.FlightRoute;
import flightdiscovery.paull.domain.model.Waypoint;

@Service
public class SightseeingService {

    private static final double LOCAL_ROUTE_SIGHTSEEING_MIN_SCENIC_SCORE = 85.0;
    private static final double SIGHTSEEING_TARGET_USEFUL_TIME_RATIO = 0.85;
    private static final double SIGHTSEEING_AVAILABLE_MARGIN_RATIO = 0.65;
    private static final double MAX_SIGHTSEEING_ROUTE_RATIO = 0.85;
    private static final double MAX_SIGHTSEEING_MINUTES_PER_WAYPOINT = 12.0;
    private static final double MAX_TOTAL_SIGHTSEEING_MINUTES = 30.0;
    private static final double SIGHTSEEING_ORBIT_MIN_RADIUS_KM = 0.8;
    private static final double SIGHTSEEING_ORBIT_MAX_RADIUS_KM = 3.0;
    private static final int SIGHTSEEING_ORBIT_SEGMENTS = 16;

    private final SunExposureService sunExposureService;

    public SightseeingService(SunExposureService sunExposureService) {
        this.sunExposureService = sunExposureService;
    }

    public double sightseeingTimeMinutes(FlightRoute route, double baseFlightTimeMinutes, double usefulFlightTimeMinutes) {
        if (isInterIslandRoute(route)
                || normalizedScenicScore(route) < LOCAL_ROUTE_SIGHTSEEING_MIN_SCENIC_SCORE
                || usefulFlightTimeMinutes <= 0.0
                || baseFlightTimeMinutes <= 0.0) {
            return 0.0;
        }

        double targetComfortableDurationMinutes = usefulFlightTimeMinutes * SIGHTSEEING_TARGET_USEFUL_TIME_RATIO;
        double missingMinutes = (targetComfortableDurationMinutes - baseFlightTimeMinutes) * SIGHTSEEING_AVAILABLE_MARGIN_RATIO;
        if (missingMinutes <= 0.0) {
            return 0.0;
        }

        double waypointLimitMinutes = route.waypoints().size() * MAX_SIGHTSEEING_MINUTES_PER_WAYPOINT;
        double routeRatioLimitMinutes = baseFlightTimeMinutes * MAX_SIGHTSEEING_ROUTE_RATIO;

        return Math.min(missingMinutes, Math.min(MAX_TOTAL_SIGHTSEEING_MINUTES, Math.min(waypointLimitMinutes, routeRatioLimitMinutes)));
    }

    public List<Waypoint> flightPath(
            FlightRoute route,
            Waypoint departureAirport,
            List<SightseeingManeuverResponse> sightseeingManeuvers
    ) {
        List<Waypoint> flightPath = new ArrayList<>();
        flightPath.add(departureAirport);

        route.waypoints().forEach(waypoint -> {
            flightPath.add(waypoint);
            SightseeingManeuverResponse maneuver = sightseeingManeuverFor(waypoint, sightseeingManeuvers);
            if (maneuver != null) {
                flightPath.addAll(maneuver.orbitPath());
                flightPath.add(waypoint);
            }
        });

        flightPath.add(departureAirport);

        return flightPath;
    }

    public List<SightseeingManeuverResponse> sightseeingManeuvers(
            FlightRoute route,
            double sightseeingTimeMinutes,
            double cruiseSpeedKmh,
            double sunAzimuthDegrees
    ) {
        if (sightseeingTimeMinutes <= 0.0) {
            return List.of();
        }

        List<Waypoint> sightseeingWaypoints = sightseeingWaypoints(route);
        double sightseeingMinutesPerWaypoint = sightseeingTimeMinutes / sightseeingWaypoints.size();

        return sightseeingWaypoints.stream()
                .map(waypoint -> {
                    double radiusKm = sightseeingOrbitRadiusKm(sightseeingMinutesPerWaypoint, cruiseSpeedKmh);
                    double preferredViewingBearingDegrees = sunExposureService.preferredViewingBearingDegrees(sunAzimuthDegrees);
                    List<Waypoint> orbitPath = sightseeingOrbit(waypoint, radiusKm, preferredViewingBearingDegrees);
                    return new SightseeingManeuverResponse(
                            waypoint.name(),
                            "SUN_ORIENTED_CLOCKWISE_ORBIT",
                            round(sightseeingMinutesPerWaypoint),
                            round(radiusKm),
                            round(sunAzimuthDegrees),
                            round(preferredViewingBearingDegrees),
                            orbitPath,
                            "Realizar una orbita visual orientada por sol alrededor de " + waypoint.name()
                                    + " durante " + round(sightseeingMinutesPerWaypoint)
                                    + " minutos, radio aproximado " + round(radiusKm)
                                    + " km, iniciando por el sector " + round(preferredViewingBearingDegrees)
                                    + " grados para mantener el sol lateral y mejorar la observacion."
                    );
                })
                .toList();
    }

    private List<Waypoint> sightseeingWaypoints(FlightRoute route) {
        if (route.waypoints().isEmpty()) {
            return List.of();
        }

        Map<String, Waypoint> selectedWaypoints = new LinkedHashMap<>();
        addUniquePoint(selectedWaypoints, route.waypoints().getFirst());
        addUniquePoint(selectedWaypoints, route.waypoints().getLast());

        return selectedWaypoints.values().stream()
                .limit(2)
                .toList();
    }

    private SightseeingManeuverResponse sightseeingManeuverFor(
            Waypoint waypoint,
            List<SightseeingManeuverResponse> sightseeingManeuvers
    ) {
        return sightseeingManeuvers.stream()
                .filter(maneuver -> maneuver.waypointName().equals(waypoint.name()))
                .findFirst()
                .orElse(null);
    }

    private double sightseeingOrbitRadiusKm(double sightseeingMinutes, double cruiseSpeedKmh) {
        double orbitDistanceKm = cruiseSpeedKmh * sightseeingMinutes / 60.0;

        return Math.max(
                SIGHTSEEING_ORBIT_MIN_RADIUS_KM,
                Math.min(SIGHTSEEING_ORBIT_MAX_RADIUS_KM, orbitDistanceKm / (2.0 * Math.PI))
        );
    }

    private List<Waypoint> sightseeingOrbit(Waypoint center, double radiusKm, double startBearingDegrees) {
        List<Waypoint> orbit = new ArrayList<>();

        for (int segment = 0; segment <= SIGHTSEEING_ORBIT_SEGMENTS; segment++) {
            double angle = Math.toRadians(startBearingDegrees) + 2.0 * Math.PI * segment / SIGHTSEEING_ORBIT_SEGMENTS;
            double latitudeOffset = radiusKm * Math.cos(angle) / 111.32;
            double longitudeScale = 111.32 * Math.cos(Math.toRadians(center.latitude()));
            double longitudeOffset = longitudeScale == 0.0 ? 0.0 : radiusKm * Math.sin(angle) / longitudeScale;

            orbit.add(new Waypoint(
                    center.name() + " scenic orbit",
                    center.latitude() + latitudeOffset,
                    center.longitude() + longitudeOffset
            ));
        }

        return orbit;
    }

    private void addUniquePoint(Map<String, Waypoint> points, Waypoint point) {
        points.putIfAbsent(point.latitude() + ":" + point.longitude(), point);
    }

    private double normalizedScenicScore(FlightRoute route) {
        if (route.scenicScore() <= 10.0) {
            return route.scenicScore() * 10.0;
        }

        return Math.min(100.0, route.scenicScore());
    }

    private boolean isInterIslandRoute(FlightRoute route) {
        return route.tags().stream()
                .anyMatch(tag -> tag.equalsIgnoreCase("inter-island") || tag.equalsIgnoreCase("islands"));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
