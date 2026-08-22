package flightdiscovery.paull.domain.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import flightdiscovery.paull.domain.model.Airport;
import flightdiscovery.paull.domain.model.FlightRoute;
import flightdiscovery.paull.domain.model.RouteScore;
import flightdiscovery.paull.domain.model.RouteType;

class RouteScoringServiceTest {

    private final RouteScoringService scoringService = new RouteScoringService();

    @Test
    void calculatesTotalScoreWithConfiguredWeights() {
        FlightRoute route = routeWithScenicScoreAndTags(8.0, List.of("coast"));

        RouteScore score = scoringService.score(route, 90.0, 120, 100.0, "coast", 80.0);

        assertEquals(80.0, score.weatherScore(), 0.01);
        assertEquals(90.0, score.timeFitScore(), 0.01);
        assertEquals(100.0, score.preferenceScore(), 0.01);
        assertEquals(80.0, score.scenicScore(), 0.01);
        assertEquals(60.0, score.costScore(), 0.01);
        assertEquals(84.5, score.totalScore(), 0.01);
    }

    @Test
    void givesHighestTimeFitNearTargetDuration() {
        double targetTimeFitScore = scoringService.timeFitScore(102.0, 120);
        double shorterHighTimeFitScore = scoringService.timeFitScore(84.0, 120);
        double longerHighTimeFitScore = scoringService.timeFitScore(120.0, 120);

        assertEquals(100.0, targetTimeFitScore, 0.01);
        assertTrue(targetTimeFitScore > shorterHighTimeFitScore);
        assertTrue(targetTimeFitScore > longerHighTimeFitScore);
    }

    @Test
    void givesStrongTimeFitWhenRouteUsesMostAvailableTime() {
        double timeFitScore = scoringService.timeFitScore(110.0, 120);

        assertEquals(93.33, timeFitScore, 0.01);
    }

    @Test
    void givesMediumTimeFitWhenRouteUsesHalfToSeventyPercentOfAvailableTime() {
        assertEquals(47.5, scoringService.timeFitScore(60.0, 120), 0.01);
        assertEquals(55.0, scoringService.timeFitScore(72.0, 120), 0.01);
        assertEquals(62.5, scoringService.timeFitScore(78.0, 120), 0.01);
    }

    @Test
    void penalizesNonShortRoutesHeavilyWhenTheyUseLessThanFortyPercentOfAvailableTime() {
        double timeFitScore = scoringService.timeFitScore(36.0, 120, "coast");

        assertEquals(22.5, timeFitScore, 0.01);
    }

    @Test
    void penalizesNonShortRoutesModeratelyWhenTheyUseLessThanSixtyPercentOfAvailableTime() {
        double timeFitScore = scoringService.timeFitScore(48.0, 120, "coast");

        assertEquals(40.0, timeFitScore, 0.01);
    }

    @Test
    void allowsShortPreferenceToScoreVeryShortRoutesBetter() {
        double shortPreferenceScore = scoringService.timeFitScore(48.0, 120, "short");
        double defaultPreferenceScore = scoringService.timeFitScore(48.0, 120, "coast");

        assertEquals(85.0, shortPreferenceScore, 0.01);
        assertTrue(shortPreferenceScore > defaultPreferenceScore);
    }

    @Test
    void penalizesRoutesThatExceedAvailableTime() {
        double timeFitScore = scoringService.timeFitScore(150.0, 120);

        assertEquals(15.0, timeFitScore, 0.01);
    }

    @Test
    void discardsTimeFitWhenRouteExceedsAvailableTimeByMoreThanTwentyFivePercent() {
        double timeFitScore = scoringService.timeFitScore(151.0, 120);

        assertEquals(0.0, timeFitScore, 0.01);
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
        assertEquals(35.0, scoringService.preferenceScore(nonMatchingRoute, "coast"), 0.01);
    }

    @Test
    void givesMediumPreferenceScoreForPartialMatches() {
        FlightRoute route = routeWithScenicScoreAndTags(8.0, List.of("coastal", "short"));

        assertEquals(70.0, scoringService.preferenceScore(route, "coast"), 0.01);
        assertEquals(70.0, scoringService.preferenceScore(route, "coastal route"), 0.01);
    }

    @Test
    void givesNeutralPreferenceScoreWhenPreferenceIsBlankOrAny() {
        FlightRoute route = routeWithScenicScoreAndTags(8.0, List.of("coast"));

        assertEquals(60.0, scoringService.preferenceScore(route, null), 0.01);
        assertEquals(60.0, scoringService.preferenceScore(route, ""), 0.01);
        assertEquals(60.0, scoringService.preferenceScore(route, "any"), 0.01);
    }

    @Test
    void preferenceScoreAlwaysStaysBetweenZeroAndOneHundred() {
        FlightRoute route = routeWithScenicScoreAndTags(8.0, List.of("coast"));

        assertScoreInRange(scoringService.preferenceScore(route, "coast"));
        assertScoreInRange(scoringService.preferenceScore(route, "coastal route"));
        assertScoreInRange(scoringService.preferenceScore(route, "mountain"));
        assertScoreInRange(scoringService.preferenceScore(route, "any"));
    }

    @Test
    void totalScoreAlwaysStaysBetweenZeroAndOneHundred() {
        FlightRoute route = routeWithScenicScoreAndTags(20.0, List.of("coast"));

        RouteScore cheapRouteScore = scoringService.score(route, 72.0, 120, -10.0, "coast", 140.0);
        RouteScore expensiveRouteScore = scoringService.score(route, 250.0, 120, 1000.0, "unknown", -10.0);

        assertScoreInRange(cheapRouteScore.totalScore());
        assertScoreInRange(expensiveRouteScore.totalScore());
    }

    private FlightRoute routeWithScenicScoreAndTags(double scenicScore, List<String> tags) {
        return new FlightRoute(
                "test-route",
                "Test route",
                "Test route",
                RouteType.PREDEFINED,
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
