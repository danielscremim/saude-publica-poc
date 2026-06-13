package br.usp.esalq.saude.audit.repository;

import br.usp.esalq.saude.audit.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByPatientUuidOrderByTimestampDesc(UUID patientUuid);

    List<AuditLog> findByRequesterIdOrderByTimestampDesc(String requesterId);

    /** Conta acessos a um paciente em janela deslizante (alimenta anomaly detection). */
    long countByPatientUuidAndTimestampGreaterThanEqual(UUID patientUuid, Instant since);
}
