package br.usp.esalq.saude.triage.dto;

import jakarta.validation.constraints.*;

import java.util.UUID;

/**
 * Entrada para registro de triagem.
 * Sinais vitais sao opcionais individualmente, mas a prioridade pode ser
 * sugerida automaticamente se omitida (auto-classificacao basica).
 */
public record CreateTriageRequest(
        @NotNull UUID patientUuid,
        @NotBlank String performedBy,
        @NotBlank String unit,

        @Min(40) @Max(260)  Integer bloodPressureSystolic,
        @Min(20) @Max(200)  Integer bloodPressureDiastolic,
        @Min(20) @Max(220)  Integer heartRate,
        @Min(5)  @Max(60)   Integer respiratoryRate,
        @DecimalMin("30.0") @DecimalMax("45.0") Double temperature,
        @Min(50) @Max(100)  Integer oxygenSaturation,
        @Min(0)  @Max(10)   Integer painLevel,

        String complaint,
        /** Se nulo, e calculada pelo servico a partir dos sinais vitais. */
        String priority
) { }
