package flightdiscovery.paull.application.recommendation;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import flightdiscovery.paull.api.recommendation.RecommendationRequest;
import flightdiscovery.paull.api.recommendation.RecommendationResponse;
import flightdiscovery.paull.api.recommendation.RecommendedRouteResponse;
import flightdiscovery.paull.domain.calculation.RouteCalculationService;
import flightdiscovery.paull.domain.model.Aircraft;
import flightdiscovery.paull.domain.model.Airport;
import flightdiscovery.paull.domain.model.FlightRoute;
import flightdiscovery.paull.domain.model.RouteScore;
import flightdiscovery.paull.domain.repository.FlightDataRepository;
import flightdiscovery.paull.domain.scoring.RouteScoringService;

@Service
public class RouteRecommendationService {

    private static final int MAX_RECOMMENDATIONS = 3;
    private static final double MAX_ALLOWED_TIME_OVERRUN_RATIO = 1.25;
    private static final double LOW_TIME_MARGIN_RATIO = 0.90;
    private static final double HIGH_COST_THRESHOLD_EUR = 150.0;

    private final FlightDataRepository flightDataRepository;
    private final RouteCalculationService routeCalculationService;
    private final RouteScoringService routeScoringService;
    private final CandidateRouteGenerator candidateRouteGenerator;

    public RouteRecommendationService(
            FlightDataRepository flightDataRepository,
            RouteCalculationService routeCalculationService,
            RouteScoringService routeScoringService,
            CandidateRouteGenerator candidateRouteGenerator
    ) {
        this.flightDataRepository = flightDataRepository;
        this.routeCalculationService = routeCalculationService;
        this.routeScoringService = routeScoringService;
        this.candidateRouteGenerator = candidateRouteGenerator;
    }

    public RecommendationResponse recommend(RecommendationRequest request) {
        double cruiseSpeedKmh = resolveCruiseSpeed(request);
        double fuelBurnLitersPerHour = resolveFuelBurn(request);

        Airport departureAirport = resolveDepartureAirport(request.departureAirport());
        var predefinedRoutes = flightDataRepository.routes().stream()
                .filter(route -> route.departureAirport().code().equalsIgnoreCase(departureAirport.code()));
        var generatedRoutes = candidateRouteGenerator.generate(
                departureAirport,
                request.availableFlightTimeMinutes(),
                cruiseSpeedKmh,
                request.preference()
        ).stream();

        var viableRecommendations = Stream.concat(predefinedRoutes, generatedRoutes)
                .map(route -> toRecommendation(route, request, cruiseSpeedKmh, fuelBurnLitersPerHour))
                .filter(recommendation -> isWithinAllowedTime(recommendation, request.availableFlightTimeMinutes()))
                .sorted(Comparator.comparingDouble(RecommendedRouteResponse::totalScore).reversed())
                .limit(MAX_RECOMMENDATIONS)
                .toList();

        return new RecommendationResponse(viableRecommendations, warnings(viableRecommendations));
    }

