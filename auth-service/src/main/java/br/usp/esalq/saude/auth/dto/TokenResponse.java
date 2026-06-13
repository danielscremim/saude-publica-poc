package br.usp.esalq.saude.auth.dto;

/** Resposta de token padrao OAuth2 (RFC 6749 secao 5.1). */
public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String scope
) { }
