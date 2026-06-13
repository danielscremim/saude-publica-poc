package br.usp.esalq.saude.triage.controller;

import br.usp.esalq.saude.triage.dto.CreateTriageRequest;
import br.usp.esalq.saude.triage.dto.TriageResponse;
import br.usp.esalq.saude.triage.service.TriageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/triages")
public class TriageController {

    private final TriageService service;

    public TriageController(TriageService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<TriageResponse> register(@Valid @RequestBody CreateTriageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.register(request));
    }

    @GetMapping("/patient/{patientUuid}")
    public ResponseEntity<List<TriageResponse>> byPatient(@PathVariable UUID patientUuid) {
        return ResponseEntity.ok(service.byPatient(patientUuid));
    }
}
