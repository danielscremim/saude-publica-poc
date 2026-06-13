package br.usp.esalq.saude.history.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/** RestClients dedicados para os 3 servicos a montante. */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient patientRestClient(@Value("${downstream.patient-service}") String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).build();
    }

    @Bean
    public RestClient resultRestClient(@Value("${downstream.result-service}") String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).build();
    }

    @Bean
    public RestClient consentRestClient(@Value("${downstream.consent-service}") String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).build();
    }

    /** Exposto apenas para o caso de configuracao futura (timeout, interceptors). */
    @SuppressWarnings("unused")
    private static Duration defaultTimeout() {
        return Duration.ofMillis(800);  // RNF-05: history <= 800ms
    }
}
