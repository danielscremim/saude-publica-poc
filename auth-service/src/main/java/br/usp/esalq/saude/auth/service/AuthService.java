package br.usp.esalq.saude.auth.service;

import br.usp.esalq.saude.auth.dto.RegisterClientRequest;
import br.usp.esalq.saude.auth.dto.RegisterClientResponse;
import br.usp.esalq.saude.auth.dto.TokenRequest;
import br.usp.esalq.saude.auth.dto.TokenResponse;
import br.usp.esalq.saude.auth.entity.AuthClient;
import br.usp.esalq.saude.auth.repository.AuthClientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthClientRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(AuthClientRepository repository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * Autentica o client (client_id + client_secret) e emite um JWT.
     * Se o request pedir um escopo, devolvemos somente a intersecao com os
     * escopos cadastrados (downgrade-only, padrao OAuth2).
     */
    @Transactional(readOnly = true)
    public TokenResponse issueToken(TokenRequest req) {
        AuthClient client = repository.findByClientId(req.clientId())
                .orElseThrow(() -> new InvalidCredentialsException("Credenciais invalidas"));

        if (!passwordEncoder.matches(req.clientSecret(), client.getClientSecretHash())) {
            log.warn("Tentativa de autenticacao falhou para clientId={}", req.clientId());
            throw new InvalidCredentialsException("Credenciais invalidas");
        }

        String grantedScope = intersect(client.getScopes(), req.scope());
        String token = jwtService.issue(client.getClientId(), client.getInstitutionId(), grantedScope);
        log.info("Token emitido: clientId={}, institutionId={}", client.getClientId(), client.getInstitutionId());

        return new TokenResponse(token, "Bearer", jwtService.expirationSeconds(), grantedScope);
    }

    @Transactional
    public RegisterClientResponse registerClient(RegisterClientRequest req) {
        repository.findByClientId(req.clientId()).ifPresent(c -> {
            throw new IllegalArgumentException("clientId ja registrado: " + req.clientId());
        });
        AuthClient client = new AuthClient(
                UUID.randomUUID(),
                req.clientId(),
                passwordEncoder.encode(req.clientSecret()),
                req.institutionId(),
                req.scopes()
        );
        repository.save(client);
        log.info("Client cadastrado: clientId={}, institutionId={}", client.getClientId(), client.getInstitutionId());
        return new RegisterClientResponse(client.getId(), client.getClientId(),
                client.getInstitutionId(), client.getScopes());
    }

    /** Retorna apenas os escopos solicitados que tambem estao cadastrados. */
    private String intersect(String registered, String requested) {
        if (requested == null || requested.isBlank()) {
            return registered;
        }
        Set<String> reg = new LinkedHashSet<>(Arrays.asList(registered.split("\\s+")));
        return Arrays.stream(requested.split("\\s+"))
                .filter(reg::contains)
                .collect(Collectors.joining(" "));
    }

    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException(String msg) { super(msg); }
    }
}
