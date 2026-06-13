package br.usp.esalq.saude.consent.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Consentimento de acesso aos dados de um paciente por uma instituicao.
 * - patientUuid: paciente que concedeu o consentimento (NUNCA usar CPF aqui - RNF-06).
 * - institutionId: identificador da instituicao consumidora.
 * - scope: escopo concedido (ex: "history:read:own_patients").
 * - revokedAt: nulo enquanto ativo; preenchido na revogacao (preserva historico para auditoria).
 */
@Entity
@Table(name = "consents",
       indexes = {
           @Index(name = "idx_consents_patient_institution",
                  columnList = "patient_uuid, institution_id")
       })
public class Consent {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "patient_uuid", nullable = false)
    private UUID patientUuid;

    @Column(name = "institution_id", nullable = false)
    private String institutionId;

    @Column(nullable = false)
    private String scope;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected Consent() { }

    public Consent(UUID id, UUID patientUuid, String institutionId, String scope) {
        this.id = id;
        this.patientUuid = patientUuid;
        this.institutionId = institutionId;
        this.scope = scope;
        this.grantedAt = Instant.now();
    }

    public void revoke() {
        if (this.revokedAt == null) {
            this.revokedAt = Instant.now();
        }
    }

    public boolean isActive() {
        return revokedAt == null;
    }

    public UUID getId() { return id; }
    public UUID getPatientUuid() { return patientUuid; }
    public String getInstitutionId() { return institutionId; }
    public String getScope() { return scope; }
    public Instant getGrantedAt() { return grantedAt; }
    public Instant getRevokedAt() { return revokedAt; }
}
