package br.usp.esalq.saude.lab.event;

import java.time.Instant;
import java.util.UUID;

/** Evento publicado em exam.completed apos o processamento do exame. */
public record ExamCompletedEvent(
        UUID examId,
        UUID patientUuid,
        String examType,
        String origin,
        double resultValue,
        String resultUnit,
        Instant completedAt
) { }
