package flightdiscovery.paull.domain.model;

public record Airport(
        String code,
        String name,
        double latitude,
        double longitude
) {
}
