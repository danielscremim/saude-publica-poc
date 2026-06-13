package br.usp.esalq.saude.history.dto;

import java.util.UUID;

/** Espelho de ConsentCheckResponse do consent-service. */
public record ConsentCheckDto(UUID patientUuid, String institutionId, boolean granted, String scope) { }
