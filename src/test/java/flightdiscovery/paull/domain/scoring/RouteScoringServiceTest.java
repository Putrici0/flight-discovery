package flightdiscovery.paull.domain.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import flightdiscovery.paull.domain.model.Airport;
import flightdiscovery.paull.domain.model.FlightRoute;
import flightdiscovery.paull.domain.model.RouteScore;

class RouteScoringServiceTest {

    private final RouteScoringService scoringService = new RouteScoringService();

    @Test
    void calculatesTotalScoreWithConfiguredWeights() {
        FlightRoute route = routeWithScenicScore(8.0);

        RouteScore score = scoringService.score(route, 90.0, 120, 100.0);

        assertEquals(80.0, score.weatherScore(), 0.01);
        assertEquals(86.5, score.timeFitScore(), 0.01);
        assertEquals(80.0, score.scenicScore(), 0.01);
        assertEquals(92.14, score.costScore(), 0.01);
        assertEquals(83.77, score.totalScore(), 0.01);
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
        assertEquals(100.0, scoringService.costScore(80.0), 0.01);
        assertEquals(80.36, scoringService.costScore(130.0), 0.01);
        assertEquals(60.71, scoringService.costScore(180.0), 0.01);
        assertEquals(45.0, scoringService.costScore(220.0), 0.01);
    }

    @Test
    void normalizesScenicScoreFromMockScaleToOneHundred() {
        FlightRoute route = routeWithScenicScore(8.7);

        assertEquals(87.0, scoringService.scenicScore(route), 0.01);
    }

    private FlightRoute routeWithScenicScore(double scenicScore) {
        return new FlightRoute(
                "test-route",
                "Test route",
                "Test route",
                new Airport("TEST", "Test Airport", 0.0, 0.0),
                List.of(),
                0.0,
                0.0,
                scenicScore
        );
    }
}
