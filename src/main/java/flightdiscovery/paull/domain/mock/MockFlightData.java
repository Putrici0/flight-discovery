package flightdiscovery.paull.domain.mock;

import java.util.List;

import flightdiscovery.paull.domain.model.Aircraft;
import flightdiscovery.paull.domain.model.Airport;
import flightdiscovery.paull.domain.model.AirportFuelPrice;
import flightdiscovery.paull.domain.model.FlightRoute;
import flightdiscovery.paull.domain.model.FuelPrice;
import flightdiscovery.paull.domain.model.FuelPriceSource;
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
            new Aircraft("cessna-172", "Cessna 172", 226.0, 34.0, "AVGAS_100LL", 4.4, 45),
            new Aircraft("piper-pa-28", "Piper PA-28", 215.0, 36.0, "AVGAS_100LL", 5.0, 45),
            new Aircraft("diamond-da40", "Diamond DA40", 235.0, 28.0, "JET_A1", 5.4, 45)
    );

    private static final List<FuelPrice> FUEL_PRICES = List.of(
            new FuelPrice("AVGAS_100LL", 2.85, FuelPriceSource.MOCK),
            new FuelPrice("JET_A1", 1.95, FuelPriceSource.MOCK),
            new FuelPrice("MOGAS", 1.75, FuelPriceSource.MOCK)
    );

    private static final List<AirportFuelPrice> AIRPORT_FUEL_PRICES = List.of(
            new AirportFuelPrice("GCLP", "AVGAS_100LL", 2.85, "EUR", "MOCK_AIRPORT_FUEL_TABLE", java.time.LocalDate.parse("2026-08-30"), true),
            new AirportFuelPrice("GCLP", "JET_A1", 1.95, "EUR", "MOCK_AIRPORT_FUEL_TABLE", java.time.LocalDate.parse("2026-08-30"), true),
            new AirportFuelPrice("GCLP", "MOGAS", 1.75, "EUR", "MOCK_AIRPORT_FUEL_TABLE", java.time.LocalDate.parse("2026-08-30"), true),
            new AirportFuelPrice("GCTS", "AVGAS_100LL", 2.92, "EUR", "MOCK_AIRPORT_FUEL_TABLE", java.time.LocalDate.parse("2026-08-30"), true),
            new AirportFuelPrice("GCTS", "JET_A1", 2.02, "EUR", "MOCK_AIRPORT_FUEL_TABLE", java.time.LocalDate.parse("2026-08-30"), true),
            new AirportFuelPrice("GCTS", "MOGAS", 1.82, "EUR", "MOCK_AIRPORT_FUEL_TABLE", java.time.LocalDate.parse("2026-08-30"), true),
            new AirportFuelPrice("GCXO", "AVGAS_100LL", 2.88, "EUR", "MOCK_AIRPORT_FUEL_TABLE", java.time.LocalDate.parse("2026-08-30"), true),
            new AirportFuelPrice("GCXO", "JET_A1", 1.98, "EUR", "MOCK_AIRPORT_FUEL_TABLE", java.time.LocalDate.parse("2026-08-30"), true),
            new AirportFuelPrice("GCXO", "MOGAS", 1.79, "EUR", "MOCK_AIRPORT_FUEL_TABLE", java.time.LocalDate.parse("2026-08-30"), true)
    );

    private static final List<VisualWaypoint> VISUAL_WAYPOINTS = List.of(
            new VisualWaypoint("gc-maspalomas-dunes", "Dunas de Maspalomas", 27.7406, -15.5846, List.of("GCLP"), List.of("coast", "island", "panoramic", "short"), 92.0),
            new VisualWaypoint("gc-puerto-de-mogan", "Puerto de Mogan", 27.8170, -15.7650, List.of("GCLP"), List.of("coast", "village", "island", "short"), 86.0),
            new VisualWaypoint("gc-agaete", "Agaete", 28.1005, -15.7004, List.of("GCLP"), List.of("coast", "village", "panoramic", "island"), 88.0),
            new VisualWaypoint("gc-roque-nublo", "Roque Nublo", 27.9676, -15.6007, List.of("GCLP"), List.of("mountain", "volcanic", "island", "panoramic"), 96.0),
            new VisualWaypoint("gc-tejeda", "Tejeda", 27.9956, -15.6156, List.of("GCLP"), List.of("mountain", "village", "panoramic", "island"), 91.0),
            new VisualWaypoint("gc-las-canteras", "Las Canteras", 28.1404, -15.4366, List.of("GCLP"), List.of("coast", "beach", "urban", "island"), 84.0),
            new VisualWaypoint("gc-arucas", "Arucas", 28.1194, -15.5239, List.of("GCLP"), List.of("historic", "village", "urban", "island"), 78.0),
            new VisualWaypoint("gc-guayadeque", "Barranco de Guayadeque", 27.9170, -15.4760, List.of("GCLP"), List.of("ravine", "mountain", "panoramic", "short"), 87.0),
            new VisualWaypoint("gc-pico-de-las-nieves", "Pico de las Nieves", 27.9621, -15.5715, List.of("GCLP"), List.of("mountain", "panoramic", "island"), 94.0),
            new VisualWaypoint("gc-roque-bentayga", "Roque Bentayga", 27.9888, -15.6401, List.of("GCLP"), List.of("mountain", "volcanic", "historic", "panoramic"), 90.0),
            new VisualWaypoint("gc-teror", "Teror", 28.0609, -15.5474, List.of("GCLP"), List.of("historic", "village", "island"), 80.0),
            new VisualWaypoint("gc-vegueta", "Vegueta", 28.1008, -15.4150, List.of("GCLP"), List.of("historic", "urban", "short"), 76.0),
            new VisualWaypoint("gc-risco-caido", "Risco Caido", 28.0500, -15.6850, List.of("GCLP"), List.of("historic", "mountain", "panoramic", "island"), 89.0),
            new VisualWaypoint("gc-fataga", "Fataga", 27.8890, -15.5630, List.of("GCLP"), List.of("mountain", "village", "panoramic", "short"), 82.0),
            new VisualWaypoint("gc-aguimes", "Aguimes", 27.9054, -15.4461, List.of("GCLP"), List.of("historic", "village", "short"), 77.0),
            new VisualWaypoint("gc-galdar-cueva-pintada", "Galdar y Cueva Pintada", 28.1470, -15.6540, List.of("GCLP"), List.of("historic", "urban", "island"), 79.0),
            new VisualWaypoint("gc-firgas", "Firgas", 28.1075, -15.5628, List.of("GCLP"), List.of("village", "mountain", "panoramic"), 75.0),
            new VisualWaypoint("gc-tamadaba", "Pinar de Tamadaba", 28.0610, -15.6950, List.of("GCLP"), List.of("forest", "mountain", "panoramic", "island"), 90.0),
            new VisualWaypoint("gc-sardina-del-norte", "Sardina del Norte", 28.1635, -15.7040, List.of("GCLP"), List.of("coast", "village", "panoramic"), 81.0),
            new VisualWaypoint("gc-playa-de-amadores", "Playa de Amadores", 27.7919, -15.7240, List.of("GCLP"), List.of("coast", "beach", "short"), 82.0),
            new VisualWaypoint("gc-costa-oeste-fuerteventura", "Costa oeste de Fuerteventura", 28.6000, -13.8000, List.of("GCLP"), List.of("coast", "inter-island", "panoramic", "cross-country"), 90.0),
            new VisualWaypoint("gc-isla-de-lobos", "Isla de Lobos", 28.8000, -13.8000, List.of("GCLP"), List.of("coast", "inter-island", "panoramic", "cross-country"), 88.0),
            new VisualWaypoint("gc-canal-oriental-canarias", "Canal oriental de Canarias", 29.0000, -13.8000, List.of("GCLP"), List.of("coast", "inter-island", "panoramic", "cross-country"), 86.0),
            new VisualWaypoint("gc-costa-sur-lanzarote", "Costa sur de Lanzarote", 29.2000, -13.2000, List.of("GCLP"), List.of("coast", "inter-island", "panoramic", "cross-country"), 87.0),
            new VisualWaypoint("gc-punta-papagayo", "Punta de Papagayo", 29.4000, -13.0000, List.of("GCLP"), List.of("coast", "inter-island", "panoramic", "cross-country"), 89.0),
            new VisualWaypoint("gc-canal-lanzarote", "Canal de Lanzarote", 29.8000, -12.8000, List.of("GCLP"), List.of("coast", "inter-island", "panoramic", "cross-country"), 85.0),
            new VisualWaypoint("tf-teide", "Teide", 28.2724, -16.6425, List.of("GCLP", "GCTS", "GCXO"), List.of("volcano", "mountain", "panoramic", "island", "inter-island", "cross-country"), 99.0),
            new VisualWaypoint("tf-los-gigantes", "Acantilados de Los Gigantes", 28.2437, -16.8392, List.of("GCLP", "GCTS", "GCXO"), List.of("coast", "cliffs", "panoramic", "island", "inter-island", "cross-country"), 93.0),
            new VisualWaypoint("tf-anaga", "Macizo de Anaga", 28.5447, -16.2009, List.of("GCLP", "GCTS", "GCXO"), List.of("mountain", "forest", "coast", "panoramic", "island", "inter-island", "cross-country"), 94.0),
            new VisualWaypoint("tf-la-orotava", "La Orotava", 28.3892, -16.5239, List.of("GCTS", "GCXO"), List.of("historic", "mountain", "panoramic", "island"), 86.0),
            new VisualWaypoint("tf-garachico", "Garachico", 28.3737, -16.7637, List.of("GCTS", "GCXO"), List.of("coast", "historic", "village", "island"), 85.0),
            new VisualWaypoint("tf-costa-adeje", "Costa Adeje", 28.0866, -16.7350, List.of("GCLP", "GCTS", "GCXO"), List.of("coast", "beach", "short", "island", "inter-island", "cross-country"), 82.0),
            new VisualWaypoint("tf-la-laguna", "La Laguna", 28.4874, -16.3159, List.of("GCTS", "GCXO"), List.of("historic", "urban", "island"), 83.0),
            new VisualWaypoint("tf-punta-de-teno", "Punta de Teno", 28.3424, -16.9227, List.of("GCLP", "GCTS", "GCXO"), List.of("coast", "cliffs", "panoramic", "island", "inter-island", "cross-country"), 91.0),
            new VisualWaypoint("tf-corona-forestal", "Corona Forestal", 28.3000, -16.5200, List.of("GCTS", "GCXO"), List.of("forest", "mountain", "volcano", "panoramic"), 90.0),
            new VisualWaypoint("tf-masca", "Masca", 28.3042, -16.8405, List.of("GCLP", "GCTS", "GCXO"), List.of("mountain", "village", "ravine", "panoramic", "inter-island", "cross-country"), 92.0),
            new VisualWaypoint("tf-icod-drago", "Icod de los Vinos y Drago Milenario", 28.3667, -16.7227, List.of("GCTS", "GCXO"), List.of("historic", "village", "island"), 80.0),
            new VisualWaypoint("tf-puerto-de-la-cruz", "Puerto de la Cruz", 28.4133, -16.5482, List.of("GCTS", "GCXO"), List.of("coast", "urban", "historic", "island"), 81.0),
            new VisualWaypoint("tf-playa-de-las-teresitas", "Playa de Las Teresitas", 28.5085, -16.1853, List.of("GCTS", "GCXO"), List.of("coast", "beach", "panoramic"), 84.0),
            new VisualWaypoint("tf-candelaria", "Candelaria", 28.3548, -16.3727, List.of("GCTS", "GCXO"), List.of("coast", "historic", "urban"), 77.0),
            new VisualWaypoint("tf-barranco-del-infierno", "Barranco del Infierno", 28.1228, -16.7247, List.of("GCTS", "GCXO"), List.of("ravine", "mountain", "short", "panoramic"), 86.0),
            new VisualWaypoint("tf-el-medano", "El Medano", 28.0465, -16.5360, List.of("GCTS", "GCXO"), List.of("coast", "beach", "short", "island"), 78.0),
            new VisualWaypoint("tf-roque-cinchado", "Roque Cinchado", 28.2236, -16.6320, List.of("GCTS", "GCXO"), List.of("volcano", "mountain", "panoramic"), 88.0),
            new VisualWaypoint("tf-santa-cruz", "Santa Cruz de Tenerife", 28.4636, -16.2518, List.of("GCLP", "GCTS", "GCXO"), List.of("urban", "coast", "historic", "inter-island", "cross-country"), 76.0)
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
                            new Waypoint("Agaete", 28.1005, -15.7004),
                            new Waypoint("Punta de Sardina", 28.1640, -15.7100),
                            new Waypoint("Canal Gran Canaria Tenerife", 28.1800, -16.0000),
                            new Waypoint("Costa de Guimar", 28.2920, -16.3730),
                            new Waypoint("Candelaria", 28.3548, -16.3727),
                            new Waypoint("Santa Cruz de Tenerife", 28.4636, -16.2518),
                            new Waypoint("Macizo de Anaga", 28.5447, -16.2009)
                    ),
                    List.of("islands", "inter-island", "coast", "panoramic", "cross-country"),
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

    public static List<FuelPrice> fuelPrices() {
        return FUEL_PRICES;
    }

    public static List<AirportFuelPrice> airportFuelPrices() {
        return AIRPORT_FUEL_PRICES;
    }

    public static List<FlightRoute> routes() {
        return ROUTES;
    }

    public static List<VisualWaypoint> visualWaypoints() {
        return VISUAL_WAYPOINTS;
    }
}
