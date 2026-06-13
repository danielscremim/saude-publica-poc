package br.usp.esalq.saude.notification.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Notificacao enviada (ou agendada) para um paciente.
 * Canais previstos: LOG (atual), EMAIL, WEBHOOK (futuros - estrutura pronta).
 * Status: PENDING -> SENT (ou FAILED).
 */
@Entity
@Table(name = "notifications",
       indexes = @Index(name = "idx_notif_patient_created", columnList = "patient_uuid, created_at"))
public class Notification {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "patient_uuid", nullable = false)
    private UUID patientUuid;

    @Column(nullable = false)
    private String channel;

    @Column(nullable = false, length = 256)
    private String subject;

    @Column(nullable = false, length = 2048)
    private String message;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected Notification() { }

    public Notification(UUID id, UUID patientUuid, String channel, String subject, String message) {
        this.id = id;
        this.patientUuid = patientUuid;
        this.channel = channel;
        this.subject = subject;
        this.message = message;
        this.status = "PENDING";
        this.createdAt = Instant.now();
    }

    public void markSent() {
        this.status = "SENT";
        this.sentAt = Instant.now();
    }

    public void markFailed() {
        this.status = "FAILED";
    }

    public UUID getId() { return id; }
    public UUID getPatientUuid() { return patientUuid; }
    public String getChannel() { return channel; }
    public String getSubject() { return subject; }
    public String getMessage() { return message; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSentAt() { return sentAt; }
}
