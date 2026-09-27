package flightdiscovery.paull.domain.aviationweather;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AviationWeatherClientTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-27T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void mapsMetarJsonToStructuredReport() {
        RestClient.Builder restClientBuilder = RestClient.builder().baseUrl(AviationWeatherClient.BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        AviationWeatherClient client = new AviationWeatherClient(restClientBuilder.build(), CLOCK);
        server.expect(requestTo(containsString("/api/data/metar")))
                .andExpect(queryParam("ids", "GCLP"))
                .andExpect(queryParam("format", "json"))
                .andRespond(withSuccess("""
                        {
                          "value": [{
                            "icaoId": "GCLP",
                            "receiptTime": "2026-09-27T11:34:19.567Z",
                            "obsTime": 1790508600,
                            "temp": 26,
                            "dewp": 23,
                            "wdir": 20,
                            "wspd": 6,
                            "visib": "6+",
                            "altim": 1018,
                            "rawOb": "METAR GCLP 271130Z 02006KT 9999 FEW025 26/23 Q1018",
                            "name": "Grand Canaria Arpt",
                            "fltCat": "VFR",
                            "clouds": [{"cover": "FEW", "base": 2500}]
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        var metar = client.latestMetar("gclp").orElseThrow();

        assertEquals("GCLP", metar.airportCode());
        assertEquals("METAR GCLP 271130Z 02006KT 9999 FEW025 26/23 Q1018", metar.rawText());
        assertEquals("VFR", metar.flightCategory());
        assertEquals(6, metar.windSpeedKt());
        assertEquals(30, metar.ageMinutes());
        assertEquals("FEW", metar.clouds().getFirst().cover());
        server.verify();
    }

    @Test
    void mapsTafJsonToStructuredReport() {
        RestClient.Builder restClientBuilder = RestClient.builder().baseUrl(AviationWeatherClient.BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        AviationWeatherClient client = new AviationWeatherClient(restClientBuilder.build(), CLOCK);
        server.expect(requestTo(containsString("/api/data/taf")))
                .andExpect(queryParam("ids", "GCLP"))
                .andExpect(queryParam("format", "json"))
                .andRespond(withSuccess("""
                        {
                          "value": [{
                            "icaoId": "GCLP",
                            "bulletinTime": "2026-09-27T08:00:00.000Z",
                            "issueTime": "2026-09-27T10:00:00.000Z",
                            "validTimeFrom": 1790503200,
                            "validTimeTo": 1790586000,
                            "rawTAF": "TAF GCLP 271000Z 2710/2809 04004KT CAVOK",
                            "name": "Grand Canaria Arpt",
                            "fcsts": [{
                              "timeFrom": 1790503200,
                              "timeTo": 1790514000,
                              "wdir": 40,
                              "wspd": 4,
                              "visib": "6+",
                              "clouds": [{"cover": "NSC"}]
                            }]
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        var taf = client.latestTaf("GCLP").orElseThrow();

        assertEquals("GCLP", taf.airportCode());
        assertEquals("TAF GCLP 271000Z 2710/2809 04004KT CAVOK", taf.rawText());
        assertEquals(120, taf.ageMinutes());
        assertEquals(1, taf.forecastPeriods().size());
        assertEquals(4, taf.forecastPeriods().getFirst().windSpeedKt());
        server.verify();
    }

    @Test
    void cachesMetarLookupsWithinTtl() {
        RestClient.Builder restClientBuilder = RestClient.builder().baseUrl(AviationWeatherClient.BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        AviationWeatherClient client = new AviationWeatherClient(restClientBuilder.build(), CLOCK);
        server.expect(requestTo(containsString("/api/data/metar")))
                .andRespond(withSuccess("""
                        {"value": [{"icaoId": "GCLP", "obsTime": 1790508600, "rawOb": "METAR GCLP 271130Z AUTO"}]}
                        """, MediaType.APPLICATION_JSON));

        assertTrue(client.latestMetar("GCLP").isPresent());
        assertTrue(client.latestMetar("GCLP").isPresent());

        server.verify();
    }

    @Test
    void returnsEmptyWhenTafIsAbsent() {
        RestClient.Builder restClientBuilder = RestClient.builder().baseUrl(AviationWeatherClient.BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        AviationWeatherClient client = new AviationWeatherClient(restClientBuilder.build(), CLOCK);
        server.expect(requestTo(containsString("/api/data/taf")))
                .andRespond(withSuccess("{\"value\": []}", MediaType.APPLICATION_JSON));

        assertTrue(client.latestTaf("GCLP").isEmpty());
        server.verify();
    }

    @Test
    void throwsControlledExceptionOnNetworkFailure() {
        RestClient.Builder restClientBuilder = RestClient.builder().baseUrl(AviationWeatherClient.BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(restClientBuilder).build();
        AviationWeatherClient client = new AviationWeatherClient(restClientBuilder.build(), CLOCK);
        server.expect(requestTo(containsString("/api/data/metar")))
                .andRespond(withServerError());

        assertThrows(AviationWeatherException.class, () -> client.latestMetar("GCLP"));
        server.verify();
    }
}
