package flightdiscovery.paull.api.recommendation;

import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class RecommendationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsRouteRecommendationsFromMockData() throws Exception {
        String requestBody = """
                {
                  "departureAirport": "GCLP",
                  "availableFlightTimeMinutes": 120,
                  "aircraftId": "cessna-172",
                  "cruiseSpeedKmh": 220,
                  "fuelBurnLitersPerHour": 35,
                  "fuelPricePerLiter": 2.3,
                  "preference": "coast"
                }
                """;

        mockMvc.perform(post("/api/recommendations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.recommendations.length()", lessThanOrEqualTo(5)))
                .andExpect(jsonPath("$.recommendations[0].name").exists())
                .andExpect(jsonPath("$.recommendations[0].description").exists())
                .andExpect(jsonPath("$.recommendations[0].routeType").exists())
                .andExpect(jsonPath("$.recommendations[0].waypoints").isArray())
                .andExpect(jsonPath("$.recommendations[0].approximateDistanceKm").isNumber())
                .andExpect(jsonPath("$.recommendations[0].estimatedTimeMinutes").isNumber())
                .andExpect(jsonPath("$.recommendations[0].estimatedTimeHours").isNumber())
                .andExpect(jsonPath("$.recommendations[0].estimatedFuelLiters").isNumber())
                .andExpect(jsonPath("$.recommendations[0].fuelPricePerLiter").isNumber())
                .andExpect(jsonPath("$.recommendations[0].fuelPriceSource").value("MANUAL"))
                .andExpect(jsonPath("$.recommendations[0].estimatedCost").isNumber())
                .andExpect(jsonPath("$.recommendations[0].totalScore").isNumber())
                .andExpect(jsonPath("$.recommendations[0].weatherScore").isNumber())
                .andExpect(jsonPath("$.recommendations[0].windKmh").isNumber())
                .andExpect(jsonPath("$.recommendations[0].cloudCoverPercent").isNumber())
                .andExpect(jsonPath("$.recommendations[0].precipitationProbability").isNumber())
                .andExpect(jsonPath("$.recommendations[0].visibilityKm").isNumber())
                .andExpect(jsonPath("$.recommendations[0].scoreBreakdown.totalScore").isNumber())
                .andExpect(jsonPath("$.recommendations[0].explanation").exists())
                .andExpect(jsonPath("$.recommendations[0].warnings").isArray())
                .andExpect(jsonPath("$.debugInfo.generatedCandidateRoutes").isNumber())
                .andExpect(jsonPath("$.debugInfo.discardedByTimeRoutes").isNumber())
                .andExpect(jsonPath("$.debugInfo.recommendedRoutes").isNumber())
                .andExpect(jsonPath("$.warnings").isArray());
    }

    @Test
    void returnsClearValidationErrorsForInvalidRequest() throws Exception {
        String requestBody = """
                {
                  "departureAirport": "",
                  "availableFlightTimeMinutes": 0,
                  "aircraftId": "cessna-172",
                  "cruiseSpeedKmh": 0,
                  "fuelBurnLitersPerHour": 0,
                  "fuelPricePerLiter": -1,
                  "preference": ""
                }
                """;

        mockMvc.perform(post("/api/recommendations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Invalid recommendation request"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void returnsWarningsWhenTimeFilterLeavesFewerThanThreeRecommendations() throws Exception {
        String requestBody = """
                {
                  "departureAirport": "GCLP",
                  "availableFlightTimeMinutes": 1,
                  "aircraftId": "cessna-172",
                  "cruiseSpeedKmh": 226,
                  "fuelBurnLitersPerHour": 34,
                  "fuelPricePerLiter": 2.3,
                  "preference": "coast"
                }
                """;

        mockMvc.perform(post("/api/recommendations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations.length()", lessThanOrEqualTo(2)))
                .andExpect(jsonPath("$.warnings[0]").exists());
    }

    @Test
    void usesMockFuelPriceWhenFuelPriceIsOmitted() throws Exception {
        String requestBody = """
                {
                  "departureAirport": "GCLP",
                  "availableFlightTimeMinutes": 120,
                  "aircraftId": "cessna-172",
                  "cruiseSpeedKmh": 226,
                  "fuelBurnLitersPerHour": 34,
                  "preference": "coast"
                }
                """;

        mockMvc.perform(post("/api/recommendations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations[0].fuelPricePerLiter").value(2.85))
                .andExpect(jsonPath("$.recommendations[0].fuelPriceSource").value("MOCK"));
    }

    @Test
    void returnsDevelopmentDebugInformation() throws Exception {
        String requestBody = """
                {
                  "departureAirport": "GCLP",
                  "availableFlightTimeMinutes": 120,
                  "aircraftId": "cessna-172",
                  "cruiseSpeedKmh": 226,
                  "fuelBurnLitersPerHour": 34,
                  "fuelPricePerLiter": 2.3,
                  "preference": "coast",
                  "safetyMarginPercent": 15
                }
                """;

        mockMvc.perform(post("/api/recommendations/debug")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request.departureAirport").value("GCLP"))
                .andExpect(jsonPath("$.aircraft.id").value("cessna-172"))
                .andExpect(jsonPath("$.usefulAvailableTimeMinutes").value(63.75))
                .andExpect(jsonPath("$.compatibleWaypoints").isNumber())
                .andExpect(jsonPath("$.generatedCandidateRoutes").isNumber())
                .andExpect(jsonPath("$.discardedRoutes").isNumber())
                .andExpect(jsonPath("$.finalRoutes").isNumber())
                .andExpect(jsonPath("$.candidates[0].routeId").exists())
                .andExpect(jsonPath("$.candidates[0].routeType").exists())
                .andExpect(jsonPath("$.candidates[0].estimatedTimeMinutes").isNumber())
                .andExpect(jsonPath("$.candidates[0].discarded").isBoolean())
                .andExpect(jsonPath("$.candidates[0].totalScore").isNumber())
                .andExpect(jsonPath("$.candidates[0].timeFitScore").isNumber())
                .andExpect(jsonPath("$.candidates[0].costScore").isNumber())
                .andExpect(jsonPath("$.discards[0].routeId").exists())
                .andExpect(jsonPath("$.discards[0].stage").exists())
                .andExpect(jsonPath("$.discards[0].reason").exists())
                .andExpect(jsonPath("$.recommendations").isArray());
    }
}
