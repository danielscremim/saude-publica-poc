package br.usp.esalq.saude.consent.dto;

import java.time.Instant;
import java.util.UUID;

public record ConsentResponse(
        UUID id,
        UUID patientUuid,
        String institutionId,
        String scope,
        Instant grantedAt,
        Instant revokedAt
) { }
