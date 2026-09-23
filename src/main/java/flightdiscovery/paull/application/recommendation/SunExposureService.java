package flightdiscovery.paull.application.recommendation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import flightdiscovery.paull.domain.model.FlightRoute;
import flightdiscovery.paull.domain.model.RouteLegOrientation;
import flightdiscovery.paull.domain.model.RouteOrientationAnalysis;
import flightdiscovery.paull.domain.model.VisualWaypoint;
import flightdiscovery.paull.domain.model.Waypoint;
import flightdiscovery.paull.domain.repository.MockWaypointRepository;

@Service
public class SunExposureService {

    private static final double DAYLIGHT_START_MINUTES = 420.0;
    private static final double DAYLIGHT_END_MINUTES = 1200.0;
    private static final double DEFAULT_VIEWING_QUALITY_SCORE = 72.0;

    private final MockWaypointRepository waypointRepository;

    public SunExposureService() {
        this(new MockWaypointRepository());
    }

    public SunExposureService(MockWaypointRepository waypointRepository) {
        this.waypointRepository = waypointRepository;
    }

    public double sunAzimuthDegrees(String plannedDepartureDateTime) {
        int minutes = localTimeMinutes(plannedDepartureDateTime);
        if (!hasUsefulDaylight(minutes)) {
            return 0.0;
        }

        double daylightProgress = (minutes - DAYLIGHT_START_MINUTES) / (DAYLIGHT_END_MINUTES - DAYLIGHT_START_MINUTES);

        return 90.0 + daylightProgress * 180.0;
    }

    public double sunExposureScore(FlightRoute route, Waypoint departureAirport, double sunAzimuthDegrees, String plannedDepartureDateTime) {
        return orientationAnalysis(route, departureAirport, sunAzimuthDegrees, plannedDepartureDateTime).sunExposureScore();
    }

    public double preferredViewingBearingDegrees(double sunAzimuthDegrees) {
        if (sunAzimuthDegrees == 0.0) {
            return 0.0;
        }

        return (sunAzimuthDegrees + 90.0) % 360.0;
    }

    public RouteOrientationAnalysis orientationAnalysis(
            FlightRoute route,
            Waypoint departureAirport,
            double sunAzimuthDegrees,
            String plannedDepartureDateTime
    ) {
        int minutes = localTimeMinutes(plannedDepartureDateTime);
        List<Waypoint> points = routePoints(route, departureAirport);
        if (!hasUsefulDaylight(minutes)) {
            List<RouteLegOrientation> legs = pointsForLegs(points).stream()
                    .map(leg -> routeLegOrientation(leg, 0.0, route, false))
                    .toList();

            return new RouteOrientationAnalysis(
                    0.0,
                    15.0,
                    visualOrientationScore(legs),
                    round(15.0 * 0.55 + visualOrientationScore(legs) * 0.45),
                    "LOW_LIGHT",
                    predominantRecommendedSide(legs),
                    "Hora con luz solar baja o nocturna; se penaliza para vuelo escenico visual.",
                    "La luz disponible es limitada para valorar orientacion visual con confianza.",
                    List.of(),
                    legs
            );
        }

        List<RouteLegOrientation> legs = pointsForLegs(points).stream()
                .map(leg -> routeLegOrientation(leg, sunAzimuthDegrees, route, true))
                .toList();
        double sunExposureScore = weightedAverage(
                legs,
                leg -> leg.distanceKm(),
                leg -> 100.0 - leg.frontalSunPenalty()
        ).orElse(60.0);
        double visualOrientationScore = visualOrientationScore(legs);
        double orientationScore = round(sunExposureScore * 0.60 + visualOrientationScore * 0.40);
        String predominantSunPosition = predominantSunPosition(legs);
        String recommendedViewingSide = predominantRecommendedSide(legs);
        List<String> frontalSunLegs = legs.stream()
                .filter(leg -> leg.frontalSunPenalty() >= 45.0)
                .map(leg -> leg.fromName() + " -> " + leg.toName())
                .toList();

        return new RouteOrientationAnalysis(
                round(sunAzimuthDegrees),
                round(sunExposureScore),
                round(visualOrientationScore),
                orientationScore,
                predominantSunPosition,
                recommendedViewingSide,
                sunExposureSummary(sunExposureScore, sunAzimuthDegrees),
                favorableReason(predominantSunPosition, recommendedViewingSide, frontalSunLegs, visualOrientationScore),
                frontalSunLegs,
                legs
        );
    }

