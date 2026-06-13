package br.usp.esalq.saude.auth.dto;

import java.util.UUID;

public record RegisterClientResponse(
        UUID id,
        String clientId,
        String institutionId,
        String scopes
) { }
