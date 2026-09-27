package flightdiscovery.paull.domain.aviationweather;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonProperty;

import flightdiscovery.paull.domain.model.AviationCloudLayer;
import flightdiscovery.paull.domain.model.MetarReport;
import flightdiscovery.paull.domain.model.TafForecastPeriod;
import flightdiscovery.paull.domain.model.TafReport;

@Service
public class AviationWeatherClient {

    public static final String PROVIDER = "AviationWeather.gov";
    public static final String BASE_URL = "https://aviationweather.gov";
    private static final Duration METAR_TTL = Duration.ofMinutes(5);
    private static final Duration TAF_TTL = Duration.ofMinutes(10);
    private static final String USER_AGENT = "FlightDiscovery/0.0.1";

    private final RestClient restClient;
    private final Clock clock;
    private final Map<String, CacheEntry<Optional<MetarReport>>> metarCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<Optional<TafReport>>> tafCache = new ConcurrentHashMap<>();

    public AviationWeatherClient() {
        this(RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("User-Agent", USER_AGENT)
                .build(), Clock.systemUTC());
    }

    AviationWeatherClient(RestClient restClient, Clock clock) {
        this.restClient = restClient;
        this.clock = clock;
    }

    public Optional<MetarReport> latestMetar(String airportCode) {
        String normalizedCode = normalizeAirportCode(airportCode);
        return cached("metar:" + normalizedCode, METAR_TTL, metarCache, () -> fetchMetar(normalizedCode));
    }

    public Optional<TafReport> latestTaf(String airportCode) {
        String normalizedCode = normalizeAirportCode(airportCode);
        return cached("taf:" + normalizedCode, TAF_TTL, tafCache, () -> fetchTaf(normalizedCode));
    }

    private Optional<MetarReport> fetchMetar(String airportCode) {
        try {
            MetarApiResponse apiResponse = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/data/metar")
                            .queryParam("ids", airportCode)
                            .queryParam("format", "json")
                            .build())
                    .retrieve()
                    .onStatus(status -> status.value() == 204, (request, clientResponse) -> {
                    })
                    .body(MetarApiResponse.class);

            if (apiResponse == null || apiResponse.value() == null || apiResponse.value().isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(toMetarReport(apiResponse.value().getFirst()));
        } catch (RestClientException | IllegalArgumentException exception) {
            throw new AviationWeatherException("Unable to retrieve METAR from AviationWeather.gov", exception);
        }
    }

    private Optional<TafReport> fetchTaf(String airportCode) {
        try {
            TafApiResponse apiResponse = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/data/taf")
                            .queryParam("ids", airportCode)
                            .queryParam("format", "json")
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, clientResponse) -> {
                        if (clientResponse.getStatusCode().value() != 204) {
                            throw new AviationWeatherException("AviationWeather.gov TAF request failed", null);
                        }
                    })
                    .body(TafApiResponse.class);

