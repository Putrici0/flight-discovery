package flightdiscovery.paull.domain.mock;

import java.util.List;

import flightdiscovery.paull.domain.model.Aircraft;
import flightdiscovery.paull.domain.model.Airport;
import flightdiscovery.paull.domain.model.FlightRoute;
import flightdiscovery.paull.domain.model.RouteType;
import flightdiscovery.paull.domain.model.VisualWaypoint;
import flightdiscovery.paull.domain.model.Waypoint;

public final class MockFlightData {

    public static final Airport GCLP = new Airport(
            "GCLP",
            "Gran Canaria",
            27.9319,
            -15.3866
    );

    public static final Airport GCTS = new Airport(
            "GCTS",
            "Tenerife Sur",
            28.0445,
            -16.5725
    );

    public static final Airport GCXO = new Airport(
            "GCXO",
            "Tenerife Norte",
            28.4827,
            -16.3415
    );

    private static final List<Airport> AIRPORTS = List.of(GCLP, GCTS, GCXO);

    private static final List<Aircraft> AIRCRAFT = List.of(
            new Aircraft("cessna-172", "Cessna 172", 226.0, 34.0, 151.0),
            new Aircraft("piper-pa-28", "Piper PA-28", 215.0, 36.0, 182.0),
            new Aircraft("diamond-da40", "Diamond DA40", 235.0, 28.0, 151.0)
    );

    private static final List<VisualWaypoint> VISUAL_WAYPOINTS = List.of(
            new VisualWaypoint(
                    "gc-maspalomas-dunes",
                    "Dunas de Maspalomas",
                    27.7406,
                    -15.5846,
                    List.of("coast", "island", "panoramic", "short"),
                    9.0
            ),
            new VisualWaypoint(
                    "gc-roque-nublo",
                    "Roque Nublo",
                    27.9676,
                    -15.6007,
                    List.of("mountain", "island", "panoramic"),
                    9.4
            ),
            new VisualWaypoint(
                    "gc-pico-de-las-nieves",
                    "Pico de las Nieves",
                    27.9621,
                    -15.5715,
                    List.of("mountain", "panoramic", "island"),
                    9.1
            ),
            new VisualWaypoint(
                    "gc-puerto-de-mogan",
                    "Puerto de Mogan",
                    27.8170,
                    -15.7650,
                    List.of("coast", "island", "short"),
                    8.3
            ),
            new VisualWaypoint(
                    "gc-agaete-cliffs",
                    "Acantilados de Agaete",
                    28.1005,
                    -15.7104,
                    List.of("coast", "panoramic", "island"),
                    8.7
            ),
            new VisualWaypoint(
                    "tf-teide",
                    "Teide",
                    28.2724,
                    -16.6425,
                    List.of("volcano", "mountain", "panoramic", "island"),
                    9.8
            ),
            new VisualWaypoint(
                    "tf-anaga",
                    "Macizo de Anaga",
                    28.5447,
                    -16.2009,
                    List.of("mountain", "coast", "panoramic", "island"),
                    9.2
            ),
            new VisualWaypoint(
                    "tf-los-gigantes",
                    "Acantilados de Los Gigantes",
                    28.2437,
                    -16.8392,
                    List.of("coast", "panoramic", "island"),
                    9.0
            ),
            new VisualWaypoint(
                    "tf-costa-adeje",
                    "Costa Adeje",
                    28.0866,
                    -16.7350,
                    List.of("coast", "short", "island"),
                    8.0
            ),
            new VisualWaypoint(
                    "tf-la-orotava",
                    "Valle de La Orotava",
                    28.3892,
                    -16.5239,
                    List.of("mountain", "panoramic", "island"),
                    8.6
            )
    );

    private static final List<FlightRoute> ROUTES = List.of(
            new FlightRoute(
                    "gclp-coastal-south",
                    "Costa sur de Gran Canaria",
                    "Ruta costera recreativa desde GCLP siguiendo el litoral sur de la isla.",
                    RouteType.PREDEFINED,
                    GCLP,
                    List.of(
                            new Waypoint("Maspalomas", 27.7606, -15.5860),
                            new Waypoint("Puerto Rico", 27.7894, -15.7104),
                            new Waypoint("Arguineguin", 27.7589, -15.6816)
                    ),
                    List.of("coast", "short", "panoramic"),
                    145.0,
                    45.0,
                    8.2
            ),
            new FlightRoute(
                    "gclp-panoramic-central",
                    "Panoramica central de Gran Canaria",
                    "Ruta panoramica hacia el interior para sobrevolar referencias visuales del centro de la isla.",
                    RouteType.PREDEFINED,
                    GCLP,
                    List.of(
                            new Waypoint("Telde", 27.9955, -15.4174),
                            new Waypoint("Roque Nublo", 27.9676, -15.6007),
                            new Waypoint("Tejeda", 27.9956, -15.6156)
                    ),
                    List.of("mountain", "panoramic", "short"),
                    120.0,
                    38.0,
                    8.8
            ),
            new FlightRoute(
                    "gclp-tenerife-island-hop",
                    "Salto entre islas a Tenerife",
                    "Ruta entre islas desde GCLP hacia Tenerife con puntos visuales de aproximacion a la isla.",
                    RouteType.PREDEFINED,
                    GCLP,
                    List.of(
                            new Waypoint("Punta de Sardina", 28.1640, -15.7100),
                            new Waypoint("Canal Gran Canaria Tenerife", 28.1800, -16.0000),
                            new Waypoint("Costa de Guimar", 28.2920, -16.3730)
                    ),
                    List.of("islands", "coast", "panoramic"),
                    225.0,
                    68.0,
                    9.0
            ),
            new FlightRoute(
                    "gcts-south-coast",
                    "Costa sur de Tenerife",
                    "Ruta recreativa desde Tenerife Sur por la costa meridional.",
                    RouteType.PREDEFINED,
                    GCTS,
                    List.of(
                            new Waypoint("Los Cristianos", 28.0516, -16.7206),
                            new Waypoint("Costa Adeje", 28.0866, -16.7350),
                            new Waypoint("Las Galletas", 28.0080, -16.6530)
                    ),
                    List.of("coast", "short"),
                    115.0,
                    35.0,
                    8.0
            ),
            new FlightRoute(
                    "gcxo-north-scenic",
                    "Norte panoramico de Tenerife",
                    "Ruta desde Tenerife Norte para disfrutar de referencias visuales del norte de la isla.",
                    RouteType.PREDEFINED,
                    GCXO,
                    List.of(
                            new Waypoint("La Orotava", 28.3892, -16.5239),
                            new Waypoint("Puerto de la Cruz", 28.4133, -16.5482),
                            new Waypoint("Anaga", 28.5447, -16.2009)
                    ),
                    List.of("mountain", "panoramic", "coast"),
                    130.0,
                    40.0,
                    8.7
            )
    );

    private MockFlightData() {
    }

    public static List<Airport> airports() {
        return AIRPORTS;
    }

    public static List<Aircraft> aircraft() {
        return AIRCRAFT;
    }

    public static List<FlightRoute> routes() {
        return ROUTES;
    }

    public static List<VisualWaypoint> visualWaypoints() {
        return VISUAL_WAYPOINTS;
    }
}
