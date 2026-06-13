package br.usp.esalq.saude.audit.service;

import br.usp.esalq.saude.audit.entity.AuditLog;
import br.usp.esalq.saude.audit.event.AuditEvent;
import br.usp.esalq.saude.audit.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditConsumerService {

    private static final Logger log = LoggerFactory.getLogger(AuditConsumerService.class);

    private final AuditLogRepository repository;
    private final AnomalyDetector anomalyDetector;

    public AuditConsumerService(AuditLogRepository repository, AnomalyDetector anomalyDetector) {
        this.repository = repository;
        this.anomalyDetector = anomalyDetector;
    }

    /**
     * Persiste o evento em audit_log (PK = eventId garante idempotencia: se o mesmo
     * evento for re-entregue por rebalance/falha, o segundo INSERT colide e e ignorado).
     */
    @KafkaListener(topics = "audit.events", groupId = "audit-service")
    @Transactional
    public void onAuditEvent(AuditEvent event) {
        AuditLog entry = new AuditLog(
                event.eventId(), event.requesterId(), event.patientUuid(),
                event.action(), event.purpose(), event.sourceService(), event.timestamp());
        try {
            repository.save(entry);
            log.debug("audit.events armazenado: eventId={}, paciente={}",
                    event.eventId(), event.patientUuid());
        } catch (DataIntegrityViolationException dup) {
            log.debug("Evento ja persistido (idempotencia): eventId={}", event.eventId());
            return;
        }
        anomalyDetector.checkAfterInsert(entry);
    }
}
