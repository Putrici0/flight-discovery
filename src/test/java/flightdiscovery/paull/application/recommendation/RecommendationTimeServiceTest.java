package flightdiscovery.paull.application.recommendation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import flightdiscovery.paull.api.recommendation.RouteDurationCategory;
import flightdiscovery.paull.domain.model.Aircraft;

class RecommendationTimeServiceTest {

    private final RecommendationTimeService service = new RecommendationTimeService();

    @Test
    void usefulAvailableTimeSubtractsReserveAndAppliesSafetyMargin() {
        Aircraft aircraft = new Aircraft("test", "Test", 200.0, 30.0, "AVGAS_100LL", 4.0, 45);

        double usefulTime = service.usefulAvailableTimeMinutes(165, aircraft, 20);

        assertEquals(96.0, usefulTime, 0.01);
    }

    @Test
    void routeDurationCategoryKeepsExistingBoundaries() {
        assertEquals(RouteDurationCategory.TOO_SHORT, service.routeDurationCategory(39.0, 100.0));
        assertEquals(RouteDurationCategory.SHORT, service.routeDurationCategory(40.0, 100.0));
        assertEquals(RouteDurationCategory.GOOD_FIT, service.routeDurationCategory(70.0, 100.0));
        assertEquals(RouteDurationCategory.LONG, service.routeDurationCategory(90.0, 100.0));
        assertEquals(RouteDurationCategory.SLIGHTLY_OVER_TIME, service.routeDurationCategory(125.0, 100.0));
        assertEquals(RouteDurationCategory.TOO_LONG, service.routeDurationCategory(126.0, 100.0));
    }

    @Test
    void allowedTimeLimitPreservesTwentyFivePercentTolerance() {
        assertEquals(125.0, service.allowedTimeLimitMinutes(100.0), 0.01);
        assertTrue(service.allowedTimeLimitMinutes(0.0) == 0.0);
    }
}
