package flightdiscovery.paull.application.recommendation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import flightdiscovery.paull.api.recommendation.RecommendationRequest;
import flightdiscovery.paull.api.recommendation.RecommendationResponse;
import flightdiscovery.paull.api.recommendation.RecommendationCandidateDebug;
import flightdiscovery.paull.api.recommendation.RecommendationDebugInfo;
import flightdiscovery.paull.api.recommendation.RecommendationDevelopmentDebugResponse;
import flightdiscovery.paull.api.recommendation.RecommendationRouteDiscardDebug;
import flightdiscovery.paull.api.recommendation.RecommendedRouteResponse;
import flightdiscovery.paull.api.recommendation.RouteDurationCategory;
import flightdiscovery.paull.domain.calculation.RouteCalculationService;
import flightdiscovery.paull.domain.model.Aircraft;
import flightdiscovery.paull.domain.model.Airport;
import flightdiscovery.paull.domain.model.FlightRoute;
import flightdiscovery.paull.domain.model.FuelPrice;
import flightdiscovery.paull.domain.model.FuelPriceSource;
import flightdiscovery.paull.domain.model.RouteScore;
import flightdiscovery.paull.domain.model.WeatherData;
import flightdiscovery.paull.domain.repository.MockAircraftRepository;
import flightdiscovery.paull.domain.repository.MockAirportRepository;
import flightdiscovery.paull.domain.repository.MockFuelPriceRepository;
import flightdiscovery.paull.domain.repository.MockRouteRepository;
import flightdiscovery.paull.domain.scoring.RouteScoringService;
import flightdiscovery.paull.domain.weather.WeatherService;

