package br.usp.esalq.saude.lab.event;

import java.time.Instant;
import java.util.UUID;

public record ExamRequestedEvent(
        UUID examId,
        UUID patientUuid,
        String examType,
        String origin,
        Instant requestedAt
) { }
