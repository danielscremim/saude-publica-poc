package br.usp.esalq.saude.result.service;

import br.usp.esalq.saude.result.event.AuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Publica em audit.events (RNF-03). Todo escrita por consumidor externo deve
 * ser auditada - permite anomaly detection e prestacao de contas.
 */
@Service
public class AuditPublisher {

    private static final Logger log = LoggerFactory.getLogger(AuditPublisher.class);
    private static final String SOURCE = "result-service";
    private static final String TOPIC = "audit.events";

    private final KafkaTemplate<String, AuditEvent> kafkaTemplate;

    public AuditPublisher(KafkaTemplate<String, AuditEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(String requesterId, UUID patientUuid, String action, String purpose) {
        AuditEvent event = new AuditEvent(
                UUID.randomUUID(), requesterId, patientUuid,
                action, purpose == null ? "UNSPECIFIED" : purpose, SOURCE, Instant.now());
        kafkaTemplate.send(TOPIC, patientUuid.toString(), event);
        log.info("Auditoria publicada: action={}, requester={}, patient={}", action, requesterId, patientUuid);
    }
}
