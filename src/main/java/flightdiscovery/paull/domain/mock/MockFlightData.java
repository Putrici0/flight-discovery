package flightdiscovery.paull.domain.mock;

import java.util.List;

import flightdiscovery.paull.domain.model.Aircraft;
import flightdiscovery.paull.domain.model.Airport;
import flightdiscovery.paull.domain.model.FlightRoute;
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

    private static final List<FlightRoute> ROUTES = List.of(
            new FlightRoute(
                    "gclp-coastal-south",
                    "Costa sur de Gran Canaria",
                    "Ruta costera recreativa desde GCLP siguiendo el litoral sur de la isla.",
                    GCLP,
                    List.of(
                            new Waypoint("Maspalomas", 27.7606, -15.5860),
                            new Waypoint("Puerto Rico", 27.7894, -15.7104),
                            new Waypoint("Arguineguin", 27.7589, -15.6816)
                    ),
                    145.0,
                    45.0,
                    8.2
            ),
            new FlightRoute(
                    "gclp-panoramic-central",
                    "Panoramica central de Gran Canaria",
                    "Ruta panoramica hacia el interior para sobrevolar referencias visuales del centro de la isla.",
                    GCLP,
                    List.of(
                            new Waypoint("Telde", 27.9955, -15.4174),
                            new Waypoint("Roque Nublo", 27.9676, -15.6007),
                            new Waypoint("Tejeda", 27.9956, -15.6156)
                    ),
                    120.0,
                    38.0,
                    8.8
            ),
            new FlightRoute(
                    "gclp-tenerife-island-hop",
                    "Salto entre islas a Tenerife",
                    "Ruta entre islas desde GCLP hacia Tenerife con puntos visuales de aproximacion a la isla.",
                    GCLP,
                    List.of(
                            new Waypoint("Punta de Sardina", 28.1640, -15.7100),
                            new Waypoint("Canal Gran Canaria Tenerife", 28.1800, -16.0000),
                            new Waypoint("Costa de Guimar", 28.2920, -16.3730)
                    ),
                    225.0,
                    68.0,
                    9.0
            ),
            new FlightRoute(
                    "gcts-south-coast",
                    "Costa sur de Tenerife",
                    "Ruta recreativa desde Tenerife Sur por la costa meridional.",
                    GCTS,
                    List.of(
                            new Waypoint("Los Cristianos", 28.0516, -16.7206),
                            new Waypoint("Costa Adeje", 28.0866, -16.7350),
                            new Waypoint("Las Galletas", 28.0080, -16.6530)
                    ),
                    115.0,
                    35.0,
                    8.0
            ),
            new FlightRoute(
                    "gcxo-north-scenic",
                    "Norte panoramico de Tenerife",
                    "Ruta desde Tenerife Norte para disfrutar de referencias visuales del norte de la isla.",
                    GCXO,
                    List.of(
                            new Waypoint("La Orotava", 28.3892, -16.5239),
                            new Waypoint("Puerto de la Cruz", 28.4133, -16.5482),
                            new Waypoint("Anaga", 28.5447, -16.2009)
                    ),
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
}
