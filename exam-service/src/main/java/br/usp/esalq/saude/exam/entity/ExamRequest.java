package br.usp.esalq.saude.exam.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "exam_requests")
public class ExamRequest {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID examId;

    @Column(nullable = false)
    private UUID patientUuid;

    @Column(nullable = false)
    private String examType;

    /** Origem do dado (data provenance): UBS, LAB_PUBLICO, LAB_PRIVADO, HOSPITAL_PRIVADO */
    @Column(nullable = false)
    private String origin;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private Instant requestedAt;

    protected ExamRequest() { }

    public ExamRequest(UUID examId, UUID patientUuid, String examType, String origin) {
        this.examId = examId;
        this.patientUuid = patientUuid;
        this.examType = examType;
        this.origin = origin;
        this.status = "REQUESTED";
        this.requestedAt = Instant.now();
    }

    public UUID getExamId() { return examId; }
    public UUID getPatientUuid() { return patientUuid; }
    public String getExamType() { return examType; }
    public String getOrigin() { return origin; }
    public String getStatus() { return status; }
    public Instant getRequestedAt() { return requestedAt; }
}
