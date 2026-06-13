package br.usp.esalq.saude.history.service;

import br.usp.esalq.saude.history.config.KafkaTopicConfig;
import br.usp.esalq.saude.history.event.AuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Publica eventos no topico audit.events. Cada acesso a dados clinicos gera um
 * evento (RNF-03: auditoria imutavel). O audit-service consumira esse topico.
 */
@Service
public class AuditPublisher {

    private static final Logger log = LoggerFactory.getLogger(AuditPublisher.class);
    private static final String SOURCE = "history-service";

    private final KafkaTemplate<String, AuditEvent> kafkaTemplate;

    public AuditPublisher(KafkaTemplate<String, AuditEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(String requesterId, UUID patientUuid, String action, String purpose) {
        AuditEvent event = new AuditEvent(
                UUID.randomUUID(), requesterId, patientUuid,
                action, purpose, SOURCE, Instant.now());
        kafkaTemplate.send(KafkaTopicConfig.AUDIT_EVENTS_TOPIC, patientUuid.toString(), event);
        log.info("Auditoria publicada: action={}, requester={}, patient={}",
                action, requesterId, patientUuid);
    }
}
