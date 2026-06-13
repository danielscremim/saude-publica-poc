package br.usp.esalq.saude.audit.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Registro IMUTAVEL de um acesso a dados (RNF-03: auditoria imutavel).
 *
 * Imutabilidade e garantida POR CONVENCAO neste servico:
 *   - sem endpoint REST de UPDATE/DELETE;
 *   - apenas o consumer Kafka faz INSERT.
 *
 * A primary key e o proprio eventId do evento Kafka -> idempotencia automatica
 * (re-processamento do mesmo evento gera DataIntegrityViolation no UNIQUE).
 *
 * Indices: patient_uuid + timestamp para suportar a janela deslizante da
 * anomaly detection sem table scan.
 */
@Entity
@Table(name = "audit_log",
       indexes = {
           @Index(name = "idx_audit_patient_ts", columnList = "patient_uuid, timestamp"),
           @Index(name = "idx_audit_requester_ts", columnList = "requester_id, timestamp")
       })
public class AuditLog {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "requester_id", nullable = false, updatable = false)
    private String requesterId;

    @Column(name = "patient_uuid", nullable = false, updatable = false)
    private UUID patientUuid;

    @Column(nullable = false, updatable = false)
    private String action;

    @Column(nullable = false, updatable = false)
    private String purpose;

    @Column(name = "source_service", nullable = false, updatable = false)
    private String sourceService;

    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    protected AuditLog() { }

    public AuditLog(UUID eventId, String requesterId, UUID patientUuid, String action,
                    String purpose, String sourceService, Instant timestamp) {
        this.eventId = eventId;
        this.requesterId = requesterId;
        this.patientUuid = patientUuid;
        this.action = action;
        this.purpose = purpose;
        this.sourceService = sourceService;
        this.timestamp = timestamp;
    }

    public UUID getEventId() { return eventId; }
    public String getRequesterId() { return requesterId; }
    public UUID getPatientUuid() { return patientUuid; }
    public String getAction() { return action; }
    public String getPurpose() { return purpose; }
    public String getSourceService() { return sourceService; }
    public Instant getTimestamp() { return timestamp; }
}
