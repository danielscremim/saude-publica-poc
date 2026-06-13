package br.usp.esalq.saude.exam.event;

import java.time.Instant;
import java.util.UUID;

/** Evento publicado no topico exam.requested quando um exame e solicitado. */
public record ExamRequestedEvent(
        UUID examId,
        UUID patientUuid,
        String examType,
        String origin,
        Instant requestedAt
) { }