    public String sunExposureSummary(double sunExposureScore, double sunAzimuthDegrees) {
        if (sunAzimuthDegrees == 0.0) {
            return "Hora con luz solar baja o nocturna; se penaliza para vuelo escenico visual.";
        }

        if (sunExposureScore >= 80.0) {
            return "Buena orientacion solar: la ruta evita tramos largos con sol frontal.";
        }

        if (sunExposureScore >= 55.0) {
            return "Orientacion solar aceptable, con algun tramo potencialmente incomodo.";
        }

        return "Orientacion solar desfavorable: varios tramos pueden quedar con sol frontal.";
    }

    public FlightRoute reversedRouteVariant(FlightRoute route) {
        List<Waypoint> reversedWaypoints = new ArrayList<>(route.waypoints());
        java.util.Collections.reverse(reversedWaypoints);

        return new FlightRoute(
                route.id() + "-reverse",
                route.name() + " inversa",
                route.description() + " Variante en sentido inverso para comparar orientacion solar y visual.",
                route.routeType(),
                route.departureAirport(),
                reversedWaypoints,
                route.tags(),
                route.estimatedDistanceKm(),
                route.estimatedDurationMinutes(),
                route.scenicScore()
        );
    }

    private List<Leg> pointsForLegs(List<Waypoint> points) {
        List<Leg> legs = new ArrayList<>();
        for (int i = 0; i < points.size() - 1; i++) {
            legs.add(new Leg(points.get(i), points.get(i + 1)));
        }

        return legs;
    }

    private RouteLegOrientation routeLegOrientation(
            Leg leg,
            double sunAzimuthDegrees,
            FlightRoute route,
            boolean daylight
    ) {
        Waypoint from = leg.first();
        Waypoint to = leg.second();
        double bearing = bearingDegrees(from, to);
        double relativeAngle = daylight ? signedRelativeAngleDegrees(bearing, sunAzimuthDegrees) : 0.0;
        double absoluteRelativeAngle = Math.abs(relativeAngle);
        String sunPosition = daylight ? sunPosition(relativeAngle) : "LOW_LIGHT";
        double frontalPenalty = daylight ? frontalSunPenalty(absoluteRelativeAngle) : 85.0;
        String recommendedViewingSide = recommendedViewingSide(from, to, bearing, route).orElse("UNKNOWN");
        double viewingQualityScore = viewingQualityScore(recommendedViewingSide, relativeAngle, daylight);
        String explanation = legExplanation(sunPosition, frontalPenalty, recommendedViewingSide, viewingQualityScore);

        return new RouteLegOrientation(
                from.name(),
                to.name(),
                round(distanceKm(from, to)),
                round(bearing),
                round(sunAzimuthDegrees),
                round(relativeAngle),
                sunPosition,
                round(frontalPenalty),
                recommendedViewingSide,
                round(viewingQualityScore),
                explanation
        );
    }

    private List<Waypoint> routePoints(FlightRoute route, Waypoint departureAirport) {
        List<Waypoint> points = new ArrayList<>();
        points.add(departureAirport);
        points.addAll(route.waypoints());
        points.add(departureAirport);

        return points;
    }

    private Optional<String> recommendedViewingSide(Waypoint from, Waypoint to, double aircraftBearing, FlightRoute route) {
        Optional<VisualWaypoint> targetWaypoint = waypointFor(to);
        if (targetWaypoint.isPresent()) {
            VisualWaypoint visualWaypoint = targetWaypoint.get();
            if (visualWaypoint.preferredViewingSideHint() != null && !visualWaypoint.preferredViewingSideHint().isBlank()) {
                return Optional.of(visualWaypoint.preferredViewingSideHint().trim().toUpperCase());
            }

            if (visualWaypoint.preferredViewingBearingDegrees() != null) {
                return Optional.of(sideForRelativeAngle(signedRelativeAngleDegrees(aircraftBearing, visualWaypoint.preferredViewingBearingDegrees())));
            }
        }

        if (route.tags().stream().anyMatch(tag -> tag.equalsIgnoreCase("coast"))) {
            return Optional.of(sideForRelativeAngle(signedRelativeAngleDegrees(
                    aircraftBearing,
                    bearingDegrees(to, from)
            )));
        }

        return Optional.empty();
    }

