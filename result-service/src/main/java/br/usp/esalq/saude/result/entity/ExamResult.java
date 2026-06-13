package br.usp.esalq.saude.result.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "exam_results", indexes = @Index(name = "idx_patient", columnList = "patientUuid"))
public class ExamResult {

    @Id
    private UUID examId;

    @Column(nullable = false)
    private UUID patientUuid;

    @Column(nullable = false)
    private String examType;

    @Column(nullable = false)
    private String origin;

    @Column(nullable = false)
    private double resultValue;

    @Column(nullable = false)
    private String resultUnit;

    @Column(nullable = false)
    private Instant completedAt;

    protected ExamResult() { }

    public ExamResult(UUID examId, UUID patientUuid, String examType, String origin,
                      double resultValue, String resultUnit, Instant completedAt) {
        this.examId = examId;
        this.patientUuid = patientUuid;
        this.examType = examType;
        this.origin = origin;
        this.resultValue = resultValue;
        this.resultUnit = resultUnit;
        this.completedAt = completedAt;
    }

    public UUID getExamId() { return examId; }
    public UUID getPatientUuid() { return patientUuid; }
    public String getExamType() { return examType; }
    public String getOrigin() { return origin; }
    public double getResultValue() { return resultValue; }
    public String getResultUnit() { return resultUnit; }
    public Instant getCompletedAt() { return completedAt; }
}
