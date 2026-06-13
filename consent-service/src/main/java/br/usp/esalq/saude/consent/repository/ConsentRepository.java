package br.usp.esalq.saude.consent.repository;

import br.usp.esalq.saude.consent.entity.Consent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ConsentRepository extends JpaRepository<Consent, UUID> {

    /**
     * Retorna consentimentos ATIVOS (nao revogados) do paciente para a instituicao.
     * Consulta indexada por (patient_uuid, institution_id) para responder em ~ms (RNF-06).
     */
    @Query("""
        SELECT c FROM Consent c
        WHERE c.patientUuid = :patientUuid
          AND c.institutionId = :institutionId
          AND c.revokedAt IS NULL
    """)
    List<Consent> findActive(UUID patientUuid, String institutionId);

    List<Consent> findByPatientUuid(UUID patientUuid);
}