    private Optional<VisualWaypoint> waypointFor(Waypoint point) {
        return waypointRepository.findAll().stream()
                .filter(visualWaypoint -> visualWaypoint.name().equalsIgnoreCase(point.name())
                        || distanceKm(
                        new Waypoint(visualWaypoint.name(), visualWaypoint.latitude(), visualWaypoint.longitude()),
                        point
                ) < 1.5)
                .max(Comparator.comparingDouble(VisualWaypoint::scenicValue));
    }

    private double viewingQualityScore(String recommendedViewingSide, double relativeSunAngle, boolean daylight) {
        if (recommendedViewingSide.equals("UNKNOWN")) {
            return DEFAULT_VIEWING_QUALITY_SCORE;
        }

        if (!daylight) {
            return 35.0;
        }

        String sunSide = sideForRelativeAngle(relativeSunAngle);
        double frontPenalty = frontalSunPenalty(Math.abs(relativeSunAngle)) * 0.30;
        double sideScore = recommendedViewingSide.equals(sunSide)
                ? 58.0
                : sunSide.equals("BEHIND")
                ? 92.0
                : sunSide.equals("FRONT")
                ? 65.0
                : 82.0;

        return clampScore(sideScore - frontPenalty);
    }

    private double visualOrientationScore(List<RouteLegOrientation> legs) {
        return weightedAverage(
                legs,
                RouteLegOrientation::distanceKm,
                RouteLegOrientation::viewingQualityScore
        ).orElse(DEFAULT_VIEWING_QUALITY_SCORE);
    }

    private String sunPosition(double relativeAngle) {
        double absoluteRelativeAngle = Math.abs(relativeAngle);
        if (absoluteRelativeAngle <= 45.0) {
            return "FRONT";
        }

        if (absoluteRelativeAngle >= 135.0) {
            return "BEHIND";
        }

        return relativeAngle > 0.0 ? "RIGHT" : "LEFT";
    }

    private String sideForRelativeAngle(double relativeAngle) {
        double absoluteRelativeAngle = Math.abs(relativeAngle);
        if (absoluteRelativeAngle <= 35.0) {
            return "FRONT";
        }

        if (absoluteRelativeAngle >= 145.0) {
            return "BEHIND";
        }

        return relativeAngle > 0.0 ? "RIGHT" : "LEFT";
    }

    private double frontalSunPenalty(double absoluteRelativeAngle) {
        if (absoluteRelativeAngle >= 85.0) {
            return 0.0;
        }

        double frontness = (85.0 - absoluteRelativeAngle) / 85.0;

        return clampScore(Math.pow(frontness, 1.35) * 90.0);
    }

    private String predominantSunPosition(List<RouteLegOrientation> legs) {
        return weightedCategory(legs, RouteLegOrientation::sunPosition, "UNKNOWN");
    }

    private String predominantRecommendedSide(List<RouteLegOrientation> legs) {
        return weightedCategory(
                legs.stream()
                        .filter(leg -> !leg.recommendedViewingSide().equals("UNKNOWN"))
                        .toList(),
                RouteLegOrientation::recommendedViewingSide,
                "UNKNOWN"
        );
    }

    private String weightedCategory(
            List<RouteLegOrientation> legs,
            java.util.function.Function<RouteLegOrientation, String> category,
            String fallback
    ) {
        return legs.stream()
                .collect(Collectors.groupingBy(
                        category,
                        Collectors.summingDouble(RouteLegOrientation::distanceKm)
                ))
                .entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(fallback);
    }

    private Optional<Double> weightedAverage(
            List<RouteLegOrientation> legs,
            java.util.function.ToDoubleFunction<RouteLegOrientation> weight,
            java.util.function.ToDoubleFunction<RouteLegOrientation> value
    ) {
        double totalWeight = legs.stream().mapToDouble(weight).sum();
        if (totalWeight <= 0.0) {
            return Optional.empty();
        }

        double weightedValue = legs.stream()
                .mapToDouble(leg -> weight.applyAsDouble(leg) * value.applyAsDouble(leg))
                .sum();

        return Optional.of(round(weightedValue / totalWeight));
    }

