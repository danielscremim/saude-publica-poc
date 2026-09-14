package br.usp.esalq.saude.history.dto;

import java.time.Instant;
import java.util.UUID;

/** Espelho da resposta do audit-service (trilha de acesso do paciente). */
public record AuditLogDto(
        UUID eventId,
        String requesterId,
        UUID patientUuid,
        String action,
        String purpose,
        String sourceService,
        Instant timestamp
) { }
