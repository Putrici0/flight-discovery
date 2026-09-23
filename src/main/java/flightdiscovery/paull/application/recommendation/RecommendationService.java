package flightdiscovery.paull.application.recommendation;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import flightdiscovery.paull.api.recommendation.RecommendationCandidateDebug;
import flightdiscovery.paull.api.recommendation.RecommendationDebugInfo;
import flightdiscovery.paull.api.recommendation.RecommendationDevelopmentDebugResponse;
import flightdiscovery.paull.api.recommendation.RecommendationRequest;
import flightdiscovery.paull.api.recommendation.RecommendationResponse;
import flightdiscovery.paull.api.recommendation.RecommendationRouteDiscardDebug;
import flightdiscovery.paull.api.recommendation.RecommendedRouteResponse;
import flightdiscovery.paull.api.recommendation.SightseeingManeuverResponse;
import flightdiscovery.paull.domain.calculation.RouteCalculationService;
import flightdiscovery.paull.domain.model.Aircraft;
import flightdiscovery.paull.domain.model.Airport;
import flightdiscovery.paull.domain.model.AirportFuelPrice;
import flightdiscovery.paull.domain.model.FlightRoute;
import flightdiscovery.paull.domain.model.FuelPrice;
import flightdiscovery.paull.domain.model.FuelPriceSource;
import flightdiscovery.paull.domain.model.RouteScore;
import flightdiscovery.paull.domain.model.RouteOrientationAnalysis;
import flightdiscovery.paull.domain.model.RouteWeatherSummary;
import flightdiscovery.paull.domain.model.WeatherData;
import flightdiscovery.paull.domain.model.Waypoint;
import flightdiscovery.paull.domain.repository.MockAircraftRepository;
import flightdiscovery.paull.domain.repository.MockAirportRepository;
import flightdiscovery.paull.domain.repository.MockFuelPriceRepository;
import flightdiscovery.paull.domain.repository.MockRouteRepository;
import flightdiscovery.paull.domain.scoring.RouteScoringService;
import flightdiscovery.paull.domain.weather.MockWeatherService;
import flightdiscovery.paull.domain.weather.OpenMeteoWeatherService;
import flightdiscovery.paull.domain.weather.WeatherService;

