package flightdiscovery.paull.application.recommendation;

enum RouteDurationBand {
    LONG(0),
    MEDIUM(1),
    EXTENDED(2),
    SHORT(3),
    TOO_SHORT(4);

    private final int priority;

    RouteDurationBand(int priority) {
        this.priority = priority;
    }

    int priority() {
        return priority;
    }
}
