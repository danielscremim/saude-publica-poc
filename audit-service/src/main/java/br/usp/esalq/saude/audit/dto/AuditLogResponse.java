package br.usp.esalq.saude.audit.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID eventId,
        String requesterId,
        UUID patientUuid,
        String action,
        String purpose,
        String sourceService,
        Instant timestamp
) { }
