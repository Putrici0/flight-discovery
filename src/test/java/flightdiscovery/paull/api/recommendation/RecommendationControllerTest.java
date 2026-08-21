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
                .andExpect(jsonPath("$.recommendations.length()", lessThanOrEqualTo(3)))
                .andExpect(jsonPath("$.recommendations[0].name").exists())
                .andExpect(jsonPath("$.recommendations[0].description").exists())
                .andExpect(jsonPath("$.recommendations[0].waypoints").isArray())
                .andExpect(jsonPath("$.recommendations[0].approximateDistanceKm").isNumber())
                .andExpect(jsonPath("$.recommendations[0].estimatedTimeMinutes").isNumber())
                .andExpect(jsonPath("$.recommendations[0].estimatedFuelLiters").isNumber())
                .andExpect(jsonPath("$.recommendations[0].estimatedCost").isNumber())
                .andExpect(jsonPath("$.recommendations[0].totalScore").isNumber())
                .andExpect(jsonPath("$.recommendations[0].scoreBreakdown.totalScore").isNumber())
                .andExpect(jsonPath("$.recommendations[0].explanation").exists());
    }
}
