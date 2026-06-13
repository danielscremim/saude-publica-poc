package br.usp.esalq.saude.history.client;

import br.usp.esalq.saude.history.dto.ConsentCheckDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class ConsentClient {

    private final RestClient client;

    public ConsentClient(@Qualifier("consentRestClient") RestClient client) {
        this.client = client;
    }

    public ConsentCheckDto check(UUID patientUuid, String institutionId) {
        return client.get()
                .uri(uri -> uri.path("/v1/consents/check")
                        .queryParam("patientUuid", patientUuid)
                        .queryParam("institutionId", institutionId)
                        .build())
                .retrieve()
                .body(ConsentCheckDto.class);
    }
}
