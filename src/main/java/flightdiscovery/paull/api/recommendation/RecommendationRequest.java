package flightdiscovery.paull.api.recommendation;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record RecommendationRequest(
        @NotBlank(message = "departureAirport is required")
        String departureAirport,

        @Min(value = 1, message = "availableFlightTimeMinutes must be greater than 0")
        int availableFlightTimeMinutes,

        String aircraftId,

        @DecimalMin(value = "0.0", inclusive = false, message = "cruiseSpeedKmh must be greater than 0")
        double cruiseSpeedKmh,

        @DecimalMin(value = "0.0", inclusive = false, message = "fuelBurnLitersPerHour must be greater than 0")
        double fuelBurnLitersPerHour,

        @DecimalMin(value = "0.0", message = "fuelPricePerLiter must be greater than or equal to 0")
        double fuelPricePerLiter,

        @NotBlank(message = "preference is required")
        String preference
) {
}
