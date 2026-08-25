package flightdiscovery.paull.domain.weather;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class WeatherProviderSelectionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MockWeatherService.class, OpenMeteoWeatherService.class);

    @Test
    void usesMockWeatherProviderByDefault() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(WeatherService.class)
                .getBean(WeatherService.class)
                .isInstanceOf(MockWeatherService.class));
    }

    @Test
    void usesOpenMeteoWeatherProviderWhenConfigured() {
        contextRunner
                .withPropertyValues("weather.provider=open-meteo")
                .run(context -> assertThat(context)
                        .hasSingleBean(WeatherService.class)
                        .getBean(WeatherService.class)
                        .isInstanceOf(OpenMeteoWeatherService.class));
    }
}
