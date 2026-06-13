package br.usp.esalq.saude.notification.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID patientUuid,
        String channel,
        String subject,
        String message,
        String status,
        Instant createdAt,
        Instant sentAt
) { }
