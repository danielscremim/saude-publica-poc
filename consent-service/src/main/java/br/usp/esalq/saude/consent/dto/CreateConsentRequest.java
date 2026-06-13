package br.usp.esalq.saude.consent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Registro de consentimento (paciente x instituicao x escopo). */
public record CreateConsentRequest(
        @NotNull UUID patientUuid,
        @NotBlank String institutionId,
        @NotBlank String scope
) { }
