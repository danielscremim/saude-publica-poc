package br.usp.esalq.saude.history.client;

import br.usp.esalq.saude.history.dto.NotificationDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

@Component
public class NotificationClient {

    private static final ParameterizedTypeReference<List<NotificationDto>> LIST_TYPE =
            new ParameterizedTypeReference<>() { };

    private final RestClient client;

    public NotificationClient(@Qualifier("notificationRestClient") RestClient client) {
        this.client = client;
    }

    public List<NotificationDto> findByPatient(UUID patientUuid) {
        return client.get()
                .uri("/v1/notifications/patient/{uuid}", patientUuid)
                .retrieve()
                .body(LIST_TYPE);
    }
}