@Service
public class RecommendationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecommendationService.class);
    private static final int MIN_RECOMMENDATIONS = 3;
    private static final double LOW_TIME_MARGIN_RATIO = 0.90;
    private static final double HIGH_COST_THRESHOLD_EUR = 150.0;
    private static final double SIGHTSEEING_TARGET_USEFUL_TIME_RATIO = 0.85;
    private static final String TIME_DISCARD_REASON = "Estimated route time exceeds useful available time plus 25% tolerance";
    private static final String FINAL_SELECTION_DISCARD_REASON = "Not selected after score and diversity recommendation limit";

    private final MockRouteRepository routeRepository;
    private final MockAirportRepository airportRepository;
    private final MockAircraftRepository aircraftRepository;
    private final MockFuelPriceRepository fuelPriceRepository;
    private final RouteCalculationService routeCalculationService;
    private final RouteScoringService routeScoringService;
    private final RouteCandidateGenerator routeCandidateGenerator;
    private final RecommendationTimeService recommendationTimeService;
    private final RecommendationSelectionService recommendationSelectionService;
    private final SightseeingService sightseeingService;
    private final SunExposureService sunExposureService;
    private final RouteWeatherSummaryService routeWeatherSummaryService;
    private final WeatherService weatherService;

    public RecommendationService(
            MockRouteRepository routeRepository,
            MockAirportRepository airportRepository,
            MockAircraftRepository aircraftRepository,
            MockFuelPriceRepository fuelPriceRepository,
            RouteCalculationService routeCalculationService,
            RouteScoringService routeScoringService,
            RouteCandidateGenerator routeCandidateGenerator,
            RecommendationTimeService recommendationTimeService,
            RecommendationSelectionService recommendationSelectionService,
            SightseeingService sightseeingService,
            SunExposureService sunExposureService,
            RouteWeatherSummaryService routeWeatherSummaryService,
            WeatherService weatherService
    ) {
        this.routeRepository = routeRepository;
        this.airportRepository = airportRepository;
        this.aircraftRepository = aircraftRepository;
        this.fuelPriceRepository = fuelPriceRepository;
        this.routeCalculationService = routeCalculationService;
        this.routeScoringService = routeScoringService;
        this.routeCandidateGenerator = routeCandidateGenerator;
        this.recommendationTimeService = recommendationTimeService;
        this.recommendationSelectionService = recommendationSelectionService;
        this.sightseeingService = sightseeingService;
        this.sunExposureService = sunExposureService;
        this.routeWeatherSummaryService = routeWeatherSummaryService;
        this.weatherService = weatherService;
    }

    public RecommendationResponse recommend(RecommendationRequest request) {
        RecommendationRun run = runRecommendation(request);
        int scoredRoutesDiscardedByTime = run.scoredRoutes().size() - run.usefulTimeViableRoutes().size();
        RecommendationDebugInfo debugInfo = new RecommendationDebugInfo(
                run.generationResult().generatedCandidateRoutes(),
                run.generationResult().discardedByTimeRoutes() + scoredRoutesDiscardedByTime,
                run.viableRecommendations().size()
        );

        return new RecommendationResponse(run.viableRecommendations(), warnings(run.viableRecommendations()), debugInfo);
    }

    public RecommendationDevelopmentDebugResponse debug(RecommendationRequest request) {
        RecommendationRun run = runRecommendation(request);
        List<RecommendationRouteDiscardDebug> discards = new ArrayList<>();
        discards.addAll(run.generationResult().discardedRoutes().stream()
                .map(discard -> toDiscardDebug(discard, "GENERATED_TIME_FILTER"))
                .toList());
        discards.addAll(run.scoredRoutes().stream()
                .filter(scoredRoute -> !recommendationTimeService.isWithinAllowedTime(scoredRoute.recommendation(), run.usefulAvailableTimeMinutes()))
                .map(scoredRoute -> new RecommendationRouteDiscardDebug(
                        scoredRoute.route().id(),
                        scoredRoute.route().name(),
                        "SCORING_TIME_FILTER",
                        TIME_DISCARD_REASON,
                        scoredRoute.recommendation().estimatedTimeMinutes(),
                        round(recommendationTimeService.allowedTimeLimitMinutes(run.usefulAvailableTimeMinutes()))
                ))
                .toList());
        discards.addAll(run.usefulTimeViableRoutes().stream()
                .filter(scoredRoute -> run.viableRecommendations().stream()
                        .noneMatch(recommendation -> recommendation.id().equals(scoredRoute.recommendation().id())))
                .filter(scoredRoute -> run.similarRoutes().stream()
                        .noneMatch(similarRoute -> similarRoute.route().id().equals(scoredRoute.route().id())))
                .map(scoredRoute -> new RecommendationRouteDiscardDebug(
                        scoredRoute.route().id(),
                        scoredRoute.route().name(),
                        "FINAL_SELECTION",
                        FINAL_SELECTION_DISCARD_REASON,
                        scoredRoute.recommendation().estimatedTimeMinutes(),
                        round(recommendationTimeService.allowedTimeLimitMinutes(run.usefulAvailableTimeMinutes()))
                ))
                .toList());
        discards.addAll(run.similarRoutes().stream()
                .map(scoredRoute -> new RecommendationRouteDiscardDebug(
                        scoredRoute.route().id(),
                        scoredRoute.route().name(),
                        "FINAL_SIMILARITY_FILTER",
                        "Too similar to an already selected recommendation",
                        scoredRoute.recommendation().estimatedTimeMinutes(),
                        round(recommendationTimeService.allowedTimeLimitMinutes(run.usefulAvailableTimeMinutes()))
                ))
                .toList());
        List<RecommendationCandidateDebug> candidates = candidateDebugRows(
                run.scoredRoutes(),
                run.generationResult().discardedRoutes(),
                discards,
                run.viableRecommendations()
        );

        return new RecommendationDevelopmentDebugResponse(
                request,
                run.aircraft(),
                request.effectivePlannedDepartureDateTime(),
                weatherProvider(run.viableRecommendations()),
                run.viableScoredRoutes().stream()
                        .flatMap(scoredRoute -> routeWeatherSummaryService.routeWeatherPoints(scoredRoute.route(), run.departureAirport()).stream())
                        .distinct()
                        .toList(),
                run.fuelPrice().fuelType(),
                run.fuelPrice().pricePerLiter(),
                run.fuelPrice().source().name(),
                run.fuelPrice().isMock(),
                run.fuelPrice().airportCode(),
                round(run.usefulAvailableTimeMinutes() * SIGHTSEEING_TARGET_USEFUL_TIME_RATIO),
                round(run.usefulAvailableTimeMinutes()),
                run.generationResult().compatibleWaypointCount(),
                run.generationResult().generatedCandidateRoutes(),
                discards.size(),
                run.viableRecommendations().size(),
                candidates,
                discards,
                run.viableRecommendations()
        );
    }

    private RecommendationRun runRecommendation(RecommendationRequest request) {
        Aircraft aircraft = resolveAircraft(request);
        double cruiseSpeedKmh = resolveCruiseSpeed(request, aircraft);
        double fuelBurnLitersPerHour = resolveFuelBurn(request, aircraft);
        double usefulAvailableTimeMinutes = recommendationTimeService.usefulAvailableTimeMinutes(
                request.availableFlightTimeMinutes(),
                aircraft,
                request.effectiveSafetyMarginPercent()
        );
        Airport departureAirport = resolveDepartureAirport(request.departureAirport());
        FuelPrice fuelPrice = resolveFuelPrice(request, aircraft, departureAirport);
        WeatherService activeWeatherService = resolveWeatherService(request);
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

        List<ScoredRoute> scoredRoutes = routesForScoring.stream()
                .map(route -> new ScoredRoute(
                        route,
                        toRecommendation(route, request, departureAirport, usefulAvailableTimeMinutes, cruiseSpeedKmh, fuelBurnLitersPerHour, fuelPrice, activeWeatherService)
                ))
                .toList();
        List<ScoredRoute> usefulTimeViableRoutes = scoredRoutes.stream()
                .filter(scoredRoute -> recommendationTimeService.isWithinAllowedTime(scoredRoute.recommendation(), usefulAvailableTimeMinutes))
                .toList();
        RecommendationSelectionResult selectionResult = recommendationSelectionService.selectDiverseRecommendations(usefulTimeViableRoutes, request.preference());
        List<ScoredRoute> viableScoredRoutes = selectionResult.selectedRoutes();
        List<RecommendedRouteResponse> viableRecommendations = viableScoredRoutes.stream()
                .map(ScoredRoute::recommendation)
                .toList();
        LOGGER.info("Recommendation diagnostics: scoredRecommendations={} discardedScoredRecommendationsByUsefulTime={} usefulTimeViableScoredRecommendations={} returnedRecommendations={}",
                scoredRoutes.size(),
                scoredRoutes.size() - usefulTimeViableRoutes.size(),
                usefulTimeViableRoutes.size(),
                viableRecommendations.size());

        return new RecommendationRun(
                aircraft,
                departureAirport,
                fuelPrice,
                usefulAvailableTimeMinutes,
                generationResult,
                scoredRoutes,
                usefulTimeViableRoutes,
                viableScoredRoutes,
                viableRecommendations,
                selectionResult.similarRoutes()
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

    private RecommendedRouteResponse toRecommendation(
            FlightRoute route,
            RecommendationRequest request,
            Airport departureAirport,
            double usefulFlightTimeMinutes,
            double cruiseSpeedKmh,
            double fuelBurnLitersPerHour,
            FuelPrice fuelPrice,
            WeatherService activeWeatherService
    ) {
        double approximateDistanceKm = routeCalculationService.totalDistanceKm(route);
        double baseFlightTimeHours = routeCalculationService.estimatedTimeHours(approximateDistanceKm, cruiseSpeedKmh);
        double baseFlightTimeMinutes = routeCalculationService.estimatedTimeMinutes(baseFlightTimeHours);
        double sightseeingTimeMinutes = sightseeingService.sightseeingTimeMinutes(route, baseFlightTimeMinutes, usefulFlightTimeMinutes);
        double estimatedTimeMinutes = baseFlightTimeMinutes + sightseeingTimeMinutes;
        double estimatedTimeHours = estimatedTimeMinutes / 60.0;
        double estimatedFuelLiters = routeCalculationService.estimatedFuelLiters(estimatedTimeMinutes, fuelBurnLitersPerHour);
        double estimatedCost = routeCalculationService.estimatedCost(estimatedFuelLiters, fuelPrice.pricePerLiter());
        String plannedDepartureDateTime = request.effectivePlannedDepartureDateTime();
        LocalDateTime parsedPlannedDepartureDateTime = LocalDateTime.parse(plannedDepartureDateTime);
        WeatherData fallbackWeatherData = activeWeatherService.weatherFor(route, parsedPlannedDepartureDateTime);
        RouteWeatherSummary routeWeatherSummary = routeWeatherSummaryService.routeWeatherSummary(
                route,
                departureAirport,
                parsedPlannedDepartureDateTime,
                fallbackWeatherData,
                activeWeatherService
        );
        WeatherData weatherData = new WeatherData(
                routeWeatherSummary.averageWindKmh(),
                routeWeatherSummary.averageCloudCoverPercent(),
                routeWeatherSummary.maxPrecipitationProbability(),
                routeWeatherSummary.minVisibilityKm(),
                routeWeatherSummary.averageTemperatureCelsius(),
                routeWeatherSummary.weatherScore(),
                routeWeatherSummary.provider(),
                routeWeatherSummary.isMock()
        );
        double sunAzimuthDegrees = sunExposureService.sunAzimuthDegrees(plannedDepartureDateTime);
        List<SightseeingManeuverResponse> sightseeingManeuvers = sightseeingService.sightseeingManeuvers(route, sightseeingTimeMinutes, cruiseSpeedKmh, sunAzimuthDegrees);
        List<Waypoint> flightPath = sightseeingService.flightPath(route, airportWaypoint(departureAirport), sightseeingManeuvers);
        RouteOrientationAnalysis orientationAnalysis = sunExposureService.orientationAnalysis(
                route,
                airportWaypoint(departureAirport),
                sunAzimuthDegrees,
                plannedDepartureDateTime
        );
        RouteScore score = routeScoringService.score(
                route,
                estimatedTimeMinutes,
                usefulFlightTimeMinutes,
                estimatedCost,
                request.preference(),
                weatherData.weatherScore(),
                orientationAnalysis.orientationScore()
        );
        double totalScore = score.totalScore();

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
                round(orientationAnalysis.sunExposureScore()),
                orientationAnalysis.summary(),
                round(orientationAnalysis.visualOrientationScore()),
                round(orientationAnalysis.orientationScore()),
                orientationAnalysis.predominantSunPosition(),
                orientationAnalysis.recommendedViewingSide(),
                orientationAnalysis.favorableReason(),
                orientationAnalysis.frontalSunLegs(),
                orientationAnalysis.legs(),
                round(estimatedTimeMinutes),
                round(estimatedTimeHours),
                recommendationTimeService.routeDurationCategory(estimatedTimeMinutes, usefulFlightTimeMinutes),
                roundOneDecimal(estimatedFuelLiters),
                fuelPrice.pricePerLiter(),
                fuelPrice.source().name(),
                fuelPrice.isMock(),
                fuelPrice.fuelType(),
                fuelPrice.airportCode(),
                roundTwoDecimals(estimatedCost),
                totalScore,
                score.weatherScore(),
                weatherData.provider(),
                weatherData.isMock(),
                weatherData.windKmh(),
                weatherData.cloudCoverPercent(),
                weatherData.precipitationProbability(),
                weatherData.visibilityKm(),
                weatherData.temperatureCelsius(),
                routeWeatherSummary,
                score,
                explanation(route, request, usefulFlightTimeMinutes, estimatedTimeMinutes, sightseeingTimeMinutes, estimatedFuelLiters, estimatedCost, score, weatherData, orientationAnalysis),
                routeWarnings(estimatedTimeMinutes, usefulFlightTimeMinutes, estimatedCost, weatherData)
        );
    }

    private Waypoint airportWaypoint(Airport airport) {
        return new Waypoint(airport.code(), airport.latitude(), airport.longitude());
    }

    private List<String> warnings(List<RecommendedRouteResponse> recommendations) {
        if (recommendations.size() >= MIN_RECOMMENDATIONS) {
            return List.of();
        }

        return List.of("Fewer than 3 candidate routes fit within the available flight time plus 25% tolerance.");
    }

    private List<String> routeWarnings(
            double estimatedTimeMinutes,
            double availableTimeMinutes,
            double estimatedCost,
            WeatherData weatherData
    ) {
        List<String> warnings = new ArrayList<>();

        if (estimatedTimeMinutes > availableTimeMinutes) {
            warnings.add("Esta ruta supera ligeramente el tiempo disponible");
        } else if (estimatedTimeMinutes >= availableTimeMinutes * LOW_TIME_MARGIN_RATIO) {
            warnings.add("Esta ruta deja poco margen de tiempo");
        }

        if (estimatedCost >= HIGH_COST_THRESHOLD_EUR) {
            warnings.add("El coste estimado es alto");
        }

        if (weatherData.isMock()) {
            warnings.add("La meteorologia usada es simulada/mock");
        }

        return warnings;
    }

    private Aircraft resolveAircraft(RecommendationRequest request) {
        return aircraftRepository.findById(request.aircraftId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "aircraftId must match a known aircraft"
                ));
    }

    private Airport resolveDepartureAirport(String departureAirportCode) {
        return airportRepository.findByCode(departureAirportCode)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "departureAirport must match a known airport"
                ));
    }

    private WeatherService resolveWeatherService(RecommendationRequest request) {
        String requestedProvider = request.requestedWeatherProvider();
        if ("mock".equals(requestedProvider)) {
            return new MockWeatherService();
        }

        if ("open-meteo".equals(requestedProvider)) {
            return new OpenMeteoWeatherService();
        }

        return weatherService;
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

    private FuelPrice resolveFuelPrice(RecommendationRequest request, Aircraft aircraft, Airport departureAirport) {
        if (request.fuelPricePerLiter() != null) {
            return new FuelPrice(
                    departureAirport.code(),
                    aircraft.fuelType(),
                    request.fuelPricePerLiter(),
                    "EUR",
                    FuelPriceSource.MANUAL,
                    false
            );
        }

        return fuelPriceRepository.findByAirportCodeAndFuelType(departureAirport.code(), aircraft.fuelType())
                .map(this::toFuelPrice)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "departureAirport and aircraft fuelType must have a configured mock fuel price"
                ));
    }

    private FuelPrice toFuelPrice(AirportFuelPrice airportFuelPrice) {
        return new FuelPrice(
                airportFuelPrice.airportCode(),
                airportFuelPrice.fuelType(),
                airportFuelPrice.pricePerLiter(),
                airportFuelPrice.currency(),
                FuelPriceSource.MOCK,
                airportFuelPrice.isMock()
        );
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
            RouteOrientationAnalysis orientationAnalysis
    ) {
        String timeFit = recommendationTimeService.timeFitText(estimatedTimeMinutes, usefulFlightTimeMinutes);
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
        String weatherProviderText = weatherData.isMock()
                ? "meteorologia simulada/mock"
                : "meteorologia estimada con Open-Meteo";
        String weatherFit = weatherData.weatherScore() >= 75.0
                ? weatherProviderText + " favorable, con viento bajo y baja probabilidad de precipitacion"
                : weatherData.weatherScore() >= 50.0
                ? weatherProviderText + " aceptable"
                : weatherProviderText + " desfavorable por viento, precipitacion, nubosidad o visibilidad";
        String sightseeingFit = sightseeingTimeMinutes > 0.0
                ? ", incluyendo " + round(sightseeingTimeMinutes) + " minutos de observacion escenica local"
                : "";
        String sunFit = " " + orientationAnalysis.favorableReason();
        String routeExperience = isInterIslandRoute(route) && !isInterIslandPreference(request.preference())
                ? " Es una travesia entre islas, por lo que se prioriza por debajo de rutas locales salvo preferencia explicita."
                : "";

        return "Esta ruta " + timeFit + ", tiene " + scenicFit
                + ", " + costFit + ", " + weatherFit + " y " + preferenceFit + ". Se estiman "
                + round(estimatedTimeMinutes) + " minutos" + sightseeingFit + ", "
                + roundOneDecimal(estimatedFuelLiters) + " litros y "
                + roundTwoDecimals(estimatedCost) + " EUR." + sunFit + routeExperience;
    }

    private String weatherProvider(List<RecommendedRouteResponse> recommendations) {
        if (recommendations.stream().anyMatch(recommendation -> !recommendation.weatherIsMock())) {
            return "open-meteo";
        }

        return "mock";
    }

    private boolean routeMatchesPreference(FlightRoute route, String preference) {
        if (preference == null || preference.isBlank()) {
            return false;
        }

        return route.tags().stream()
                .anyMatch(tag -> tag.equalsIgnoreCase(preference.trim()));
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

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double roundOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private double roundTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record RecommendationRun(
            Aircraft aircraft,
            Airport departureAirport,
            FuelPrice fuelPrice,
            double usefulAvailableTimeMinutes,
            RouteGenerationResult generationResult,
            List<ScoredRoute> scoredRoutes,
            List<ScoredRoute> usefulTimeViableRoutes,
            List<ScoredRoute> viableScoredRoutes,
            List<RecommendedRouteResponse> viableRecommendations,
            List<ScoredRoute> similarRoutes
    ) {
    }
}
