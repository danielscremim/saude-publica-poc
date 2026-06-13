package br.usp.esalq.saude.history.dto;

import java.time.Instant;
import java.util.UUID;

/** Espelho da resposta do result-service. */
public record ResultDto(
        UUID examId,
        UUID patientUuid,
        String examType,
        String origin,
        double resultValue,
        String resultUnit,
        Instant completedAt
) { }
