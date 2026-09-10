package br.usp.esalq.saude.history.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;

/**
 * Valida um JWT HS256 emitido pelo auth-service e o converte em AuthenticatedCaller.
 * Compartilhado pelo JwtAuthFilter (REST) e pelo CallerContextInterceptor (GraphQL):
 * um unico ponto de validacao para os dois transportes (RNF-03).
 */
@Component
public class JwtValidator {

    private final SecretKey key;
    private final String issuer;

    public JwtValidator(@Value("${auth.jwt.secret}") String base64Secret,
                        @Value("${auth.jwt.issuer}") String issuer) {
        this.key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(base64Secret));
        this.issuer = issuer;
    }

    /** @throws JwtException se o token for invalido, expirado ou de outro emissor. */
    public AuthenticatedCaller validate(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new AuthenticatedCaller(
                claims.getSubject(),
                claims.get("inst", String.class),
                claims.get("scope", String.class));
    }

    /** Extrai o token de um header "Bearer xxx"; null se ausente/malformado. */
    public static String extractBearer(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) return null;
        String t = authorizationHeader.substring("Bearer ".length()).trim();
        return t.isEmpty() ? null : t;
    }
}
