package br.usp.esalq.saude.history.client;

import br.usp.esalq.saude.history.dto.TriageDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

@Component
public class TriageClient {

    private static final ParameterizedTypeReference<List<TriageDto>> LIST_TYPE =
            new ParameterizedTypeReference<>() { };

    private final RestClient client;

    public TriageClient(@Qualifier("triageRestClient") RestClient client) {
        this.client = client;
    }

    public List<TriageDto> findByPatient(UUID patientUuid) {
        return client.get()
                .uri("/v1/triages/patient/{uuid}", patientUuid)
                .retrieve()
                .body(LIST_TYPE);
    }
}