@Service
public class RecommendationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecommendationService.class);
    private static final int MIN_RECOMMENDATIONS = 3;
    private static final int MAX_RECOMMENDATIONS = 5;
    private static final double MAX_ALLOWED_TIME_OVERRUN_RATIO = 1.25;
    private static final double LOW_TIME_MARGIN_RATIO = 0.90;
    private static final double HIGH_COST_THRESHOLD_EUR = 150.0;
    private static final double HIGH_SHORT_ROUTE_SCORE = 85.0;
    private static final String TIME_DISCARD_REASON = "Estimated route time exceeds useful available time plus 25% tolerance";
    private static final String FINAL_SELECTION_DISCARD_REASON = "Not selected after score and diversity recommendation limit";

    private final MockRouteRepository routeRepository;
    private final MockAirportRepository airportRepository;
    private final MockAircraftRepository aircraftRepository;
    private final MockFuelPriceRepository fuelPriceRepository;
    private final RouteCalculationService routeCalculationService;
    private final RouteScoringService routeScoringService;
    private final RouteCandidateGenerator routeCandidateGenerator;
    private final WeatherService weatherService;

    public RecommendationService(
            MockRouteRepository routeRepository,
            MockAirportRepository airportRepository,
            MockAircraftRepository aircraftRepository,
            MockFuelPriceRepository fuelPriceRepository,
            RouteCalculationService routeCalculationService,
            RouteScoringService routeScoringService,
            RouteCandidateGenerator routeCandidateGenerator,
            WeatherService weatherService
    ) {
        this.routeRepository = routeRepository;
        this.airportRepository = airportRepository;
        this.aircraftRepository = aircraftRepository;
        this.fuelPriceRepository = fuelPriceRepository;
        this.routeCalculationService = routeCalculationService;
        this.routeScoringService = routeScoringService;
        this.routeCandidateGenerator = routeCandidateGenerator;
        this.weatherService = weatherService;
    }

    public RecommendationResponse recommend(RecommendationRequest request) {
        Aircraft aircraft = resolveAircraft(request);
        double cruiseSpeedKmh = resolveCruiseSpeed(request, aircraft);
        double fuelBurnLitersPerHour = resolveFuelBurn(request, aircraft);
        FuelPrice fuelPrice = resolveFuelPrice(request, aircraft);
        double usefulAvailableTimeMinutes = usefulAvailableTimeMinutes(request, aircraft);

        Airport departureAirport = resolveDepartureAirport(request.departureAirport());
        List<FlightRoute> allPredefinedRoutes = routeRepository.findAll();
        List<FlightRoute> predefinedRoutes = routeRepository.findByDepartureAirportCode(departureAirport.code());
        LOGGER.info("Recommendation diagnostics: predefinedRoutesTotal={} predefinedRoutesForDeparture={} departureAirport={} availableFlightTimeMinutes={} recommendedReserveMinutes={} safetyMarginPercent={} usefulAvailableTimeMinutes={} fuelType={} fuelPricePerLiter={} fuelPriceSource={}",
                allPredefinedRoutes.size(),
                predefinedRoutes.size(),
                departureAirport.code(),
                request.availableFlightTimeMinutes(),
                aircraft.recommendedReserveMinutes(),
                request.effectiveSafetyMarginPercent(),
                round(usefulAvailableTimeMinutes),
                fuelPrice.fuelType(),
                fuelPrice.pricePerLiter(),
                fuelPrice.source());

        RouteGenerationResult generationResult = routeCandidateGenerator.generateWithDebug(
                departureAirport,
                usefulAvailableTimeMinutes,
                cruiseSpeedKmh,
                request.preference()
        );
        List<FlightRoute> generatedRoutes = generationResult.routes();
        List<FlightRoute> routesForScoring = Stream.concat(predefinedRoutes.stream(), generatedRoutes.stream())
                .toList();
        LOGGER.info("Recommendation diagnostics: routesReachingScoring={} predefinedReachingScoring={} generatedReachingScoring={}",
                routesForScoring.size(), predefinedRoutes.size(), generatedRoutes.size());

        var scoredRoutes = routesForScoring.stream()
                .map(route -> new ScoredRoute(
                        route,
                        toRecommendation(route, request, usefulAvailableTimeMinutes, cruiseSpeedKmh, fuelBurnLitersPerHour, fuelPrice)
                ))
                .toList();
        var usefulTimeViableRoutes = scoredRoutes.stream()
                .filter(scoredRoute -> isWithinAllowedTime(scoredRoute.recommendation(), usefulAvailableTimeMinutes))
                .toList();
        var viableRecommendations = diverseRecommendations(usefulTimeViableRoutes, request.preference()).stream()
                .map(ScoredRoute::recommendation)
                .toList();
        LOGGER.info("Recommendation diagnostics: scoredRecommendations={} discardedScoredRecommendationsByUsefulTime={} usefulTimeViableScoredRecommendations={} returnedRecommendations={}",
                scoredRoutes.size(),
                scoredRoutes.size() - usefulTimeViableRoutes.size(),
                usefulTimeViableRoutes.size(),
                viableRecommendations.size());

        int scoredRoutesDiscardedByTime = scoredRoutes.size() - usefulTimeViableRoutes.size();
        RecommendationDebugInfo debugInfo = new RecommendationDebugInfo(
                generationResult.generatedCandidateRoutes(),
                generationResult.discardedByTimeRoutes() + scoredRoutesDiscardedByTime,
                viableRecommendations.size()
        );

        return new RecommendationResponse(viableRecommendations, warnings(viableRecommendations), debugInfo);
    }

    public RecommendationDevelopmentDebugResponse debug(RecommendationRequest request) {
        Aircraft aircraft = resolveAircraft(request);
        double cruiseSpeedKmh = resolveCruiseSpeed(request, aircraft);
        double fuelBurnLitersPerHour = resolveFuelBurn(request, aircraft);
        FuelPrice fuelPrice = resolveFuelPrice(request, aircraft);
        double usefulAvailableTimeMinutes = usefulAvailableTimeMinutes(request, aircraft);

        Airport departureAirport = resolveDepartureAirport(request.departureAirport());
        List<FlightRoute> predefinedRoutes = routeRepository.findByDepartureAirportCode(departureAirport.code());
        RouteGenerationResult generationResult = routeCandidateGenerator.generateWithDebug(
                departureAirport,
                usefulAvailableTimeMinutes,
                cruiseSpeedKmh,
                request.preference()
        );
        List<FlightRoute> generatedRoutes = generationResult.routes();
        List<FlightRoute> routesForScoring = Stream.concat(predefinedRoutes.stream(), generatedRoutes.stream())
                .toList();
        var scoredRoutes = routesForScoring.stream()
                .map(route -> new ScoredRoute(
                        route,
                        toRecommendation(route, request, usefulAvailableTimeMinutes, cruiseSpeedKmh, fuelBurnLitersPerHour, fuelPrice)
                ))
                .toList();
        var usefulTimeViableRoutes = scoredRoutes.stream()
                .filter(scoredRoute -> isWithinAllowedTime(scoredRoute.recommendation(), usefulAvailableTimeMinutes))
                .toList();
        var viableRecommendations = diverseRecommendations(usefulTimeViableRoutes, request.preference()).stream()
                .map(ScoredRoute::recommendation)
                .toList();
        List<RecommendationRouteDiscardDebug> discards = new ArrayList<>();
        discards.addAll(generationResult.discardedRoutes().stream()
                .map(discard -> toDiscardDebug(discard, "GENERATED_TIME_FILTER"))
                .toList());
        discards.addAll(scoredRoutes.stream()
                .filter(scoredRoute -> !isWithinAllowedTime(scoredRoute.recommendation(), usefulAvailableTimeMinutes))
                .map(scoredRoute -> new RecommendationRouteDiscardDebug(
                        scoredRoute.route().id(),
                        scoredRoute.route().name(),
                        "SCORING_TIME_FILTER",
                        TIME_DISCARD_REASON,
                        scoredRoute.recommendation().estimatedTimeMinutes(),
                        round(usefulAvailableTimeMinutes * MAX_ALLOWED_TIME_OVERRUN_RATIO)
                ))
                .toList());
        discards.addAll(usefulTimeViableRoutes.stream()
                .filter(scoredRoute -> viableRecommendations.stream()
                        .noneMatch(recommendation -> recommendation.id().equals(scoredRoute.recommendation().id())))
                .map(scoredRoute -> new RecommendationRouteDiscardDebug(
                        scoredRoute.route().id(),
                        scoredRoute.route().name(),
                        "FINAL_SELECTION",
                        FINAL_SELECTION_DISCARD_REASON,
                        scoredRoute.recommendation().estimatedTimeMinutes(),
                        round(usefulAvailableTimeMinutes * MAX_ALLOWED_TIME_OVERRUN_RATIO)
                ))
                .toList());
        List<RecommendationCandidateDebug> candidates = candidateDebugRows(
                scoredRoutes,
                generationResult.discardedRoutes(),
                discards,
                viableRecommendations
        );

        return new RecommendationDevelopmentDebugResponse(
                request,
                aircraft,
                round(usefulAvailableTimeMinutes),
                generationResult.compatibleWaypointCount(),
                generationResult.generatedCandidateRoutes(),
                discards.size(),
                viableRecommendations.size(),
                candidates,
                discards,
                viableRecommendations
        );
    }

    private List<RecommendationCandidateDebug> candidateDebugRows(
            List<ScoredRoute> scoredRoutes,
            List<RouteCandidateDiscard> generationDiscards,
            List<RecommendationRouteDiscardDebug> discards,
            List<RecommendedRouteResponse> viableRecommendations
    ) {
        List<RecommendationCandidateDebug> candidates = new ArrayList<>();
        candidates.addAll(scoredRoutes.stream()
                .map(scoredRoute -> toCandidateDebug(scoredRoute, discards, viableRecommendations))
                .toList());
        candidates.addAll(generationDiscards.stream()
                .map(discard -> new RecommendationCandidateDebug(
                        discard.route().id(),
                        discard.route().name(),
                        discard.route().routeType(),
                        discard.estimatedTimeMinutes(),
                        null,
                        null,
                        null,
                        true,
                        "GENERATION",
                        discard.reason()
                ))
                .toList());

        return candidates;
    }

    private RecommendationCandidateDebug toCandidateDebug(
            ScoredRoute scoredRoute,
            List<RecommendationRouteDiscardDebug> discards,
            List<RecommendedRouteResponse> viableRecommendations
    ) {
        RecommendedRouteResponse recommendation = scoredRoute.recommendation();
        RecommendationRouteDiscardDebug discard = discards.stream()
                .filter(candidateDiscard -> candidateDiscard.routeId().equals(recommendation.id()))
                .findFirst()
                .orElse(null);
        boolean selected = viableRecommendations.stream()
                .anyMatch(finalRecommendation -> finalRecommendation.id().equals(recommendation.id()));

        return new RecommendationCandidateDebug(
                recommendation.id(),
                recommendation.name(),
                recommendation.routeType(),
                recommendation.estimatedTimeMinutes(),
                recommendation.totalScore(),
                recommendation.scoreBreakdown().timeFitScore(),
                recommendation.scoreBreakdown().costScore(),
                !selected,
                discard == null ? null : discard.stage(),
                discard == null ? null : discard.reason()
        );
    }

    private RecommendationRouteDiscardDebug toDiscardDebug(RouteCandidateDiscard discard, String stage) {
        return new RecommendationRouteDiscardDebug(
                discard.route().id(),
                discard.route().name(),
                stage,
                discard.reason(),
                discard.estimatedTimeMinutes(),
                discard.limitMinutes()
        );
    }

    private List<ScoredRoute> diverseRecommendations(List<ScoredRoute> scoredRoutes, String preference) {
        List<ScoredRoute> sortedRoutes = sortedBySelection(scoredRoutes, preference);
        List<ScoredRoute> selectedRoutes = new ArrayList<>();
        Set<String> waypointSignatures = new HashSet<>();
        boolean shortPreference = isShortPreference(preference);

        List<ScoredRoute> goodFitRoutes = sortedRoutes.stream()
                .filter(scoredRoute -> routeDurationCategory(scoredRoute) == RouteDurationCategory.GOOD_FIT)
                .toList();
        addDiverseRoutes(goodFitRoutes, selectedRoutes, waypointSignatures, false, false, Math.min(2, goodFitRoutes.size()));

        List<ScoredRoute> preferredDurationRoutes = sortedRoutes.stream()
                .filter(scoredRoute -> routeDurationCategory(scoredRoute) == RouteDurationCategory.GOOD_FIT
                        || routeDurationCategory(scoredRoute) == RouteDurationCategory.LONG
                        || routeDurationCategory(scoredRoute) == RouteDurationCategory.SLIGHTLY_OVER_TIME
                        || (routeDurationCategory(scoredRoute) == RouteDurationCategory.SHORT && isHighScoringShortRoute(scoredRoute))
                        || (shortPreference && routeDurationCategory(scoredRoute) == RouteDurationCategory.TOO_SHORT))
                .toList();
        addDiverseRoutes(preferredDurationRoutes, selectedRoutes, waypointSignatures, true, true, MAX_RECOMMENDATIONS);
        addDiverseRoutes(preferredDurationRoutes, selectedRoutes, waypointSignatures, true, false, MAX_RECOMMENDATIONS);
        addDiverseRoutes(preferredDurationRoutes, selectedRoutes, waypointSignatures, false, false, MAX_RECOMMENDATIONS);

        List<ScoredRoute> shortRoutes = sortedRoutes.stream()
                .filter(scoredRoute -> routeDurationCategory(scoredRoute) == RouteDurationCategory.SHORT)
                .toList();
        addDiverseRoutes(shortRoutes, selectedRoutes, waypointSignatures, false, false, MAX_RECOMMENDATIONS);

        boolean hasNonTooShortAlternative = sortedRoutes.stream()
                .anyMatch(scoredRoute -> routeDurationCategory(scoredRoute) != RouteDurationCategory.TOO_SHORT
                        && selectedRoutes.stream().noneMatch(selectedRoute -> selectedRoute.route().id().equals(scoredRoute.route().id())));
        if (shortPreference || !hasNonTooShortAlternative) {
            List<ScoredRoute> tooShortRoutes = sortedRoutes.stream()
                    .filter(scoredRoute -> routeDurationCategory(scoredRoute) == RouteDurationCategory.TOO_SHORT)
                    .toList();
            addDiverseRoutes(tooShortRoutes, selectedRoutes, waypointSignatures, false, false, MAX_RECOMMENDATIONS);
        }

        return selectedRoutes.stream()
                .sorted(selectionComparator(preference))
                .toList();
    }

    private List<ScoredRoute> sortedBySelection(List<ScoredRoute> scoredRoutes, String preference) {
        return scoredRoutes.stream()
                .sorted(selectionComparator(preference))
                .toList();
    }

    private Comparator<ScoredRoute> selectionComparator(String preference) {
        return Comparator
                .comparingInt((ScoredRoute scoredRoute) -> durationSelectionPriority(scoredRoute, preference))
                .thenComparing(Comparator.comparing(
                        (ScoredRoute scoredRoute) -> routeMatchesPreference(scoredRoute.route(), preference)
                ).reversed())
                .thenComparing(Comparator.comparingDouble(
                        (ScoredRoute scoredRoute) -> scoredRoute.recommendation().totalScore()
                ).reversed());
    }

    private int durationSelectionPriority(ScoredRoute scoredRoute, String preference) {
        return switch (routeDurationCategory(scoredRoute)) {
            case GOOD_FIT -> 0;
            case LONG -> 1;
            case SLIGHTLY_OVER_TIME -> 2;
            case SHORT -> isHighScoringShortRoute(scoredRoute) ? 2 : 3;
            case TOO_SHORT -> isShortPreference(preference) ? 1 : 4;
            case TOO_LONG -> 5;
        };
    }

    private RouteDurationCategory routeDurationCategory(ScoredRoute scoredRoute) {
        return scoredRoute.recommendation().routeDurationCategory();
    }

    private boolean isHighScoringShortRoute(ScoredRoute scoredRoute) {
        return scoredRoute.recommendation().totalScore() >= HIGH_SHORT_ROUTE_SCORE;
    }

    private void addDiverseRoutes(
            List<ScoredRoute> sortedRoutes,
            List<ScoredRoute> selectedRoutes,
            Set<String> waypointSignatures,
            boolean requireNewRouteType,
            boolean requireNewPrimaryTag,
            int targetSize
    ) {
        for (ScoredRoute candidate : sortedRoutes) {
            if (selectedRoutes.size() >= targetSize || selectedRoutes.size() >= MAX_RECOMMENDATIONS) {
                return;
            }

            String waypointSignature = waypointSignature(candidate.route());
            if (waypointSignatures.contains(waypointSignature)
                    || selectedRoutes.stream().anyMatch(selectedRoute -> selectedRoute.route().id().equals(candidate.route().id()))) {
                continue;
            }

            if (requireNewRouteType && selectedRoutes.stream()
                    .anyMatch(selectedRoute -> selectedRoute.route().routeType() == candidate.route().routeType())) {
                continue;
            }

            if (requireNewPrimaryTag && selectedRoutes.stream()
                    .anyMatch(selectedRoute -> primaryTag(selectedRoute.route()).equals(primaryTag(candidate.route())))) {
                continue;
            }

            selectedRoutes.add(candidate);
            waypointSignatures.add(waypointSignature);
        }
    }

    private String waypointSignature(FlightRoute route) {
        return route.waypoints().stream()
                .map(waypoint -> waypoint.name().toLowerCase())
                .sorted()
                .reduce((first, second) -> first + "|" + second)
                .orElse(route.id());
    }

    private String primaryTag(FlightRoute route) {
        return route.tags().isEmpty() ? "" : route.tags().getFirst();
    }

    private Airport resolveDepartureAirport(String departureAirportCode) {
        return airportRepository.findByCode(departureAirportCode)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "departureAirport must match a known airport"
                ));
    }

    private double usefulAvailableTimeMinutes(RecommendationRequest request, Aircraft aircraft) {
        double availableAfterReserveMinutes = Math.max(
                0.0,
                request.availableFlightTimeMinutes() - aircraft.recommendedReserveMinutes()
        );

        return availableAfterReserveMinutes * (100.0 - request.effectiveSafetyMarginPercent()) / 100.0;
    }

    private boolean isWithinAllowedTime(RecommendedRouteResponse recommendation, double availableTimeMinutes) {
        return recommendation.estimatedTimeMinutes() <= availableTimeMinutes * MAX_ALLOWED_TIME_OVERRUN_RATIO;
    }

    private List<String> warnings(List<RecommendedRouteResponse> recommendations) {
        if (recommendations.size() >= MIN_RECOMMENDATIONS) {
            return List.of();
        }

        return List.of("Fewer than 3 candidate routes fit within the available flight time plus 25% tolerance.");
    }

    private RecommendedRouteResponse toRecommendation(
            FlightRoute route,
            RecommendationRequest request,
            double usefulFlightTimeMinutes,
            double cruiseSpeedKmh,
            double fuelBurnLitersPerHour,
            FuelPrice fuelPrice
    ) {
        double approximateDistanceKm = routeCalculationService.totalDistanceKm(route);
        double estimatedTimeHours = routeCalculationService.estimatedTimeHours(approximateDistanceKm, cruiseSpeedKmh);
        double estimatedTimeMinutes = routeCalculationService.estimatedTimeMinutes(estimatedTimeHours);
        double estimatedFuelLiters = routeCalculationService.estimatedFuelLiters(estimatedTimeMinutes, fuelBurnLitersPerHour);
        double estimatedCost = routeCalculationService.estimatedCost(estimatedFuelLiters, fuelPrice.pricePerLiter());
        WeatherData weatherData = weatherService.weatherFor(route);
        RouteScore score = routeScoringService.score(
                route,
                estimatedTimeMinutes,
                usefulFlightTimeMinutes,
                estimatedCost,
                request.preference(),
                weatherData.weatherScore()
        );

        return new RecommendedRouteResponse(
                route.id(),
                route.name(),
                route.description(),
                route.routeType(),
                route.waypoints(),
                round(approximateDistanceKm),
                round(estimatedTimeMinutes),
                round(estimatedTimeHours),
                routeDurationCategory(estimatedTimeMinutes, usefulFlightTimeMinutes),
                roundOneDecimal(estimatedFuelLiters),
                fuelPrice.pricePerLiter(),
                fuelPrice.source().name(),
                roundTwoDecimals(estimatedCost),
                score.totalScore(),
                score.weatherScore(),
                weatherData.windKmh(),
                weatherData.cloudCoverPercent(),
                weatherData.precipitationProbability(),
                weatherData.visibilityKm(),
                score,
                explanation(route, request, usefulFlightTimeMinutes, estimatedTimeMinutes, estimatedFuelLiters, estimatedCost, score, weatherData),
                routeWarnings(estimatedTimeMinutes, usefulFlightTimeMinutes, estimatedCost)
        );
    }

    private RouteDurationCategory routeDurationCategory(double estimatedTimeMinutes, double usefulAvailableTimeMinutes) {
        if (usefulAvailableTimeMinutes <= 0.0) {
            return RouteDurationCategory.TOO_LONG;
        }

        double usageRatio = estimatedTimeMinutes / usefulAvailableTimeMinutes;

        if (usageRatio < 0.4) {
            return RouteDurationCategory.TOO_SHORT;
        }

        if (usageRatio < 0.7) {
            return RouteDurationCategory.SHORT;
        }

        if (usageRatio < LOW_TIME_MARGIN_RATIO) {
            return RouteDurationCategory.GOOD_FIT;
        }

        if (usageRatio <= 1.0) {
            return RouteDurationCategory.LONG;
        }

        if (usageRatio <= MAX_ALLOWED_TIME_OVERRUN_RATIO) {
            return RouteDurationCategory.SLIGHTLY_OVER_TIME;
        }

        return RouteDurationCategory.TOO_LONG;
    }

    private List<String> routeWarnings(double estimatedTimeMinutes, double availableTimeMinutes, double estimatedCost) {
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

    private Aircraft resolveAircraft(RecommendationRequest request) {
        return aircraftRepository.findById(request.aircraftId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "aircraftId must match a known aircraft"
                ));
    }

    private double resolveCruiseSpeed(RecommendationRequest request, Aircraft aircraft) {
        if (request.cruiseSpeedKmh() != null) {
            return request.cruiseSpeedKmh();
        }

        return aircraft.cruiseSpeedKmh();
    }

    private double resolveFuelBurn(RecommendationRequest request, Aircraft aircraft) {
        if (request.fuelBurnLitersPerHour() != null) {
            return request.fuelBurnLitersPerHour();
        }

        return aircraft.fuelBurnLitersPerHour();
    }

    private FuelPrice resolveFuelPrice(RecommendationRequest request, Aircraft aircraft) {
        if (request.fuelPricePerLiter() != null) {
            return new FuelPrice(aircraft.fuelType(), request.fuelPricePerLiter(), FuelPriceSource.MANUAL);
        }

        return fuelPriceRepository.findByFuelType(aircraft.fuelType())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "aircraft fuelType must have a configured mock fuel price"
                ));
    }

    private String explanation(
            FlightRoute route,
            RecommendationRequest request,
            double usefulFlightTimeMinutes,
            double estimatedTimeMinutes,
            double estimatedFuelLiters,
            double estimatedCost,
            RouteScore score,
            WeatherData weatherData
    ) {
        String timeFit = timeFitText(estimatedTimeMinutes, usefulFlightTimeMinutes);
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

        String weatherFit = weatherData.weatherScore() >= 75.0
                ? "meteorologia simulada favorable"
                : weatherData.weatherScore() >= 50.0
                ? "meteorologia simulada aceptable"
                : "meteorologia simulada desfavorable";

        return "Esta ruta " + timeFit + ", tiene " + scenicFit
                + ", " + costFit + ", " + weatherFit + " y " + preferenceFit + ". Se estiman "
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

    private boolean isShortPreference(String preference) {
        return preference != null && preference.trim().equalsIgnoreCase("short");
    }

    private String timeFitText(double estimatedTimeMinutes, double availableTimeMinutes) {
        if (availableTimeMinutes <= 0.0) {
            return "no puede evaluarse contra el tiempo disponible";
        }

        double usageRatio = estimatedTimeMinutes / availableTimeMinutes;

        if (usageRatio < 0.7) {
            return "aprovecha poco el tiempo disponible";
        }

        if (usageRatio <= 1.0) {
            return "aprovecha bien el tiempo disponible";
        }

        if (usageRatio <= MAX_ALLOWED_TIME_OVERRUN_RATIO) {
            return "aprovecha demasiado el tiempo disponible";
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

    private record ScoredRoute(
            FlightRoute route,
            RecommendedRouteResponse recommendation
    ) {
    }
}
