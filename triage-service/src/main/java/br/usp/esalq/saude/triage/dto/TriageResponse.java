package br.usp.esalq.saude.triage.dto;

import java.time.Instant;
import java.util.UUID;

public record TriageResponse(
        UUID id,
        UUID patientUuid,
        String performedBy,
        String unit,
        Integer bloodPressureSystolic,
        Integer bloodPressureDiastolic,
        Integer heartRate,
        Integer respiratoryRate,
        Double temperature,
        Integer oxygenSaturation,
        Integer painLevel,
        String complaint,
        String priority,
        Instant performedAt
) { }
