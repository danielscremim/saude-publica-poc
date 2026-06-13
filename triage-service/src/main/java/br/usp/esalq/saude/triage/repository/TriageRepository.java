package br.usp.esalq.saude.triage.repository;

import br.usp.esalq.saude.triage.entity.Triage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TriageRepository extends JpaRepository<Triage, UUID> {
    List<Triage> findByPatientUuidOrderByPerformedAtDesc(UUID patientUuid);
}
