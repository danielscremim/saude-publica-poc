package br.usp.esalq.saude.history.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.util.Base64;

/**
 * Valida o JWT (HS256, mesmo segredo do auth-service) e anexa um AuthenticatedCaller
 * ao request. Sem token valido -> 401. Endpoints de actuator e openapi sao liberados.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String CALLER_ATTR = "history.caller";

    private final SecretKey key;
    private final String issuer;

    public JwtAuthFilter(@Value("${auth.jwt.secret}") String base64Secret,
                         @Value("${auth.jwt.issuer}") String issuer) {
        this.key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(base64Secret));
        this.issuer = issuer;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            unauthorized(response, "missing_token");
            return;
        }

        String token = header.substring("Bearer ".length()).trim();
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String clientId = claims.getSubject();
            String institutionId = claims.get("inst", String.class);
            String scope = claims.get("scope", String.class);

            request.setAttribute(CALLER_ATTR, new AuthenticatedCaller(clientId, institutionId, scope));
            chain.doFilter(request, response);

        } catch (JwtException ex) {
            unauthorized(response, "invalid_token");
        }
    }

    private void unauthorized(HttpServletResponse response, String error) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"" + error + "\"}");
    }
}
