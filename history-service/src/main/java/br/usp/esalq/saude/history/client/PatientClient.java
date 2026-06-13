package br.usp.esalq.saude.history.client;

import br.usp.esalq.saude.history.dto.PatientDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class PatientClient {

    private final RestClient client;

    public PatientClient(@Qualifier("patientRestClient") RestClient client) {
        this.client = client;
    }

    public PatientDto findByUuid(UUID uuid) {
        return client.get()
                .uri("/v1/patients/{uuid}", uuid)
                .retrieve()
                .body(PatientDto.class);
    }
}
