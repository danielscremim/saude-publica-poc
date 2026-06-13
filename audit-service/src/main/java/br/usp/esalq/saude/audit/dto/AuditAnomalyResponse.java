package br.usp.esalq.saude.audit.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditAnomalyResponse(
        UUID id,
        UUID patientUuid,
        String requesterId,
        long eventCount,
        int windowMinutes,
        long threshold,
        Instant detectedAt
) { }
