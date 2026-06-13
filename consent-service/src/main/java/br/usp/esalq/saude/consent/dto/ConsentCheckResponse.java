package br.usp.esalq.saude.consent.dto;

import java.util.UUID;

/**
 * Resposta da verificacao de consentimento usada pelo history-service antes
 * de retornar qualquer dado clinico (RNF-06: HTTP 403 se granted=false).
 */
public record ConsentCheckResponse(
        UUID patientUuid,
        String institutionId,
        boolean granted,
        String scope
) { }
