package flightdiscovery.paull.application.recommendation;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import flightdiscovery.paull.api.recommendation.SightseeingManeuverResponse;
import flightdiscovery.paull.domain.calculation.RouteCalculationService;
import flightdiscovery.paull.domain.model.Aircraft;
import flightdiscovery.paull.domain.model.Airport;
import flightdiscovery.paull.domain.model.FlightRoute;
import flightdiscovery.paull.domain.model.FuelPrice;
import flightdiscovery.paull.domain.model.FuelPriceSource;
import flightdiscovery.paull.domain.model.RouteScore;
import flightdiscovery.paull.domain.model.RouteWeatherSummary;
import flightdiscovery.paull.domain.model.WeatherData;
import flightdiscovery.paull.domain.model.Waypoint;
import flightdiscovery.paull.domain.repository.MockAircraftRepository;
import flightdiscovery.paull.domain.repository.MockAirportRepository;
import flightdiscovery.paull.domain.repository.MockFuelPriceRepository;
import flightdiscovery.paull.domain.repository.MockRouteRepository;
import flightdiscovery.paull.domain.scoring.RouteScoringService;
import flightdiscovery.paull.domain.weather.WeatherServiceException;
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
    private static final int MAX_RECOMMENDED_ROUTES_PER_WAYPOINT = 2;
    private static final double LOCAL_ROUTE_SIGHTSEEING_MIN_SCENIC_SCORE = 85.0;
    private static final double SIGHTSEEING_TARGET_USEFUL_TIME_RATIO = 0.85;
    private static final double SIGHTSEEING_AVAILABLE_MARGIN_RATIO = 0.65;
    private static final double MAX_SIGHTSEEING_ROUTE_RATIO = 0.85;
    private static final double MAX_SIGHTSEEING_MINUTES_PER_WAYPOINT = 12.0;
    private static final double MAX_TOTAL_SIGHTSEEING_MINUTES = 30.0;
    private static final double SIGHTSEEING_ORBIT_MIN_RADIUS_KM = 0.8;
    private static final double SIGHTSEEING_ORBIT_MAX_RADIUS_KM = 3.0;
    private static final int SIGHTSEEING_ORBIT_SEGMENTS = 16;
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
                        toRecommendation(route, request, departureAirport, usefulAvailableTimeMinutes, cruiseSpeedKmh, fuelBurnLitersPerHour, fuelPrice)
                ))
                .toList();
        var usefulTimeViableRoutes = scoredRoutes.stream()
                .filter(scoredRoute -> isWithinAllowedTime(scoredRoute.recommendation(), usefulAvailableTimeMinutes))
                .toList();
        var viableScoredRoutes = diverseRecommendations(usefulTimeViableRoutes, request.preference());
        var viableRecommendations = viableScoredRoutes.stream()
                .map(scoredRoute -> withRouteWeatherSummary(scoredRoute.recommendation(), scoredRoute.route(), departureAirport))
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
                        toRecommendation(route, request, departureAirport, usefulAvailableTimeMinutes, cruiseSpeedKmh, fuelBurnLitersPerHour, fuelPrice)
                ))
                .toList();
        var usefulTimeViableRoutes = scoredRoutes.stream()
                .filter(scoredRoute -> isWithinAllowedTime(scoredRoute.recommendation(), usefulAvailableTimeMinutes))
                .toList();
        var viableScoredRoutes = diverseRecommendations(usefulTimeViableRoutes, request.preference());
        var viableRecommendations = viableScoredRoutes.stream()
                .map(scoredRoute -> withRouteWeatherSummary(scoredRoute.recommendation(), scoredRoute.route(), departureAirport))
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
        List<ScoredRoute> primaryRoutes = isInterIslandPreference(preference)
                ? sortedRoutes
                : sortedRoutes.stream()
                .filter(scoredRoute -> !isInterIslandRoute(scoredRoute.route()))
                .toList();
        List<ScoredRoute> selectedRoutes = new ArrayList<>();
        Set<String> waypointSignatures = new HashSet<>();
        Map<String, Integer> waypointUsageCounts = new HashMap<>();
        boolean shortPreference = isShortPreference(preference);

        List<ScoredRoute> goodFitRoutes = primaryRoutes.stream()
                .filter(scoredRoute -> routeDurationCategory(scoredRoute) == RouteDurationCategory.GOOD_FIT)
                .toList();
        addDiverseRoutes(goodFitRoutes, selectedRoutes, waypointSignatures, waypointUsageCounts, false, false, true, Math.min(2, goodFitRoutes.size()));

        List<ScoredRoute> preferredDurationRoutes = primaryRoutes.stream()
                .filter(scoredRoute -> routeDurationCategory(scoredRoute) == RouteDurationCategory.GOOD_FIT
                        || routeDurationCategory(scoredRoute) == RouteDurationCategory.LONG
                        || routeDurationCategory(scoredRoute) == RouteDurationCategory.SLIGHTLY_OVER_TIME
                        || (routeDurationCategory(scoredRoute) == RouteDurationCategory.SHORT && isHighScoringShortRoute(scoredRoute))
                        || (shortPreference && routeDurationCategory(scoredRoute) == RouteDurationCategory.TOO_SHORT))
                .toList();
        addDiverseRoutes(preferredDurationRoutes, selectedRoutes, waypointSignatures, waypointUsageCounts, true, true, true, MAX_RECOMMENDATIONS);
        addDiverseRoutes(preferredDurationRoutes, selectedRoutes, waypointSignatures, waypointUsageCounts, true, false, true, MAX_RECOMMENDATIONS);
        addDiverseRoutes(preferredDurationRoutes, selectedRoutes, waypointSignatures, waypointUsageCounts, false, false, true, MAX_RECOMMENDATIONS);
        addDiverseRoutes(preferredDurationRoutes, selectedRoutes, waypointSignatures, waypointUsageCounts, false, false, false, MAX_RECOMMENDATIONS);

        List<ScoredRoute> shortRoutes = primaryRoutes.stream()
                .filter(scoredRoute -> routeDurationCategory(scoredRoute) == RouteDurationCategory.SHORT)
                .toList();
        addDiverseRoutes(shortRoutes, selectedRoutes, waypointSignatures, waypointUsageCounts, false, false, true, MAX_RECOMMENDATIONS);
        addDiverseRoutes(shortRoutes, selectedRoutes, waypointSignatures, waypointUsageCounts, false, false, false, MAX_RECOMMENDATIONS);

        boolean hasNonTooShortAlternative = primaryRoutes.stream()
                .anyMatch(scoredRoute -> routeDurationCategory(scoredRoute) != RouteDurationCategory.TOO_SHORT
                        && selectedRoutes.stream().noneMatch(selectedRoute -> selectedRoute.route().id().equals(scoredRoute.route().id())));
        if (shortPreference || !hasNonTooShortAlternative) {
            List<ScoredRoute> tooShortRoutes = primaryRoutes.stream()
                    .filter(scoredRoute -> routeDurationCategory(scoredRoute) == RouteDurationCategory.TOO_SHORT)
                    .toList();
            addDiverseRoutes(tooShortRoutes, selectedRoutes, waypointSignatures, waypointUsageCounts, false, false, true, MAX_RECOMMENDATIONS);
            addDiverseRoutes(tooShortRoutes, selectedRoutes, waypointSignatures, waypointUsageCounts, false, false, false, MAX_RECOMMENDATIONS);
        }

        if (!isInterIslandPreference(preference) && selectedRoutes.size() < MAX_RECOMMENDATIONS) {
            List<ScoredRoute> interIslandRoutes = sortedRoutes.stream()
                    .filter(scoredRoute -> isInterIslandRoute(scoredRoute.route()))
                    .toList();
            addDiverseRoutes(interIslandRoutes, selectedRoutes, waypointSignatures, waypointUsageCounts, false, false, true, MAX_RECOMMENDATIONS);
            addDiverseRoutes(interIslandRoutes, selectedRoutes, waypointSignatures, waypointUsageCounts, false, false, false, MAX_RECOMMENDATIONS);
        }

        return rebalanceWaypointDiversity(selectedRoutes, sortedRoutes).stream()
                .sorted(selectionComparator(preference))
                .toList();
    }

    private List<ScoredRoute> rebalanceWaypointDiversity(List<ScoredRoute> selectedRoutes, List<ScoredRoute> sortedRoutes) {
        if (selectedRoutes.size() < MAX_RECOMMENDATIONS) {
            return selectedRoutes;
        }

        List<ScoredRoute> rebalancedRoutes = new ArrayList<>();
        Set<String> waypointSignatures = new HashSet<>();
        Map<String, Integer> waypointUsageCounts = new HashMap<>();
        for (ScoredRoute candidate : sortedRoutes) {
            if (rebalancedRoutes.size() >= MAX_RECOMMENDATIONS) {
                return rebalancedRoutes;
            }

            String waypointSignature = waypointSignature(candidate.route());
            if (waypointSignatures.contains(waypointSignature)
                    || usesOverrepresentedWaypoint(candidate.route(), waypointUsageCounts)) {
                continue;
            }

            rebalancedRoutes.add(candidate);
            waypointSignatures.add(waypointSignature);
            recordWaypointUsage(candidate.route(), waypointUsageCounts);
        }

        for (ScoredRoute candidate : selectedRoutes) {
            if (rebalancedRoutes.size() >= MAX_RECOMMENDATIONS) {
                return rebalancedRoutes;
            }

            String waypointSignature = waypointSignature(candidate.route());
            if (waypointSignatures.contains(waypointSignature)) {
                continue;
            }

            rebalancedRoutes.add(candidate);
            waypointSignatures.add(waypointSignature);
        }

        return rebalancedRoutes;
    }

    private List<ScoredRoute> sortedBySelection(List<ScoredRoute> scoredRoutes, String preference) {
        return scoredRoutes.stream()
                .sorted(selectionComparator(preference))
                .toList();
    }

    private Comparator<ScoredRoute> selectionComparator(String preference) {
        return Comparator
                .comparingInt((ScoredRoute scoredRoute) -> routeExperiencePriority(scoredRoute.route(), preference))
                .thenComparingInt(scoredRoute -> durationSelectionPriority(scoredRoute, preference))
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

    private int routeExperiencePriority(FlightRoute route, String preference) {
        if (!isInterIslandRoute(route) || isInterIslandPreference(preference)) {
            return 0;
        }

        return 1;
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
            Map<String, Integer> waypointUsageCounts,
            boolean requireNewRouteType,
            boolean requireNewPrimaryTag,
            boolean limitRepeatedWaypoints,
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

            if (usesOverrepresentedWaypoint(candidate.route(), waypointUsageCounts)
                    && (limitRepeatedWaypoints || hasDiverseAlternative(sortedRoutes, selectedRoutes, waypointSignatures, waypointUsageCounts))) {
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
            recordWaypointUsage(candidate.route(), waypointUsageCounts);
        }
    }

    private boolean hasDiverseAlternative(
            List<ScoredRoute> sortedRoutes,
            List<ScoredRoute> selectedRoutes,
            Set<String> waypointSignatures,
            Map<String, Integer> waypointUsageCounts
    ) {
        return sortedRoutes.stream()
                .anyMatch(candidate -> !usesOverrepresentedWaypoint(candidate.route(), waypointUsageCounts)
                        && !waypointSignatures.contains(waypointSignature(candidate.route()))
                        && selectedRoutes.stream().noneMatch(selectedRoute -> selectedRoute.route().id().equals(candidate.route().id())));
    }

    private boolean usesOverrepresentedWaypoint(FlightRoute route, Map<String, Integer> waypointUsageCounts) {
        return route.waypoints().stream()
                .map(waypoint -> waypoint.name().toLowerCase())
                .anyMatch(waypoint -> waypointUsageCounts.getOrDefault(waypoint, 0) >= MAX_RECOMMENDED_ROUTES_PER_WAYPOINT);
    }

    private void recordWaypointUsage(FlightRoute route, Map<String, Integer> waypointUsageCounts) {
        route.waypoints().stream()
                .map(waypoint -> waypoint.name().toLowerCase())
                .forEach(waypoint -> waypointUsageCounts.merge(waypoint, 1, Integer::sum));
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
            Airport departureAirport,
            double usefulFlightTimeMinutes,
            double cruiseSpeedKmh,
            double fuelBurnLitersPerHour,
            FuelPrice fuelPrice
    ) {
        double approximateDistanceKm = routeCalculationService.totalDistanceKm(route);
        double baseFlightTimeHours = routeCalculationService.estimatedTimeHours(approximateDistanceKm, cruiseSpeedKmh);
        double baseFlightTimeMinutes = routeCalculationService.estimatedTimeMinutes(baseFlightTimeHours);
        double sightseeingTimeMinutes = sightseeingTimeMinutes(route, baseFlightTimeMinutes, usefulFlightTimeMinutes);
        double estimatedTimeMinutes = baseFlightTimeMinutes + sightseeingTimeMinutes;
        double estimatedTimeHours = estimatedTimeMinutes / 60.0;
        double estimatedFuelLiters = routeCalculationService.estimatedFuelLiters(estimatedTimeMinutes, fuelBurnLitersPerHour);
        double estimatedCost = routeCalculationService.estimatedCost(estimatedFuelLiters, fuelPrice.pricePerLiter());
        String plannedDepartureDateTime = request.effectivePlannedDepartureDateTime();
        LocalDateTime parsedPlannedDepartureDateTime = LocalDateTime.parse(plannedDepartureDateTime);
        WeatherData weatherData = weatherService.weatherFor(route, parsedPlannedDepartureDateTime);
        double sunAzimuthDegrees = sunAzimuthDegrees(plannedDepartureDateTime);
        List<SightseeingManeuverResponse> sightseeingManeuvers = sightseeingManeuvers(route, sightseeingTimeMinutes, cruiseSpeedKmh, sunAzimuthDegrees);
        List<Waypoint> flightPath = flightPath(route, airportWaypoint(departureAirport), sightseeingManeuvers);
        double sunExposureScore = sunExposureScore(route, airportWaypoint(departureAirport), sunAzimuthDegrees, plannedDepartureDateTime);
        RouteScore score = routeScoringService.score(
                route,
                estimatedTimeMinutes,
                usefulFlightTimeMinutes,
                estimatedCost,
                request.preference(),
                weatherData.weatherScore()
        );
        double totalScore = round(clampScore(score.totalScore() * 0.85 + sunExposureScore * 0.15));

        return new RecommendedRouteResponse(
                route.id(),
                route.name(),
                route.description(),
                route.routeType(),
                route.waypoints(),
                flightPath,
                sightseeingManeuvers,
                round(approximateDistanceKm),
                round(baseFlightTimeMinutes),
                round(sightseeingTimeMinutes),
                plannedDepartureDateTime,
                round(sunAzimuthDegrees),
                round(sunExposureScore),
                sunExposureSummary(sunExposureScore, sunAzimuthDegrees),
                round(estimatedTimeMinutes),
                round(estimatedTimeHours),
                routeDurationCategory(estimatedTimeMinutes, usefulFlightTimeMinutes),
                roundOneDecimal(estimatedFuelLiters),
                fuelPrice.pricePerLiter(),
                fuelPrice.source().name(),
                roundTwoDecimals(estimatedCost),
                totalScore,
                score.weatherScore(),
                weatherData.windKmh(),
                weatherData.cloudCoverPercent(),
                weatherData.precipitationProbability(),
                weatherData.visibilityKm(),
                weatherData.temperatureCelsius(),
                null,
                score,
                explanation(route, request, usefulFlightTimeMinutes, estimatedTimeMinutes, sightseeingTimeMinutes, estimatedFuelLiters, estimatedCost, score, weatherData, sunExposureScore),
                routeWarnings(estimatedTimeMinutes, usefulFlightTimeMinutes, estimatedCost)
        );
    }

    private RecommendedRouteResponse withRouteWeatherSummary(
            RecommendedRouteResponse recommendation,
            FlightRoute route,
            Airport departureAirport
    ) {
        LocalDateTime plannedDepartureDateTime = LocalDateTime.parse(recommendation.plannedDepartureDateTime());
        WeatherData fallbackWeatherData = new WeatherData(
                recommendation.windKmh(),
                recommendation.cloudCoverPercent(),
                recommendation.precipitationProbability(),
                recommendation.visibilityKm(),
                recommendation.temperatureCelsius(),
                recommendation.weatherScore()
        );
        RouteWeatherSummary routeWeatherSummary = routeWeatherSummary(route, departureAirport, plannedDepartureDateTime, fallbackWeatherData);

        return new RecommendedRouteResponse(
                recommendation.id(),
                recommendation.name(),
                recommendation.description(),
                recommendation.routeType(),
                recommendation.waypoints(),
                recommendation.flightPath(),
                recommendation.sightseeingManeuvers(),
                recommendation.approximateDistanceKm(),
                recommendation.baseFlightTimeMinutes(),
                recommendation.sightseeingTimeMinutes(),
                recommendation.plannedDepartureDateTime(),
                recommendation.sunAzimuthDegrees(),
                recommendation.sunExposureScore(),
                recommendation.sunExposureSummary(),
                recommendation.estimatedTimeMinutes(),
                recommendation.estimatedTimeHours(),
                recommendation.routeDurationCategory(),
                recommendation.estimatedFuelLiters(),
                recommendation.fuelPricePerLiter(),
                recommendation.fuelPriceSource(),
                recommendation.estimatedCost(),
                recommendation.totalScore(),
                recommendation.weatherScore(),
                recommendation.windKmh(),
                recommendation.cloudCoverPercent(),
                recommendation.precipitationProbability(),
                recommendation.visibilityKm(),
                recommendation.temperatureCelsius(),
                routeWeatherSummary,
                recommendation.scoreBreakdown(),
                recommendation.explanation(),
                recommendation.warnings()
        );
    }

    private RouteWeatherSummary routeWeatherSummary(
            FlightRoute route,
            Airport departureAirport,
            LocalDateTime plannedDepartureDateTime,
            WeatherData fallbackWeatherData
    ) {
        List<WeatherData> pointWeatherData = routeWeatherPoints(route, departureAirport).stream()
                .map(point -> weatherForPoint(point, plannedDepartureDateTime, fallbackWeatherData))
                .toList();

        return new RouteWeatherSummary(
                round(pointWeatherData.stream().mapToDouble(WeatherData::windKmh).average().orElse(fallbackWeatherData.windKmh())),
                round(pointWeatherData.stream().mapToDouble(WeatherData::windKmh).max().orElse(fallbackWeatherData.windKmh())),
                round(pointWeatherData.stream().mapToDouble(WeatherData::cloudCoverPercent).average().orElse(fallbackWeatherData.cloudCoverPercent())),
                round(pointWeatherData.stream().mapToDouble(WeatherData::precipitationProbability).max().orElse(fallbackWeatherData.precipitationProbability())),
                round(pointWeatherData.stream().mapToDouble(WeatherData::visibilityKm).min().orElse(fallbackWeatherData.visibilityKm())),
                round(pointWeatherData.stream().mapToDouble(WeatherData::temperatureCelsius).average().orElse(fallbackWeatherData.temperatureCelsius()))
        );
    }

    private WeatherData weatherForPoint(Waypoint point, LocalDateTime plannedDepartureDateTime, WeatherData fallbackWeatherData) {
        try {
            return weatherService.weatherFor(point.latitude(), point.longitude(), plannedDepartureDateTime);
        } catch (WeatherServiceException exception) {
            return fallbackWeatherData;
        }
    }

    private List<Waypoint> routeWeatherPoints(FlightRoute route, Airport departureAirport) {
        Map<String, Waypoint> selectedPoints = new LinkedHashMap<>();
        addWeatherPoint(selectedPoints, airportWaypoint(departureAirport));

        if (!route.waypoints().isEmpty()) {
            addWeatherPoint(selectedPoints, route.waypoints().getFirst());
            addWeatherPoint(selectedPoints, route.waypoints().getLast());
        }

        return selectedPoints.values().stream()
                .limit(3)
                .toList();
    }

    private void addWeatherPoint(Map<String, Waypoint> points, Waypoint point) {
        points.putIfAbsent(point.latitude() + ":" + point.longitude(), point);
    }

    private double sightseeingTimeMinutes(FlightRoute route, double baseFlightTimeMinutes, double usefulFlightTimeMinutes) {
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

    private Waypoint airportWaypoint(Airport airport) {
        return new Waypoint(airport.code(), airport.latitude(), airport.longitude());
    }

    private List<Waypoint> flightPath(
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

    private List<SightseeingManeuverResponse> sightseeingManeuvers(
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
                    double preferredViewingBearingDegrees = preferredViewingBearingDegrees(sunAzimuthDegrees);
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
        addWeatherPoint(selectedWaypoints, route.waypoints().getFirst());
        addWeatherPoint(selectedWaypoints, route.waypoints().getLast());

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

    private double preferredViewingBearingDegrees(double sunAzimuthDegrees) {
        if (sunAzimuthDegrees == 0.0) {
            return 0.0;
        }

        return (sunAzimuthDegrees + 90.0) % 360.0;
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

    private double sunAzimuthDegrees(String plannedDepartureDateTime) {
        int minutes = localTimeMinutes(plannedDepartureDateTime);
        if (minutes < 420 || minutes > 1200) {
            return 0.0;
        }

        double daylightProgress = (minutes - 420.0) / (1200.0 - 420.0);

        return 90.0 + daylightProgress * 180.0;
    }

    private double sunExposureScore(FlightRoute route, Waypoint departureAirport, double sunAzimuthDegrees, String plannedDepartureDateTime) {
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

    private String sunExposureSummary(double sunExposureScore, double sunAzimuthDegrees) {
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

    private int localTimeMinutes(String plannedDepartureDateTime) {
        String localTime = plannedDepartureDateTime.contains("T")
                ? plannedDepartureDateTime.substring(plannedDepartureDateTime.indexOf('T') + 1)
                : plannedDepartureDateTime;
        String[] parts = localTime.split(":");

        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }

    private double normalizedScenicScore(FlightRoute route) {
        if (route.scenicScore() <= 10.0) {
            return route.scenicScore() * 10.0;
        }

        return Math.min(100.0, route.scenicScore());
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
            double sightseeingTimeMinutes,
            double estimatedFuelLiters,
            double estimatedCost,
            RouteScore score,
            WeatherData weatherData,
            double sunExposureScore
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
        String sightseeingFit = sightseeingTimeMinutes > 0.0
                ? ", incluyendo " + round(sightseeingTimeMinutes) + " minutos de observacion escenica local"
                : "";
        String sunFit = sunExposureScore >= 80.0
                ? " La orientacion solar es favorable para evitar sol frontal."
                : sunExposureScore >= 55.0
                ? " La orientacion solar es aceptable para la hora indicada."
                : " La orientacion solar penaliza la ruta por posibles tramos con sol frontal.";
        String routeExperience = isInterIslandRoute(route) && !isInterIslandPreference(request.preference())
                ? " Es una travesia entre islas, por lo que se prioriza por debajo de rutas locales salvo preferencia explicita."
                : "";

        return "Esta ruta " + timeFit + ", tiene " + scenicFit
                + ", " + costFit + ", " + weatherFit + " y " + preferenceFit + ". Se estiman "
                + round(estimatedTimeMinutes) + " minutos" + sightseeingFit + ", "
                + roundOneDecimal(estimatedFuelLiters) + " litros y "
                + roundTwoDecimals(estimatedCost) + " EUR." + sunFit + routeExperience;
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

    private boolean isInterIslandRoute(FlightRoute route) {
        return route.tags().stream()
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

    private double clampScore(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }

    private record Leg(
            Waypoint first,
            Waypoint second
    ) {
    }

    private record ScoredRoute(
            FlightRoute route,
            RecommendedRouteResponse recommendation
    ) {
    }
}
