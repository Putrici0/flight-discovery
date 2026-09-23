package flightdiscovery.paull.application.recommendation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import flightdiscovery.paull.domain.model.FlightRoute;
import flightdiscovery.paull.domain.model.RouteType;

@Service
public class RouteCandidateSelectionService {

    private static final double MAX_ALLOWED_TIME_OVERRUN_RATIO = 1.25;
    private static final double TARGET_DURATION_RATIO = 0.85;
    private static final int MAX_GENERATED_ROUTES = 80;
    private static final double PREFERRED_ROUTE_RATIO = 0.80;

    private final RouteSimilarityService routeSimilarityService;

    public RouteCandidateSelectionService(RouteSimilarityService routeSimilarityService) {
        this.routeSimilarityService = routeSimilarityService;
    }

    public GeneratedRouteCandidate candidate(FlightRoute route, double estimatedTimeMinutes, double availableTimeMinutes) {
        return new GeneratedRouteCandidate(route, estimatedTimeMinutes, bandFor(estimatedTimeMinutes, availableTimeMinutes));
    }

    public List<GeneratedRouteCandidate> limitCandidates(
            List<GeneratedRouteCandidate> candidates,
            String preference,
            double availableTimeMinutes
    ) {
        if (preference == null || preference.isBlank()) {
            return balancedCandidates(candidates, preference, availableTimeMinutes);
        }

        List<GeneratedRouteCandidate> preferredRoutes = candidates.stream()
                .filter(candidate -> routeMatchesPreference(candidate.route(), preference))
                .toList();
        List<GeneratedRouteCandidate> alternativeRoutes = candidates.stream()
                .filter(candidate -> !routeMatchesPreference(candidate.route(), preference))
                .toList();
        int preferredTarget = (int) Math.round(MAX_GENERATED_ROUTES * PREFERRED_ROUTE_RATIO);
        int alternativeTarget = MAX_GENERATED_ROUTES - preferredTarget;

        List<GeneratedRouteCandidate> selectedRoutes = new ArrayList<>();
        addRoutes(selectedRoutes, balancedCandidates(preferredRoutes, preference, availableTimeMinutes), preferredTarget);
        addRoutes(selectedRoutes, balancedCandidates(alternativeRoutes, preference, availableTimeMinutes), alternativeTarget);
        addRoutes(selectedRoutes, balancedCandidates(preferredRoutes, preference, availableTimeMinutes), MAX_GENERATED_ROUTES - selectedRoutes.size());
        addRoutes(selectedRoutes, balancedCandidates(alternativeRoutes, preference, availableTimeMinutes), MAX_GENERATED_ROUTES - selectedRoutes.size());

        return ensureRouteTypeVariety(selectedRoutes, candidates, preference, availableTimeMinutes);
    }

    public List<GeneratedRouteCandidate> notSelected(
            List<GeneratedRouteCandidate> candidates,
            List<GeneratedRouteCandidate> selectedRoutes
    ) {
        return candidates.stream()
                .filter(candidate -> selectedRoutes.stream().noneMatch(selected -> selected.route().id().equals(candidate.route().id())))
                .toList();
    }

    public boolean isTooSimilarToSelected(
            GeneratedRouteCandidate candidate,
            List<GeneratedRouteCandidate> selectedRoutes
    ) {
        return selectedRoutes.stream()
                .anyMatch(selectedRoute -> routeSimilarityService.areTooSimilar(candidate.route(), selectedRoute.route()));
    }

