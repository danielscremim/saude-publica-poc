package br.usp.esalq.saude.exam.dto;

import java.time.Instant;
import java.util.UUID;

public record ExamResponse(
        UUID examId,
        UUID patientUuid,
        String examType,
        String origin,
        String status,
        Instant requestedAt
) { }
