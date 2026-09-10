package br.usp.esalq.saude.history.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Valida o JWT e anexa um AuthenticatedCaller ao request. Sem token valido -> 401.
 * Cobre REST (/v1/...) E GraphQL (/graphql). Actuator, OpenAPI e a UI do GraphiQL
 * sao liberados (a UI envia o header Authorization nas queries que dispara).
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String CALLER_ATTR = "history.caller";

    private final JwtValidator validator;

    public JwtAuthFilter(JwtValidator validator) {
        this.validator = validator;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/graphiql");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = JwtValidator.extractBearer(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (token == null) {
            unauthorized(response, "missing_token");
            return;
        }
        try {
            request.setAttribute(CALLER_ATTR, validator.validate(token));
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
