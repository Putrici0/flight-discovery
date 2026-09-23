package flightdiscovery.paull.application.recommendation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import flightdiscovery.paull.api.recommendation.RouteDurationCategory;
import flightdiscovery.paull.domain.model.FlightRoute;

@Service
public class RecommendationSelectionService {

    private static final int MAX_RECOMMENDATIONS = 5;
    private static final double HIGH_SHORT_ROUTE_SCORE = 85.0;
    private static final int MAX_RECOMMENDED_ROUTES_PER_WAYPOINT = 2;

    private final RouteSimilarityService routeSimilarityService;

    public RecommendationSelectionService(RouteSimilarityService routeSimilarityService) {
        this.routeSimilarityService = routeSimilarityService;
    }

    public List<ScoredRoute> diverseRecommendations(List<ScoredRoute> scoredRoutes, String preference) {
        return selectDiverseRecommendations(scoredRoutes, preference).selectedRoutes();
    }

    public RecommendationSelectionResult selectDiverseRecommendations(List<ScoredRoute> scoredRoutes, String preference) {
        List<ScoredRoute> sortedRoutes = sortedBySelection(scoredRoutes, preference);
        List<ScoredRoute> primaryRoutes = isInterIslandPreference(preference)
                ? sortedRoutes
                : sortedRoutes.stream()
                .filter(scoredRoute -> !isInterIslandRoute(scoredRoute.route()))
                .toList();
        List<ScoredRoute> selectedRoutes = new ArrayList<>();
        Set<String> waypointSignatures = new HashSet<>();
        Map<String, Integer> waypointUsageCounts = new HashMap<>();
        List<ScoredRoute> similarRoutes = new ArrayList<>();
        boolean shortPreference = isShortPreference(preference);

        List<ScoredRoute> goodFitRoutes = primaryRoutes.stream()
                .filter(scoredRoute -> routeDurationCategory(scoredRoute) == RouteDurationCategory.GOOD_FIT)
                .toList();
        addDiverseRoutes(goodFitRoutes, selectedRoutes, similarRoutes, waypointSignatures, waypointUsageCounts, false, false, true, Math.min(2, goodFitRoutes.size()));

        List<ScoredRoute> preferredDurationRoutes = primaryRoutes.stream()
                .filter(scoredRoute -> routeDurationCategory(scoredRoute) == RouteDurationCategory.GOOD_FIT
                        || routeDurationCategory(scoredRoute) == RouteDurationCategory.LONG
                        || routeDurationCategory(scoredRoute) == RouteDurationCategory.SLIGHTLY_OVER_TIME
                        || (routeDurationCategory(scoredRoute) == RouteDurationCategory.SHORT && isHighScoringShortRoute(scoredRoute))
                        || (shortPreference && routeDurationCategory(scoredRoute) == RouteDurationCategory.TOO_SHORT))
                .toList();
        addDiverseRoutes(preferredDurationRoutes, selectedRoutes, similarRoutes, waypointSignatures, waypointUsageCounts, true, true, true, MAX_RECOMMENDATIONS);
        addDiverseRoutes(preferredDurationRoutes, selectedRoutes, similarRoutes, waypointSignatures, waypointUsageCounts, true, false, true, MAX_RECOMMENDATIONS);
        addDiverseRoutes(preferredDurationRoutes, selectedRoutes, similarRoutes, waypointSignatures, waypointUsageCounts, false, false, true, MAX_RECOMMENDATIONS);
        addDiverseRoutes(preferredDurationRoutes, selectedRoutes, similarRoutes, waypointSignatures, waypointUsageCounts, false, false, false, MAX_RECOMMENDATIONS);

        List<ScoredRoute> shortRoutes = primaryRoutes.stream()
                .filter(scoredRoute -> routeDurationCategory(scoredRoute) == RouteDurationCategory.SHORT)
                .toList();
        addDiverseRoutes(shortRoutes, selectedRoutes, similarRoutes, waypointSignatures, waypointUsageCounts, false, false, true, MAX_RECOMMENDATIONS);
        addDiverseRoutes(shortRoutes, selectedRoutes, similarRoutes, waypointSignatures, waypointUsageCounts, false, false, false, MAX_RECOMMENDATIONS);

        boolean hasNonTooShortAlternative = primaryRoutes.stream()
                .anyMatch(scoredRoute -> routeDurationCategory(scoredRoute) != RouteDurationCategory.TOO_SHORT
                        && selectedRoutes.stream().noneMatch(selectedRoute -> selectedRoute.route().id().equals(scoredRoute.route().id())));
        if (shortPreference || !hasNonTooShortAlternative) {
            List<ScoredRoute> tooShortRoutes = primaryRoutes.stream()
                    .filter(scoredRoute -> routeDurationCategory(scoredRoute) == RouteDurationCategory.TOO_SHORT)
                    .toList();
            addDiverseRoutes(tooShortRoutes, selectedRoutes, similarRoutes, waypointSignatures, waypointUsageCounts, false, false, true, MAX_RECOMMENDATIONS);
            addDiverseRoutes(tooShortRoutes, selectedRoutes, similarRoutes, waypointSignatures, waypointUsageCounts, false, false, false, MAX_RECOMMENDATIONS);
        }

        if (!isInterIslandPreference(preference) && selectedRoutes.size() < MAX_RECOMMENDATIONS) {
            List<ScoredRoute> interIslandRoutes = sortedRoutes.stream()
                    .filter(scoredRoute -> isInterIslandRoute(scoredRoute.route()))
                    .toList();
            addDiverseRoutes(interIslandRoutes, selectedRoutes, similarRoutes, waypointSignatures, waypointUsageCounts, false, false, true, MAX_RECOMMENDATIONS);
            addDiverseRoutes(interIslandRoutes, selectedRoutes, similarRoutes, waypointSignatures, waypointUsageCounts, false, false, false, MAX_RECOMMENDATIONS);
        }

        List<ScoredRoute> rebalancedRoutes = rebalanceWaypointDiversity(selectedRoutes, sortedRoutes, similarRoutes);
        List<ScoredRoute> finalRoutes = rebalancedRoutes.stream()
                .sorted(selectionComparator(preference))
                .toList();

        return new RecommendationSelectionResult(finalRoutes, similarRoutes);
    }

