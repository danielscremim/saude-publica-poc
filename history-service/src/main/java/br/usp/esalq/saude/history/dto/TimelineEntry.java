package br.usp.esalq.saude.history.dto;

import java.time.Instant;
import java.util.UUID;

/** Entrada (exame) na linha do tempo clinica. */
public record TimelineEntry(
        UUID examId,
        String examType,
        String origin,
        double resultValue,
        String resultUnit,
        Instant completedAt
) { }
