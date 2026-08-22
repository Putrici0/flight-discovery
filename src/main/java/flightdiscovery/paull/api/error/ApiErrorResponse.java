package flightdiscovery.paull.api.error;

import java.util.List;

public record ApiErrorResponse(
        String message,
        List<String> errors
) {
}

