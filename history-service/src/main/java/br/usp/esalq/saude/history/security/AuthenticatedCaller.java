package br.usp.esalq.saude.history.security;

/**
 * Dados do JWT validado, anexados ao request pelo JwtAuthFilter.
 * Recuperado via request.getAttribute(JwtAuthFilter.CALLER_ATTR).
 */
public record AuthenticatedCaller(String clientId, String institutionId, String scope) {

    public boolean hasScope(String required) {
        if (scope == null || scope.isBlank()) return false;
        for (String s : scope.split("\\s+")) {
            if (s.equals(required)) return true;
        }
        return false;
    }
}
