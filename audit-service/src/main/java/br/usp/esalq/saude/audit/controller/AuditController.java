package br.usp.esalq.saude.audit.controller;

import br.usp.esalq.saude.audit.dto.AuditAnomalyResponse;
import br.usp.esalq.saude.audit.dto.AuditLogResponse;
import br.usp.esalq.saude.audit.service.AuditQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Consulta do log imutavel. NAO ha endpoints de UPDATE/DELETE por design (RNF-03).
 */
@RestController
@RequestMapping("/v1/audit")
public class AuditController {

    private final AuditQueryService service;

    public AuditController(AuditQueryService service) {
        this.service = service;
    }

    @GetMapping("/patient/{patientUuid}")
    public ResponseEntity<List<AuditLogResponse>> byPatient(@PathVariable UUID patientUuid) {
        return ResponseEntity.ok(service.byPatient(patientUuid));
    }

    @GetMapping("/requester/{requesterId}")
    public ResponseEntity<List<AuditLogResponse>> byRequester(@PathVariable String requesterId) {
        return ResponseEntity.ok(service.byRequester(requesterId));
    }

    @GetMapping("/anomalies")
    public ResponseEntity<List<AuditAnomalyResponse>> anomalies() {
        return ResponseEntity.ok(service.anomalies());
    }
}
