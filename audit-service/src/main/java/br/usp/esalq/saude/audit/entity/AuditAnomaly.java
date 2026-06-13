package br.usp.esalq.saude.audit.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Marcacao de anomalia detectada (excesso de acessos a um paciente em janela curta).
 * Tambem append-only: cada deteccao gera uma nova linha.
 */
@Entity
@Table(name = "audit_anomaly",
       indexes = @Index(name = "idx_anomaly_patient_detected", columnList = "patient_uuid, detected_at"))
public class AuditAnomaly {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "patient_uuid", nullable = false, updatable = false)
    private UUID patientUuid;

    @Column(name = "requester_id", updatable = false)
    private String requesterId;

    @Column(name = "event_count", nullable = false, updatable = false)
    private long eventCount;

    @Column(name = "window_minutes", nullable = false, updatable = false)
    private int windowMinutes;

    @Column(name = "threshold", nullable = false, updatable = false)
    private long threshold;

    @Column(name = "detected_at", nullable = false, updatable = false)
    private Instant detectedAt;

    protected AuditAnomaly() { }

    public AuditAnomaly(UUID id, UUID patientUuid, String requesterId,
                        long eventCount, int windowMinutes, long threshold) {
        this.id = id;
        this.patientUuid = patientUuid;
        this.requesterId = requesterId;
        this.eventCount = eventCount;
        this.windowMinutes = windowMinutes;
        this.threshold = threshold;
        this.detectedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getPatientUuid() { return patientUuid; }
    public String getRequesterId() { return requesterId; }
    public long getEventCount() { return eventCount; }
    public int getWindowMinutes() { return windowMinutes; }
    public long getThreshold() { return threshold; }
    public Instant getDetectedAt() { return detectedAt; }
}
