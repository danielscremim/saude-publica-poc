package br.usp.esalq.saude.consent.controller;

import br.usp.esalq.saude.consent.dto.ConsentCheckResponse;
import br.usp.esalq.saude.consent.dto.ConsentResponse;
import br.usp.esalq.saude.consent.dto.CreateConsentRequest;
import br.usp.esalq.saude.consent.service.ConsentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/consents")
public class ConsentController {

    private final ConsentService service;

    public ConsentController(ConsentService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ConsentResponse> grant(@Valid @RequestBody CreateConsentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.grant(request));
    }

    @DeleteMapping("/{consentId}")
    public ResponseEntity<ConsentResponse> revoke(@PathVariable UUID consentId) {
        return ResponseEntity.ok(service.revoke(consentId));
    }

    /**
     * Endpoint usado pelo history-service antes de qualquer leitura clinica.
     * RNF-06: deve responder em <= 20ms (consulta indexada).
     */
    @GetMapping("/check")
    public ResponseEntity<ConsentCheckResponse> check(
            @RequestParam UUID patientUuid,
            @RequestParam String institutionId) {
        return ResponseEntity.ok(service.check(patientUuid, institutionId));
    }

    @GetMapping("/patient/{patientUuid}")
    public ResponseEntity<List<ConsentResponse>> listByPatient(@PathVariable UUID patientUuid) {
        return ResponseEntity.ok(service.listByPatient(patientUuid));
    }
}
