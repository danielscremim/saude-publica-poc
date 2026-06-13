package br.usp.esalq.saude.history.client;

import br.usp.esalq.saude.history.dto.ResultDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

@Component
public class ResultClient {

    private static final ParameterizedTypeReference<List<ResultDto>> LIST_TYPE =
            new ParameterizedTypeReference<>() { };

    private final RestClient client;

    public ResultClient(@Qualifier("resultRestClient") RestClient client) {
        this.client = client;
    }

    public List<ResultDto> findByPatient(UUID patientUuid) {
        return client.get()
                .uri("/v1/results/patient/{uuid}", patientUuid)
                .retrieve()
                .body(LIST_TYPE);
    }
}