            if (apiResponse == null || apiResponse.value() == null || apiResponse.value().isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(toTafReport(apiResponse.value().getFirst()));
        } catch (RestClientException | IllegalArgumentException exception) {
            throw new AviationWeatherException("Unable to retrieve TAF from AviationWeather.gov", exception);
        }
    }

    private <T> T cached(String key, Duration ttl, Map<String, CacheEntry<T>> cache, CacheLoader<T> loader) {
        CacheEntry<T> cachedEntry = cache.get(key);
        Instant now = clock.instant();
        if (cachedEntry != null && cachedEntry.expiresAt().isAfter(now)) {
            return cachedEntry.value();
        }

        T value = loader.load();
        cache.put(key, new CacheEntry<>(value, now.plus(ttl)));
        return value;
    }

    private MetarReport toMetarReport(MetarApiReport report) {
        Instant observedAt = epochSeconds(report.obsTime());
        Instant receivedAt = parseInstant(report.receiptTime());

        return new MetarReport(
                report.icaoId(),
                report.name(),
                report.rawOb(),
                toIsoString(observedAt),
                toIsoString(receivedAt),
                ageMinutes(observedAt),
                report.fltCat(),
                report.wdir(),
                report.wspd(),
                report.wgst(),
                report.visib(),
                report.altim(),
                report.temp(),
                report.dewp(),
                report.wxString(),
                toCloudLayers(report.clouds()),
                PROVIDER
        );
    }

    private TafReport toTafReport(TafApiReport report) {
        Instant issuedAt = parseInstant(report.issueTime());

        return new TafReport(
                report.icaoId(),
                report.name(),
                report.rawTAF(),
                toIsoString(issuedAt),
                toIsoString(parseInstant(report.bulletinTime())),
                toIsoString(epochSeconds(report.validTimeFrom())),
                toIsoString(epochSeconds(report.validTimeTo())),
                ageMinutes(issuedAt),
                report.fcsts() == null
                        ? List.of()
                        : report.fcsts().stream().map(this::toForecastPeriod).toList(),
                PROVIDER
        );
    }

    private TafForecastPeriod toForecastPeriod(TafApiForecast forecast) {
        return new TafForecastPeriod(
                toIsoString(epochSeconds(forecast.timeFrom())),
                toIsoString(epochSeconds(forecast.timeTo())),
                toIsoString(epochSeconds(forecast.timeBec())),
                forecast.fcstChange(),
                forecast.probability(),
                forecast.wdir(),
                forecast.wspd(),
                forecast.wgst(),
                forecast.visib(),
                forecast.wxString(),
                toCloudLayers(forecast.clouds())
        );
    }

    private List<AviationCloudLayer> toCloudLayers(List<ApiCloudLayer> clouds) {
        if (clouds == null) {
            return List.of();
        }

        return clouds.stream()
                .map(cloud -> new AviationCloudLayer(cloud.cover(), cloud.base(), cloud.type()))
                .toList();
    }

    private Long ageMinutes(Instant issuedAt) {
        if (issuedAt == null) {
            return null;
        }

        return Math.max(0L, Duration.between(issuedAt, clock.instant()).toMinutes());
    }

    private Instant epochSeconds(Long epochSeconds) {
        return epochSeconds == null ? null : Instant.ofEpochSecond(epochSeconds);
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return Instant.parse(value);
    }

    private String toIsoString(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private String normalizeAirportCode(String airportCode) {
        if (airportCode == null || airportCode.isBlank()) {
            throw new IllegalArgumentException("airportCode is required");
        }

        return airportCode.trim().toUpperCase();
    }

    private interface CacheLoader<T> {
        T load();
    }

    private record CacheEntry<T>(
            T value,
            Instant expiresAt
    ) {
    }

    private record MetarApiResponse(
            @JsonProperty("value")
            List<MetarApiReport> value
    ) {
    }

    private record TafApiResponse(
            @JsonProperty("value")
            List<TafApiReport> value
    ) {
    }

    private record MetarApiReport(
            String icaoId,
            String receiptTime,
            Long obsTime,
            Integer temp,
            Integer dewp,
            Object wdir,
            Integer wspd,
            Integer wgst,
            String visib,
            Integer altim,
            String rawOb,
            String name,
            String fltCat,
            String wxString,
            List<ApiCloudLayer> clouds
    ) {
    }

    private record TafApiReport(
            String icaoId,
            String bulletinTime,
            String issueTime,
            Long validTimeFrom,
            Long validTimeTo,
            String rawTAF,
            String name,
            List<TafApiForecast> fcsts
    ) {
    }

    private record TafApiForecast(
            Long timeFrom,
            Long timeTo,
            Long timeBec,
            String fcstChange,
            Integer probability,
            Object wdir,
            Integer wspd,
            Integer wgst,
            String visib,
            String wxString,
            List<ApiCloudLayer> clouds
    ) {
    }

    private record ApiCloudLayer(
            String cover,
            Integer base,
            String type
    ) {
    }
}
