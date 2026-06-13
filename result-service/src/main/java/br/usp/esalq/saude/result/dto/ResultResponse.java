package br.usp.esalq.saude.result.dto;

import java.time.Instant;
import java.util.UUID;

public record ResultResponse(
        UUID examId,
        UUID patientUuid,
        String examType,
        String origin,
        double resultValue,
        String resultUnit,
        Instant completedAt
) { }
