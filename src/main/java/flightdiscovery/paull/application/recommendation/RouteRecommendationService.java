package flightdiscovery.paull.application.recommendation;

import java.util.Comparator;
import java.util.Locale;

import org.springframework.stereotype.Service;

import flightdiscovery.paull.api.recommendation.RecommendationRequest;
import flightdiscovery.paull.api.recommendation.RecommendationResponse;
import flightdiscovery.paull.api.recommendation.RecommendedRouteResponse;
import flightdiscovery.paull.domain.mock.MockFlightData;
import flightdiscovery.paull.domain.model.Aircraft;
import flightdiscovery.paull.domain.model.FlightRoute;
import flightdiscovery.paull.domain.model.RouteScore;

@Service
public class RouteRecommendationService {

    private static final double MOCK_WEATHER_SCORE = 8.0;

    public RecommendationResponse recommend(RecommendationRequest request) {
        double cruiseSpeedKmh = resolveCruiseSpeed(request);
        double fuelBurnLitersPerHour = resolveFuelBurn(request);

        var recommendations = MockFlightData.routes().stream()
                .filter(route -> route.departureAirport().code().equalsIgnoreCase(request.departureAirport()))
                .map(route -> toRecommendation(route, request, cruiseSpeedKmh, fuelBurnLitersPerHour))
                .sorted(Comparator.comparingDouble(RecommendedRouteResponse::totalScore).reversed())
                .limit(3)
                .toList();

        return new RecommendationResponse(recommendations);
    }

    private RecommendedRouteResponse toRecommendation(
            FlightRoute route,
            RecommendationRequest request,
            double cruiseSpeedKmh,
            double fuelBurnLitersPerHour
    ) {
        double estimatedTimeMinutes = route.estimatedDistanceKm() / cruiseSpeedKmh * 60.0;
        double estimatedFuelLiters = estimatedTimeMinutes / 60.0 * fuelBurnLitersPerHour;
        double estimatedCost = estimatedFuelLiters * request.fuelPricePerLiter();
        RouteScore score = score(route, request, estimatedTimeMinutes, estimatedCost);

        return new RecommendedRouteResponse(
                route.name(),
                route.description(),
                route.waypoints(),
                round(route.estimatedDistanceKm()),
                round(estimatedTimeMinutes),
                round(estimatedFuelLiters),
                round(estimatedCost),
                score.totalScore(),
                score,
                explanation(route, request, estimatedTimeMinutes, estimatedFuelLiters, estimatedCost, score)
        );
    }

    private RouteScore score(
            FlightRoute route,
            RecommendationRequest request,
            double estimatedTimeMinutes,
            double estimatedCost
    ) {
        double weatherScore = MOCK_WEATHER_SCORE;
        double timeFitScore = timeFitScore(estimatedTimeMinutes, request.availableFlightTimeMinutes());
        double scenicScore = preferenceAdjustedScenicScore(route, request.preference());
        double costScore = costScore(estimatedCost);
        double totalScore = weatherScore * 0.20
                + timeFitScore * 0.30
                + scenicScore * 0.30
                + costScore * 0.20;

        return new RouteScore(
                round(weatherScore),
                round(timeFitScore),
                round(scenicScore),
                round(costScore),
                round(totalScore)
        );
    }

    private double timeFitScore(double estimatedTimeMinutes, int availableTimeMinutes) {
        if (availableTimeMinutes <= 0) {
            return 0.0;
        }

        if (estimatedTimeMinutes <= availableTimeMinutes) {
            double unusedRatio = (availableTimeMinutes - estimatedTimeMinutes) / availableTimeMinutes;
            return Math.max(7.0, 10.0 - unusedRatio * 2.0);
        }

        double overrunMinutes = estimatedTimeMinutes - availableTimeMinutes;
        return Math.max(0.0, 7.0 - overrunMinutes / 10.0);
    }

    private double preferenceAdjustedScenicScore(FlightRoute route, String preference) {
        double score = route.scenicScore();
        String normalizedPreference = normalize(preference);

        if (normalizedPreference.isBlank()) {
            return score;
        }

        String routeText = normalize(route.id() + " " + route.name() + " " + route.description());
        if (routeText.contains(normalizedPreference)
                || normalizedPreference.equals("coast") && routeText.contains("costa")
                || normalizedPreference.equals("scenic") && routeText.contains("panoramica")) {
            score += 1.0;
        }

        return Math.min(10.0, score);
    }

    private double costScore(double estimatedCost) {
        if (estimatedCost <= 80.0) {
            return 10.0;
        }

        if (estimatedCost >= 180.0) {
            return 5.0;
        }

        return 10.0 - (estimatedCost - 80.0) / 20.0;
    }

    private double resolveCruiseSpeed(RecommendationRequest request) {
        if (request.cruiseSpeedKmh() > 0) {
            return request.cruiseSpeedKmh();
        }

        return findAircraft(request.aircraftId())
                .map(Aircraft::cruiseSpeedKmh)
                .orElse(200.0);
    }

    private double resolveFuelBurn(RecommendationRequest request) {
        if (request.fuelBurnLitersPerHour() > 0) {
            return request.fuelBurnLitersPerHour();
        }

        return findAircraft(request.aircraftId())
                .map(Aircraft::fuelBurnLitersPerHour)
                .orElse(35.0);
    }

    private java.util.Optional<Aircraft> findAircraft(String aircraftId) {
        return MockFlightData.aircraft().stream()
                .filter(aircraft -> aircraft.id().equalsIgnoreCase(aircraftId))
                .findFirst();
    }

    private String explanation(
            FlightRoute route,
            RecommendationRequest request,
            double estimatedTimeMinutes,
            double estimatedFuelLiters,
            double estimatedCost,
            RouteScore score
    ) {
        String fit = estimatedTimeMinutes <= request.availableFlightTimeMinutes()
                ? "encaja dentro del tiempo disponible"
                : "supera el tiempo disponible";

        return "Ruta recomendada porque " + fit
                + ", tiene un atractivo visual de " + score.scenicScore()
                + "/10 y un coste estimado de " + round(estimatedCost)
                + ". Combustible estimado: " + round(estimatedFuelLiters)
                + " litros.";
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
