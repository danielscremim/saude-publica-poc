package br.usp.esalq.saude.history.dto;

import java.time.Instant;
import java.util.UUID;

/** Espelho da resposta do notification-service. */
public record NotificationDto(
        UUID id,
        UUID patientUuid,
        String channel,
        String subject,
        String message,
        String status,
        Instant createdAt,
        Instant sentAt
) { }
