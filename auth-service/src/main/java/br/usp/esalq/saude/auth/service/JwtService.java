package br.usp.esalq.saude.auth.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * Emite JWTs HS256 assinados com o segredo configurado em auth.jwt.secret.
 * Claims:
 *   sub  = clientId (entidade autenticada)
 *   iss  = issuer configurado
 *   inst = institutionId (claim customizada)
 *   scope = escopos separados por espaco (formato OAuth2)
 *   jti  = identificador unico do token (auditoria)
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final String issuer;
    private final long expirationSeconds;

    public JwtService(@Value("${auth.jwt.secret}") String base64Secret,
                      @Value("${auth.jwt.issuer}") String issuer,
                      @Value("${auth.jwt.expiration-seconds}") long expirationSeconds) {
        byte[] decoded = Base64.getDecoder().decode(base64Secret);
        this.signingKey = Keys.hmacShaKeyFor(decoded);
        this.issuer = issuer;
        this.expirationSeconds = expirationSeconds;
    }

    public String issue(String clientId, String institutionId, String scope) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(clientId)
                .issuer(issuer)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .claims(Map.of(
                        "inst", institutionId,
                        "scope", scope == null ? "" : scope
                ))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public long expirationSeconds() {
        return expirationSeconds;
    }
}
