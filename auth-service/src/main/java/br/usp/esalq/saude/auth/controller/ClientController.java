package br.usp.esalq.saude.auth.controller;

import br.usp.esalq.saude.auth.dto.RegisterClientRequest;
import br.usp.esalq.saude.auth.dto.RegisterClientResponse;
import br.usp.esalq.saude.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoint administrativo de registro de clients. Em producao seria protegido
 * por mTLS + autenticacao admin; na PoC fica aberto para facilitar o setup.
 */
@RestController
@RequestMapping("/v1/clients")
public class ClientController {

    private final AuthService service;

    public ClientController(AuthService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<RegisterClientResponse> register(
            @Valid @RequestBody RegisterClientRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.registerClient(request));
    }
}
