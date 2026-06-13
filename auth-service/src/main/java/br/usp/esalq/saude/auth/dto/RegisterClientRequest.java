package br.usp.esalq.saude.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Registro de novo client. Em producao seria operacao administrativa restrita;
 * aqui exposto para facilitar o setup da PoC.
 */
public record RegisterClientRequest(
        @NotBlank String clientId,
        @NotBlank String clientSecret,
        @NotBlank String institutionId,
        @NotBlank String scopes
) { }
