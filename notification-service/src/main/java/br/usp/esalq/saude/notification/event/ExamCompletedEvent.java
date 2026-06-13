package br.usp.esalq.saude.notification.event;

import java.time.Instant;
import java.util.UUID;

/** Espelho de exam.completed (record duplicado por servico - ver CLAUDE.md). */
public record ExamCompletedEvent(
        UUID examId,
        UUID patientUuid,
        String examType,
        String origin,
        double resultValue,
        String resultUnit,
        Instant completedAt
) { }
