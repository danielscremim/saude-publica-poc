package br.usp.esalq.saude.notification.controller;

import br.usp.esalq.saude.notification.dto.NotificationResponse;
import br.usp.esalq.saude.notification.dto.SendNotificationRequest;
import br.usp.esalq.saude.notification.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<NotificationResponse> send(@Valid @RequestBody SendNotificationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.send(request));
    }

    @GetMapping("/patient/{patientUuid}")
    public ResponseEntity<List<NotificationResponse>> byPatient(@PathVariable UUID patientUuid) {
        return ResponseEntity.ok(service.byPatient(patientUuid));
    }
}
