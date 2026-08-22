package flightdiscovery.paull.domain.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import flightdiscovery.paull.domain.model.Airport;
import flightdiscovery.paull.domain.model.FlightRoute;
import flightdiscovery.paull.domain.model.RouteScore;

class RouteScoringServiceTest {

    private final RouteScoringService scoringService = new RouteScoringService();

    @Test
    void calculatesTotalScoreWithConfiguredWeights() {
        FlightRoute route = routeWithScenicScoreAndTags(8.0, List.of("coast"));

        RouteScore score = scoringService.score(route, 90.0, 120, 100.0, "coast");

        assertEquals(80.0, score.weatherScore(), 0.01);
        assertEquals(86.5, score.timeFitScore(), 0.01);
        assertEquals(100.0, score.preferenceScore(), 0.01);
        assertEquals(80.0, score.scenicScore(), 0.01);
        assertEquals(60.0, score.costScore(), 0.01);
        assertEquals(84.95, score.totalScore(), 0.01);
    }

    @Test
    void givesStrongTimeFitWhenRouteUsesMostAvailableTime() {
        double timeFitScore = scoringService.timeFitScore(110.0, 120);

        assertEquals(70.0, timeFitScore, 0.01);
    }

    @Test
    void penalizesRoutesThatExceedAvailableTime() {
        double timeFitScore = scoringService.timeFitScore(150.0, 120);

        assertEquals(15.0, timeFitScore, 0.01);
    }

    @Test
    void penalizesHigherEstimatedCosts() {
        assertEquals(100.0, scoringService.costScore(0.0), 0.01);
        assertEquals(80.0, scoringService.costScore(50.0), 0.01);
        assertEquals(40.0, scoringService.costScore(150.0), 0.01);
        assertEquals(0.0, scoringService.costScore(250.0), 0.01);
        assertEquals(0.0, scoringService.costScore(500.0), 0.01);
    }

    @Test
    void normalizesScenicScoreFromMockScaleToOneHundred() {
        FlightRoute route = routeWithScenicScoreAndTags(8.7, List.of("panoramic"));

        assertEquals(87.0, scoringService.scenicScore(route), 0.01);
    }

    @Test
    void boostsRoutesThatMatchUserPreference() {
        FlightRoute matchingRoute = routeWithScenicScoreAndTags(8.0, List.of("coast", "short"));
        FlightRoute nonMatchingRoute = routeWithScenicScoreAndTags(8.0, List.of("mountain"));

        assertEquals(100.0, scoringService.preferenceScore(matchingRoute, "coast"), 0.01);
        assertEquals(45.0, scoringService.preferenceScore(nonMatchingRoute, "coast"), 0.01);
    }

    @Test
    void totalScoreAlwaysStaysBetweenZeroAndOneHundred() {
        FlightRoute route = routeWithScenicScoreAndTags(20.0, List.of("coast"));

        RouteScore cheapRouteScore = scoringService.score(route, 72.0, 120, -10.0, "coast");
        RouteScore expensiveRouteScore = scoringService.score(route, 250.0, 120, 1000.0, "unknown");

        assertScoreInRange(cheapRouteScore.totalScore());
        assertScoreInRange(expensiveRouteScore.totalScore());
    }

    private FlightRoute routeWithScenicScoreAndTags(double scenicScore, List<String> tags) {
        return new FlightRoute(
                "test-route",
                "Test route",
                "Test route",
                new Airport("TEST", "Test Airport", 0.0, 0.0),
                List.of(),
                tags,
                0.0,
                0.0,
                scenicScore
        );
    }

    private void assertScoreInRange(double score) {
        assertTrue(score >= 0.0);
        assertTrue(score <= 100.0);
    }
}
