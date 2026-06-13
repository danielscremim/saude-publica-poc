package br.usp.esalq.saude.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Requisicao de token no padrao OAuth2 Client Credentials (simplificado).
 * Em producao usariamos x-www-form-urlencoded com Basic Auth no header;
 * aqui aceitamos JSON para facilitar a PoC.
 */
public record TokenRequest(
        @NotBlank String clientId,
        @NotBlank String clientSecret,
        String scope
) { }
