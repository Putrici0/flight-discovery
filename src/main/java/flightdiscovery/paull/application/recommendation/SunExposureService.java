package flightdiscovery.paull.application.recommendation;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import flightdiscovery.paull.domain.model.FlightRoute;
import flightdiscovery.paull.domain.model.Waypoint;

@Service
public class SunExposureService {

    public double sunAzimuthDegrees(String plannedDepartureDateTime) {
        int minutes = localTimeMinutes(plannedDepartureDateTime);
        if (minutes < 420 || minutes > 1200) {
            return 0.0;
        }

        double daylightProgress = (minutes - 420.0) / (1200.0 - 420.0);

        return 90.0 + daylightProgress * 180.0;
    }

    public double sunExposureScore(FlightRoute route, Waypoint departureAirport, double sunAzimuthDegrees, String plannedDepartureDateTime) {
        int minutes = localTimeMinutes(plannedDepartureDateTime);
        if (minutes < 420 || minutes > 1200) {
            return 15.0;
        }

        List<Waypoint> points = new ArrayList<>();
        points.add(departureAirport);
        points.addAll(route.waypoints());
        points.add(departureAirport);

        return pointsForLegs(points).stream()
                .mapToDouble(leg -> legSunExposureScore(leg.first(), leg.second(), sunAzimuthDegrees))
                .average()
                .orElse(60.0);
    }

    public double preferredViewingBearingDegrees(double sunAzimuthDegrees) {
        if (sunAzimuthDegrees == 0.0) {
            return 0.0;
        }

        return (sunAzimuthDegrees + 90.0) % 360.0;
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

    private List<Leg> pointsForLegs(List<Waypoint> points) {
        List<Leg> legs = new ArrayList<>();
        for (int i = 0; i < points.size() - 1; i++) {
            legs.add(new Leg(points.get(i), points.get(i + 1)));
        }

        return legs;
    }

    private double legSunExposureScore(Waypoint from, Waypoint to, double sunAzimuthDegrees) {
        double bearing = bearingDegrees(from, to);
        double angle = Math.abs(bearing - sunAzimuthDegrees);
        double smallestAngle = Math.min(angle, 360.0 - angle);

        if (smallestAngle < 25.0) {
            return 25.0;
        }

        if (smallestAngle < 45.0) {
            return 45.0;
        }

        if (smallestAngle < 80.0) {
            return 75.0;
        }

        return 95.0;
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

    private int localTimeMinutes(String plannedDepartureDateTime) {
        String localTime = plannedDepartureDateTime.contains("T")
                ? plannedDepartureDateTime.substring(plannedDepartureDateTime.indexOf('T') + 1)
                : plannedDepartureDateTime;
        String[] parts = localTime.split(":");

        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }

    private record Leg(
            Waypoint first,
            Waypoint second
    ) {
    }
}