    private Airport resolveDepartureAirport(String departureAirportCode) {
        return flightDataRepository.airports().stream()
                .filter(airport -> airport.code().equalsIgnoreCase(departureAirportCode))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "departureAirport must match a known airport"
                ));
    }

    private boolean isWithinAllowedTime(RecommendedRouteResponse recommendation, int availableTimeMinutes) {
        return recommendation.estimatedTimeMinutes() <= availableTimeMinutes * MAX_ALLOWED_TIME_OVERRUN_RATIO;
    }

    private List<String> warnings(List<RecommendedRouteResponse> recommendations) {
        if (recommendations.size() >= MAX_RECOMMENDATIONS) {
            return List.of();
        }

        return List.of("Fewer than 3 routes fit within the available flight time plus 25% tolerance.");
    }

    private RecommendedRouteResponse toRecommendation(
            FlightRoute route,
            RecommendationRequest request,
            double cruiseSpeedKmh,
            double fuelBurnLitersPerHour
    ) {
        double approximateDistanceKm = routeCalculationService.totalDistanceKm(route);
        double estimatedTimeHours = routeCalculationService.estimatedTimeHours(approximateDistanceKm, cruiseSpeedKmh);
        double estimatedTimeMinutes = routeCalculationService.estimatedTimeMinutes(estimatedTimeHours);
        double estimatedFuelLiters = routeCalculationService.estimatedFuelLiters(estimatedTimeMinutes, fuelBurnLitersPerHour);
        double estimatedCost = routeCalculationService.estimatedCost(estimatedFuelLiters, request.fuelPricePerLiter());
        RouteScore score = routeScoringService.score(
                route,
                estimatedTimeMinutes,
                request.availableFlightTimeMinutes(),
                estimatedCost,
                request.preference()
        );

        return new RecommendedRouteResponse(
                route.id(),
                route.name(),
                route.description(),
                route.waypoints(),
                round(approximateDistanceKm),
                round(estimatedTimeMinutes),
                round(estimatedTimeHours),
                roundOneDecimal(estimatedFuelLiters),
                roundTwoDecimals(estimatedCost),
                score.totalScore(),
                score,
                explanation(route, request, estimatedTimeMinutes, estimatedFuelLiters, estimatedCost, score),
                routeWarnings(estimatedTimeMinutes, request.availableFlightTimeMinutes(), estimatedCost)
        );
    }

    private List<String> routeWarnings(double estimatedTimeMinutes, int availableTimeMinutes, double estimatedCost) {
        List<String> warnings = new java.util.ArrayList<>();

        if (estimatedTimeMinutes > availableTimeMinutes) {
            warnings.add("Esta ruta supera ligeramente el tiempo disponible");
        } else if (estimatedTimeMinutes >= availableTimeMinutes * LOW_TIME_MARGIN_RATIO) {
            warnings.add("Esta ruta deja poco margen de tiempo");
        }

        if (estimatedCost >= HIGH_COST_THRESHOLD_EUR) {
            warnings.add("El coste estimado es alto");
        }

        warnings.add("La meteorologia todavia es simulada");

        return warnings;
    }

    private double resolveCruiseSpeed(RecommendationRequest request) {
        if (request.cruiseSpeedKmh() != null) {
            return request.cruiseSpeedKmh();
        }

        return flightDataRepository.findAircraftById(request.aircraftId())
                .map(Aircraft::cruiseSpeedKmh)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "aircraftId must match a known aircraft when cruiseSpeedKmh is not provided"
                ));
    }

    private double resolveFuelBurn(RecommendationRequest request) {
        if (request.fuelBurnLitersPerHour() != null) {
            return request.fuelBurnLitersPerHour();
        }

        return flightDataRepository.findAircraftById(request.aircraftId())
                .map(Aircraft::fuelBurnLitersPerHour)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "aircraftId must match a known aircraft when fuelBurnLitersPerHour is not provided"
                ));
    }

    private String explanation(
            FlightRoute route,
            RecommendationRequest request,
            double estimatedTimeMinutes,
            double estimatedFuelLiters,
            double estimatedCost,
            RouteScore score
    ) {
        String timeFit = timeFitText(estimatedTimeMinutes, request.availableFlightTimeMinutes());
        String scenicFit = score.scenicScore() >= 85.0
                ? "un alto interes visual"
                : "un interes visual correcto";
        String costFit = estimatedCost <= 90.0
                ? "un coste estimado bajo"
                : estimatedCost <= 160.0
                ? "un coste estimado moderado"
                : "un coste estimado alto";
        String preferenceFit = routeMatchesPreference(route, request.preference())
                ? "encaja con la preferencia " + request.preference()
                : "no encaja directamente con la preferencia " + request.preference();

        return "Esta ruta " + timeFit + ", tiene " + scenicFit
                + ", " + costFit + " y " + preferenceFit + ". Se estiman "
                + round(estimatedTimeMinutes) + " minutos, "
                + roundOneDecimal(estimatedFuelLiters) + " litros y "
                + roundTwoDecimals(estimatedCost) + " EUR.";
    }

    private boolean routeMatchesPreference(FlightRoute route, String preference) {
        if (preference == null || preference.isBlank()) {
            return false;
        }

        return route.tags().stream()
                .anyMatch(tag -> tag.equalsIgnoreCase(preference.trim()));
    }

    private String timeFitText(double estimatedTimeMinutes, int availableTimeMinutes) {
        if (estimatedTimeMinutes <= availableTimeMinutes * 0.65) {
            return "deja bastante margen respecto al tiempo disponible";
        }

        if (estimatedTimeMinutes <= availableTimeMinutes) {
            return "encaja bien con el tiempo disponible";
        }

        if (estimatedTimeMinutes <= availableTimeMinutes * 1.15) {
            return "supera ligeramente el tiempo disponible";
        }

        return "supera demasiado el tiempo disponible";
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double roundOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private double roundTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