    private String favorableReason(
            String predominantSunPosition,
            String recommendedViewingSide,
            List<String> frontalSunLegs,
            double visualOrientationScore
    ) {
        String sunText = switch (predominantSunPosition) {
            case "LEFT" -> "el sol queda principalmente a la izquierda";
            case "RIGHT" -> "el sol queda principalmente a la derecha";
            case "BEHIND" -> "el sol queda principalmente por detras";
            case "FRONT" -> "hay una componente frontal relevante de sol";
            default -> "la posicion solar es poco determinante";
        };
        String viewText = recommendedViewingSide.equals("UNKNOWN")
                ? "sin un lado visual preferente claro"
                : "con mejores vistas estimadas por el lado " + sideLabel(recommendedViewingSide);
        String frontalText = frontalSunLegs.isEmpty()
                ? " y sin tramos largos claramente frontales."
                : ", aunque hay tramos con sol frontal que conviene revisar.";
        String qualityText = visualOrientationScore >= 80.0
                ? " La orientacion favorece la observacion lateral."
                : visualOrientationScore >= 60.0
                ? " La orientacion visual es aceptable."
                : " La orientacion visual queda penalizada.";

        return "En esta variante " + sunText + ", " + viewText + frontalText + qualityText;
    }

    private String legExplanation(
            String sunPosition,
            double frontalPenalty,
            String recommendedViewingSide,
            double viewingQualityScore
    ) {
        String sunText = switch (sunPosition) {
            case "LEFT" -> "sol por la izquierda";
            case "RIGHT" -> "sol por la derecha";
            case "BEHIND" -> "sol por detras";
            case "FRONT" -> "sol frontal";
            default -> "luz solar baja";
        };
        String viewingText = recommendedViewingSide.equals("UNKNOWN")
                ? "sin lado visual preferente"
                : "vista recomendada por " + sideLabel(recommendedViewingSide);

        return sunText + ", " + viewingText + ", penalizacion frontal " + round(frontalPenalty)
                + ", calidad visual " + round(viewingQualityScore) + ".";
    }

    private String sideLabel(String side) {
        return switch (side) {
            case "LEFT" -> "izquierda";
            case "RIGHT" -> "derecha";
            case "FRONT" -> "frente";
            case "BEHIND" -> "detras";
            default -> side.toLowerCase();
        };
    }

    private double bearingDegrees(Waypoint from, Waypoint to) {
        double fromLatitude = Math.toRadians(from.latitude());
        double toLatitude = Math.toRadians(to.latitude());
        double longitudeDelta = Math.toRadians(to.longitude() - from.longitude());
        double y = Math.sin(longitudeDelta) * Math.cos(toLatitude);
        double x = Math.cos(fromLatitude) * Math.sin(toLatitude)
                - Math.sin(fromLatitude) * Math.cos(toLatitude) * Math.cos(longitudeDelta);

        return (Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0;
    }

    private double signedRelativeAngleDegrees(double aircraftBearingDegrees, double bearingDegrees) {
        double angle = (bearingDegrees - aircraftBearingDegrees + 540.0) % 360.0 - 180.0;

        return angle == -180.0 ? 180.0 : angle;
    }

    private double distanceKm(Waypoint from, Waypoint to) {
        double earthRadiusKm = 6371.0;
        double latitudeDelta = Math.toRadians(to.latitude() - from.latitude());
        double longitudeDelta = Math.toRadians(to.longitude() - from.longitude());
        double fromLatitude = Math.toRadians(from.latitude());
        double toLatitude = Math.toRadians(to.latitude());
        double a = Math.sin(latitudeDelta / 2.0) * Math.sin(latitudeDelta / 2.0)
                + Math.cos(fromLatitude) * Math.cos(toLatitude)
                * Math.sin(longitudeDelta / 2.0) * Math.sin(longitudeDelta / 2.0);
        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));

        return earthRadiusKm * c;
    }

    private boolean hasUsefulDaylight(int minutes) {
        return minutes >= DAYLIGHT_START_MINUTES && minutes <= DAYLIGHT_END_MINUTES;
    }

    private int localTimeMinutes(String plannedDepartureDateTime) {
        String localTime = plannedDepartureDateTime.contains("T")
                ? plannedDepartureDateTime.substring(plannedDepartureDateTime.indexOf('T') + 1)
                : plannedDepartureDateTime;
        String[] parts = localTime.split(":");

        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }

    private double clampScore(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record Leg(
            Waypoint first,
            Waypoint second
    ) {
    }
}