    private List<GeneratedRouteCandidate> balancedCandidates(
            List<GeneratedRouteCandidate> candidates,
            String preference,
            double availableTimeMinutes
    ) {
        List<GeneratedRouteCandidate> selectedRoutes = new ArrayList<>();
        int longTarget = Math.max(1, (int) Math.round(MAX_GENERATED_ROUTES * 0.40));
        int mediumTarget = Math.max(1, (int) Math.round(MAX_GENERATED_ROUTES * 0.25));
        int extendedTarget = Math.max(1, (int) Math.round(MAX_GENERATED_ROUTES * 0.20));
        int shortTarget = MAX_GENERATED_ROUTES - longTarget - mediumTarget - extendedTarget;

        addBandRoutes(selectedRoutes, candidates, RouteDurationBand.LONG, longTarget, preference, availableTimeMinutes);
        addBandRoutes(selectedRoutes, candidates, RouteDurationBand.MEDIUM, mediumTarget, preference, availableTimeMinutes);
        addBandRoutes(selectedRoutes, candidates, RouteDurationBand.EXTENDED, extendedTarget, preference, availableTimeMinutes);
        addBandRoutes(selectedRoutes, candidates, RouteDurationBand.SHORT, shortTarget, preference, availableTimeMinutes);
        addRoutes(
                selectedRoutes,
                sortedCandidates(candidates, preference, availableTimeMinutes),
                MAX_GENERATED_ROUTES - selectedRoutes.size()
        );

        return ensureRouteTypeVariety(selectedRoutes, candidates, preference, availableTimeMinutes);
    }

    private List<GeneratedRouteCandidate> ensureRouteTypeVariety(
            List<GeneratedRouteCandidate> selectedRoutes,
            List<GeneratedRouteCandidate> candidates,
            String preference,
            double availableTimeMinutes
    ) {
        List<GeneratedRouteCandidate> variedRoutes = new ArrayList<>(selectedRoutes);
        ensureBand(variedRoutes, candidates, RouteDurationBand.LONG, preference, availableTimeMinutes);
        ensureBand(variedRoutes, candidates, RouteDurationBand.MEDIUM, preference, availableTimeMinutes);
        ensureBand(variedRoutes, candidates, RouteDurationBand.EXTENDED, preference, availableTimeMinutes);
        ensureBand(variedRoutes, candidates, RouteDurationBand.SHORT, preference, availableTimeMinutes);
        ensureRouteType(variedRoutes, candidates, RouteType.GENERATED_ONE_WAYPOINT, preference, availableTimeMinutes);
        ensureRouteType(variedRoutes, candidates, RouteType.GENERATED_TWO_WAYPOINTS, preference, availableTimeMinutes);
        ensureRouteType(variedRoutes, candidates, RouteType.GENERATED_THREE_OR_MORE_WAYPOINTS, preference, availableTimeMinutes);

        return variedRoutes;
    }

    private void ensureBand(
            List<GeneratedRouteCandidate> selectedRoutes,
            List<GeneratedRouteCandidate> candidates,
            RouteDurationBand band,
            String preference,
            double availableTimeMinutes
    ) {
        boolean alreadySelected = selectedRoutes.stream()
                .anyMatch(candidate -> candidate.band() == band);
        if (alreadySelected) {
            return;
        }

        GeneratedRouteCandidate replacement = sortedCandidates(candidates, preference, availableTimeMinutes).stream()
                .filter(candidate -> candidate.band() == band)
                .findFirst()
                .orElse(null);
        if (replacement == null) {
            return;
        }

        addOrReplaceLast(selectedRoutes, replacement);
    }

    private void ensureRouteType(
            List<GeneratedRouteCandidate> selectedRoutes,
            List<GeneratedRouteCandidate> candidates,
            RouteType routeType,
            String preference,
            double availableTimeMinutes
    ) {
        boolean alreadySelected = selectedRoutes.stream()
                .anyMatch(candidate -> candidate.route().routeType() == routeType);
        if (alreadySelected) {
            return;
        }

        GeneratedRouteCandidate replacement = sortedCandidates(candidates, preference, availableTimeMinutes).stream()
                .filter(candidate -> candidate.route().routeType() == routeType)
                .findFirst()
                .orElse(null);
        if (replacement == null) {
            return;
        }

        addOrReplaceLast(selectedRoutes, replacement);
    }

