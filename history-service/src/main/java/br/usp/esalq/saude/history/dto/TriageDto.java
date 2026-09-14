package br.usp.esalq.saude.history.dto;

import java.time.Instant;
import java.util.UUID;

/** Espelho da resposta do triage-service. */
public record TriageDto(
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
