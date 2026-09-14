package br.usp.esalq.saude.history.client;

import br.usp.esalq.saude.history.dto.AuditLogDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

@Component
public class AuditClient {

    private static final ParameterizedTypeReference<List<AuditLogDto>> LIST_TYPE =
            new ParameterizedTypeReference<>() { };

    private final RestClient client;

    public AuditClient(@Qualifier("auditRestClient") RestClient client) {
        this.client = client;
    }

    public List<AuditLogDto> findByPatient(UUID patientUuid) {
        return client.get()
                .uri("/v1/audit/patient/{uuid}", patientUuid)
                .retrieve()
                .body(LIST_TYPE);
    }
}