    private List<ScoredRoute> rebalanceWaypointDiversity(
            List<ScoredRoute> selectedRoutes,
            List<ScoredRoute> sortedRoutes,
            List<ScoredRoute> similarRoutes
    ) {
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

            if (isTooSimilarToSelected(candidate, rebalancedRoutes)) {
                recordSimilarRoute(candidate, similarRoutes);
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

            if (isTooSimilarToSelected(candidate, rebalancedRoutes)) {
                recordSimilarRoute(candidate, similarRoutes);
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
                        (ScoredRoute scoredRoute) -> scoredRoute.recommendation().sunExposureScore()
                ).reversed())
                .thenComparing(Comparator.comparingDouble(
                        (ScoredRoute scoredRoute) -> scoredRoute.recommendation().weatherScore()
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
            List<ScoredRoute> similarRoutes,
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

            if (isTooSimilarToSelected(candidate, selectedRoutes)) {
                recordSimilarRoute(candidate, similarRoutes);
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

    private boolean isTooSimilarToSelected(ScoredRoute candidate, List<ScoredRoute> selectedRoutes) {
        return selectedRoutes.stream()
                .anyMatch(selectedRoute -> routeSimilarityService.areTooSimilar(candidate.route(), selectedRoute.route()));
    }

    private void recordSimilarRoute(ScoredRoute candidate, List<ScoredRoute> similarRoutes) {
        if (similarRoutes.stream().noneMatch(route -> route.route().id().equals(candidate.route().id()))) {
            similarRoutes.add(candidate);
        }
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
}
