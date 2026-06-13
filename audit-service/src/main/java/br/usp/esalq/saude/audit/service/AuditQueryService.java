package br.usp.esalq.saude.audit.service;

import br.usp.esalq.saude.audit.dto.AuditAnomalyResponse;
import br.usp.esalq.saude.audit.dto.AuditLogResponse;
import br.usp.esalq.saude.audit.entity.AuditAnomaly;
import br.usp.esalq.saude.audit.entity.AuditLog;
import br.usp.esalq.saude.audit.repository.AuditAnomalyRepository;
import br.usp.esalq.saude.audit.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AuditQueryService {

    private final AuditLogRepository logRepository;
    private final AuditAnomalyRepository anomalyRepository;

    public AuditQueryService(AuditLogRepository logRepository,
                             AuditAnomalyRepository anomalyRepository) {
        this.logRepository = logRepository;
        this.anomalyRepository = anomalyRepository;
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> byPatient(UUID patientUuid) {
        return logRepository.findByPatientUuidOrderByTimestampDesc(patientUuid)
                .stream().map(AuditQueryService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> byRequester(String requesterId) {
        return logRepository.findByRequesterIdOrderByTimestampDesc(requesterId)
                .stream().map(AuditQueryService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AuditAnomalyResponse> anomalies() {
        return anomalyRepository.findAllByOrderByDetectedAtDesc()
                .stream().map(AuditQueryService::toResponse).toList();
    }

    private static AuditLogResponse toResponse(AuditLog l) {
        return new AuditLogResponse(l.getEventId(), l.getRequesterId(), l.getPatientUuid(),
                l.getAction(), l.getPurpose(), l.getSourceService(), l.getTimestamp());
    }

    private static AuditAnomalyResponse toResponse(AuditAnomaly a) {
        return new AuditAnomalyResponse(a.getId(), a.getPatientUuid(), a.getRequesterId(),
                a.getEventCount(), a.getWindowMinutes(), a.getThreshold(), a.getDetectedAt());
    }
}
