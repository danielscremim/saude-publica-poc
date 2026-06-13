package br.usp.esalq.saude.auth.controller;

import br.usp.esalq.saude.auth.dto.TokenRequest;
import br.usp.esalq.saude.auth.dto.TokenResponse;
import br.usp.esalq.saude.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/auth")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    /** OAuth2 Client Credentials (simplificado para PoC: aceita JSON). */
    @PostMapping("/token")
    public ResponseEntity<TokenResponse> token(@Valid @RequestBody TokenRequest request) {
        return ResponseEntity.ok(service.issueToken(request));
    }
}
