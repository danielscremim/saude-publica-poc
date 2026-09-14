package br.usp.esalq.saude.history.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/** RestClients dedicados para os servicos a montante agregados pela fachada. */
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

    @Bean
    public RestClient triageRestClient(@Value("${downstream.triage-service}") String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).build();
    }

    @Bean
    public RestClient notificationRestClient(@Value("${downstream.notification-service}") String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).build();
    }

    @Bean
    public RestClient auditRestClient(@Value("${downstream.audit-service}") String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).build();
    }

    /** Exposto apenas para o caso de configuracao futura (timeout, interceptors). */
    @SuppressWarnings("unused")
    private static Duration defaultTimeout() {
        return Duration.ofMillis(800);  // RNF-05: history <= 800ms
    }
}
