package flightdiscovery.paull.api.recommendation;

import java.time.LocalDateTime;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RecommendationRequest(
        @NotBlank(message = "departureAirport is required")
        String departureAirport,

        @Min(value = 1, message = "availableFlightTimeMinutes must be greater than 0")
        int availableFlightTimeMinutes,

        String aircraftId,

        @DecimalMin(value = "0.0", inclusive = false, message = "cruiseSpeedKmh must be greater than 0")
        Double cruiseSpeedKmh,

        @DecimalMin(value = "0.0", inclusive = false, message = "fuelBurnLitersPerHour must be greater than 0")
        Double fuelBurnLitersPerHour,

        @DecimalMin(value = "0.0", message = "fuelPricePerLiter must be greater than or equal to 0")
        Double fuelPricePerLiter,

        @NotBlank(message = "preference is required")
        String preference,

        @Min(value = 0, message = "safetyMarginPercent must be greater than or equal to 0")
        @Max(value = 100, message = "safetyMarginPercent must be less than or equal to 100")
        Integer safetyMarginPercent,

        @Pattern(
                regexp = "^\\d{4}-\\d{2}-\\d{2}T([01]\\d|2[0-3]):[0-5]\\d$",
                message = "plannedDepartureDateTime must use yyyy-MM-dd'T'HH:mm format"
        )
        String plannedDepartureDateTime,

        @Pattern(
                regexp = "^(mock|open-meteo)$",
                message = "weatherProvider must be mock or open-meteo"
        )
        String weatherProvider
) {
    public RecommendationRequest(
            String departureAirport,
            int availableFlightTimeMinutes,
            String aircraftId,
            Double cruiseSpeedKmh,
            Double fuelBurnLitersPerHour,
            Double fuelPricePerLiter,
            String preference,
            Integer safetyMarginPercent
    ) {
        this(
                departureAirport,
                availableFlightTimeMinutes,
                aircraftId,
                cruiseSpeedKmh,
                fuelBurnLitersPerHour,
                fuelPricePerLiter,
                preference,
                safetyMarginPercent,
                null,
                null
        );
    }

    public RecommendationRequest(
            String departureAirport,
            int availableFlightTimeMinutes,
            String aircraftId,
            Double cruiseSpeedKmh,
            Double fuelBurnLitersPerHour,
            Double fuelPricePerLiter,
            String preference,
            Integer safetyMarginPercent,
            String plannedDepartureDateTime
    ) {
        this(
                departureAirport,
                availableFlightTimeMinutes,
                aircraftId,
                cruiseSpeedKmh,
                fuelBurnLitersPerHour,
                fuelPricePerLiter,
                preference,
                safetyMarginPercent,
                plannedDepartureDateTime,
                null
        );
    }

    public int effectiveSafetyMarginPercent() {
        return safetyMarginPercent == null ? 15 : safetyMarginPercent;
    }

    public String effectivePlannedDepartureDateTime() {
        if (plannedDepartureDateTime == null || plannedDepartureDateTime.isBlank()) {
            return LocalDateTime.now().withSecond(0).withNano(0).toString();
        }

        return plannedDepartureDateTime;
    }

    public String requestedWeatherProvider() {
        if (weatherProvider == null || weatherProvider.isBlank()) {
            return null;
        }

        return weatherProvider.trim().toLowerCase();
    }
}
