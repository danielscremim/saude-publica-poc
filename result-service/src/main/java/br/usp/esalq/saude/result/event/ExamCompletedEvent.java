package br.usp.esalq.saude.result.event;

import java.time.Instant;
import java.util.UUID;

public record ExamCompletedEvent(
        UUID examId,
        UUID patientUuid,
        String examType,
        String origin,
        double resultValue,
        String resultUnit,
        Instant completedAt
) { }
