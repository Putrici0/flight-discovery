package flightdiscovery.paull.domain.calculation;

import java.util.List;

import org.springframework.stereotype.Service;

import flightdiscovery.paull.domain.model.FlightRoute;
import flightdiscovery.paull.domain.model.Waypoint;

@Service
public class RouteCalculationService {

    private static final double EARTH_RADIUS_KM = 6371.0;

    public double totalDistanceKm(FlightRoute route) {
        List<Waypoint> waypoints = route.waypoints();
        if (waypoints.isEmpty()) {
            return 0.0;
        }

        double totalDistance = haversineKm(
                route.departureAirport().latitude(),
                route.departureAirport().longitude(),
                waypoints.getFirst().latitude(),
                waypoints.getFirst().longitude()
        );

        for (int i = 1; i < waypoints.size(); i++) {
            Waypoint previous = waypoints.get(i - 1);
            Waypoint current = waypoints.get(i);
            totalDistance += haversineKm(
                    previous.latitude(),
                    previous.longitude(),
                    current.latitude(),
                    current.longitude()
            );
        }

        Waypoint lastWaypoint = waypoints.getLast();
        totalDistance += haversineKm(
                lastWaypoint.latitude(),
                lastWaypoint.longitude(),
                route.departureAirport().latitude(),
                route.departureAirport().longitude()
        );

        return totalDistance;
    }

    public double estimatedTimeHours(double distanceKm, double cruiseSpeedKmh) {
        if (cruiseSpeedKmh <= 0.0) {
            return 0.0;
        }

        return distanceKm / cruiseSpeedKmh;
    }

    public double estimatedTimeMinutes(double estimatedTimeHours) {
        return estimatedTimeHours * 60.0;
    }

    public double estimatedFuelLiters(double estimatedTimeMinutes, double fuelBurnLitersPerHour) {
        if (fuelBurnLitersPerHour <= 0.0) {
            return 0.0;
        }

        return estimatedTimeMinutes / 60.0 * fuelBurnLitersPerHour;
    }

    public double estimatedCost(double estimatedFuelLiters, double fuelPricePerLiter) {
        if (fuelPricePerLiter <= 0.0) {
            return 0.0;
        }

        return estimatedFuelLiters * fuelPricePerLiter;
    }

    public double haversineKm(double originLatitude, double originLongitude, double destinationLatitude, double destinationLongitude) {
        double latitudeDistance = Math.toRadians(destinationLatitude - originLatitude);
        double longitudeDistance = Math.toRadians(destinationLongitude - originLongitude);
        double originLatitudeRadians = Math.toRadians(originLatitude);
        double destinationLatitudeRadians = Math.toRadians(destinationLatitude);

        double a = Math.sin(latitudeDistance / 2.0) * Math.sin(latitudeDistance / 2.0)
                + Math.cos(originLatitudeRadians) * Math.cos(destinationLatitudeRadians)
                * Math.sin(longitudeDistance / 2.0) * Math.sin(longitudeDistance / 2.0);
        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));

        return EARTH_RADIUS_KM * c;
    }
}
