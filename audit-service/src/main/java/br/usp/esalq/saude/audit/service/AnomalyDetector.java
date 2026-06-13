package br.usp.esalq.saude.audit.service;

import br.usp.esalq.saude.audit.entity.AuditAnomaly;
import br.usp.esalq.saude.audit.entity.AuditLog;
import br.usp.esalq.saude.audit.repository.AuditAnomalyRepository;
import br.usp.esalq.saude.audit.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Detecta acessos anormais a um paciente em janela deslizante (RNF-06).
 * Executa apos cada INSERT em audit_log. Se a contagem na janela passa do
 * threshold E nao ha anomaly recente para esse paciente, registra nova anomaly.
 */
@Service
public class AnomalyDetector {

    private static final Logger log = LoggerFactory.getLogger(AnomalyDetector.class);

    private final AuditLogRepository logRepository;
    private final AuditAnomalyRepository anomalyRepository;
    private final int windowMinutes;
    private final long threshold;

    public AnomalyDetector(AuditLogRepository logRepository,
                           AuditAnomalyRepository anomalyRepository,
                           @Value("${audit.anomaly.window-minutes}") int windowMinutes,
                           @Value("${audit.anomaly.threshold}") long threshold) {
        this.logRepository = logRepository;
        this.anomalyRepository = anomalyRepository;
        this.windowMinutes = windowMinutes;
        this.threshold = threshold;
    }

    public void checkAfterInsert(AuditLog inserted) {
        Instant windowStart = Instant.now().minus(Duration.ofMinutes(windowMinutes));
        long count = logRepository.countByPatientUuidAndTimestampGreaterThanEqual(
                inserted.getPatientUuid(), windowStart);

        if (count < threshold) return;

        // Evita registrar a mesma anomalia em cada novo evento dentro da mesma janela.
        boolean alreadyFlagged = anomalyRepository.existsByPatientUuidAndDetectedAtGreaterThanEqual(
                inserted.getPatientUuid(), windowStart);
        if (alreadyFlagged) return;

        AuditAnomaly anomaly = new AuditAnomaly(
                UUID.randomUUID(), inserted.getPatientUuid(), inserted.getRequesterId(),
                count, windowMinutes, threshold);
        anomalyRepository.save(anomaly);
        log.warn("ANOMALIA detectada: paciente={}, requester={}, count={} em {} min (threshold={})",
                inserted.getPatientUuid(), inserted.getRequesterId(), count, windowMinutes, threshold);
    }
}
