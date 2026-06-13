package br.usp.esalq.saude.audit.repository;

import br.usp.esalq.saude.audit.entity.AuditAnomaly;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditAnomalyRepository extends JpaRepository<AuditAnomaly, UUID> {

    List<AuditAnomaly> findByPatientUuidOrderByDetectedAtDesc(UUID patientUuid);

    List<AuditAnomaly> findAllByOrderByDetectedAtDesc();

    /** Usado para evitar duplicacao de anomaly enquanto a janela permanece estourada. */
    boolean existsByPatientUuidAndDetectedAtGreaterThanEqual(UUID patientUuid, Instant since);
}
