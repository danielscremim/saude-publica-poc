package br.usp.esalq.saude.triage.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Registro de triagem na chegada do paciente a uma UBS / pronto atendimento.
 * Sinais vitais + queixa principal + prioridade Manchester.
 *
 * Nota LGPD/RNF-06: o paciente e referenciado APENAS pelo UUID interno (nunca CPF).
 */
@Entity
@Table(name = "triages",
       indexes = @Index(name = "idx_triage_patient_performed", columnList = "patient_uuid, performed_at"))
public class Triage {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "patient_uuid", nullable = false)
    private UUID patientUuid;

    @Column(name = "performed_by", nullable = false)
    private String performedBy;

    @Column(nullable = false)
    private String unit;  // identificador da UBS (ex: UBS-VILA-MARIANA)

    @Column(name = "blood_pressure_systolic")
    private Integer bloodPressureSystolic;

    @Column(name = "blood_pressure_diastolic")
    private Integer bloodPressureDiastolic;

    @Column(name = "heart_rate")
    private Integer heartRate;

    @Column(name = "respiratory_rate")
    private Integer respiratoryRate;

    private Double temperature;

    @Column(name = "oxygen_saturation")
    private Integer oxygenSaturation;

    @Column(name = "pain_level")
    private Integer painLevel;

    @Column(length = 1024)
    private String complaint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TriagePriority priority;

    @Column(name = "performed_at", nullable = false)
    private Instant performedAt;

    protected Triage() { }

    public Triage(UUID id, UUID patientUuid, String performedBy, String unit,
                  Integer bps, Integer bpd, Integer hr, Integer rr, Double temp,
                  Integer spo2, Integer pain, String complaint, TriagePriority priority) {
        this.id = id;
        this.patientUuid = patientUuid;
        this.performedBy = performedBy;
        this.unit = unit;
        this.bloodPressureSystolic = bps;
        this.bloodPressureDiastolic = bpd;
        this.heartRate = hr;
        this.respiratoryRate = rr;
        this.temperature = temp;
        this.oxygenSaturation = spo2;
        this.painLevel = pain;
        this.complaint = complaint;
        this.priority = priority;
        this.performedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getPatientUuid() { return patientUuid; }
    public String getPerformedBy() { return performedBy; }
    public String getUnit() { return unit; }
    public Integer getBloodPressureSystolic() { return bloodPressureSystolic; }
    public Integer getBloodPressureDiastolic() { return bloodPressureDiastolic; }
    public Integer getHeartRate() { return heartRate; }
    public Integer getRespiratoryRate() { return respiratoryRate; }
    public Double getTemperature() { return temperature; }
    public Integer getOxygenSaturation() { return oxygenSaturation; }
    public Integer getPainLevel() { return painLevel; }
    public String getComplaint() { return complaint; }
    public TriagePriority getPriority() { return priority; }
    public Instant getPerformedAt() { return performedAt; }
}