    private void addOrReplaceLast(List<GeneratedRouteCandidate> selectedRoutes, GeneratedRouteCandidate replacement) {
        if (selectedRoutes.stream().anyMatch(selected -> selected.route().id().equals(replacement.route().id()))) {
            return;
        }

        if (isTooSimilarToSelected(replacement, selectedRoutes)) {
            return;
        }

        if (selectedRoutes.size() < MAX_GENERATED_ROUTES) {
            selectedRoutes.add(replacement);
            return;
        }
        selectedRoutes.removeLast();
        selectedRoutes.add(replacement);
    }

    private void addBandRoutes(
            List<GeneratedRouteCandidate> selectedRoutes,
            List<GeneratedRouteCandidate> candidates,
            RouteDurationBand band,
            int limit,
            String preference,
            double availableTimeMinutes
    ) {
        addRoutes(
                selectedRoutes,
                sortedCandidates(candidates, preference, availableTimeMinutes).stream()
                        .filter(candidate -> candidate.band() == band)
                        .toList(),
                limit
        );
    }

    private void addRoutes(List<GeneratedRouteCandidate> selectedRoutes, List<GeneratedRouteCandidate> candidates, int limit) {
        if (limit <= 0) {
            return;
        }

        candidates.stream()
                .filter(candidate -> selectedRoutes.stream().noneMatch(selected -> selected.route().id().equals(candidate.route().id())))
                .filter(candidate -> !isTooSimilarToSelected(candidate, selectedRoutes))
                .limit(limit)
                .forEach(selectedRoutes::add);
    }

    private RouteDurationBand bandFor(double estimatedTimeMinutes, double availableTimeMinutes) {
        if (availableTimeMinutes <= 0.0) {
            return RouteDurationBand.TOO_SHORT;
        }

        double usageRatio = estimatedTimeMinutes / availableTimeMinutes;

        if (usageRatio >= 0.3 && usageRatio < 0.5) {
            return RouteDurationBand.SHORT;
        }

        if (usageRatio >= 0.5 && usageRatio < 0.75) {
            return RouteDurationBand.MEDIUM;
        }

        if (usageRatio >= 0.75 && usageRatio <= 1.0) {
            return RouteDurationBand.LONG;
        }

        if (usageRatio > 1.0 && usageRatio <= MAX_ALLOWED_TIME_OVERRUN_RATIO) {
            return RouteDurationBand.EXTENDED;
        }

        return RouteDurationBand.TOO_SHORT;
    }

    private List<GeneratedRouteCandidate> sortedCandidates(
            List<GeneratedRouteCandidate> candidates,
            String preference,
            double availableTimeMinutes
    ) {
        return candidates.stream()
                .sorted(routeComparator(preference, availableTimeMinutes))
                .toList();
    }

    private Comparator<GeneratedRouteCandidate> routeComparator(String preference, double availableTimeMinutes) {
        return Comparator
                .comparingInt((GeneratedRouteCandidate candidate) -> routeExperiencePriority(candidate.route(), preference))
                .thenComparing(candidate -> candidate.band().priority())
                .thenComparingDouble(candidate -> timeFitDistance(candidate, availableTimeMinutes))
                .thenComparing(Comparator.comparing(
                        (GeneratedRouteCandidate candidate) -> routeMatchesPreference(candidate.route(), preference)
                ).reversed())
                .thenComparing(candidate -> candidate.route().scenicScore(), Comparator.reverseOrder())
                .thenComparing(candidate -> candidate.route().waypoints().size());
    }

    private int routeExperiencePriority(FlightRoute route, String preference) {
        if (!isInterIslandRoute(route) || isInterIslandPreference(preference)) {
            return 0;
        }

        return 1;
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

    private double timeFitDistance(GeneratedRouteCandidate candidate, double availableTimeMinutes) {
        if (availableTimeMinutes <= 0.0) {
            return Double.MAX_VALUE;
        }

        return Math.abs(candidate.estimatedTimeMinutes() / availableTimeMinutes - TARGET_DURATION_RATIO);
    }
}
