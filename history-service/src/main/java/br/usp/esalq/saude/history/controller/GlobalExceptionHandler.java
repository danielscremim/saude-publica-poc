package br.usp.esalq.saude.history.controller;

import br.usp.esalq.saude.history.service.TimelineService.ConsentDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Sem consent ativo -> HTTP 403 (RNF-06: inegociavel). */
    @ExceptionHandler(ConsentDeniedException.class)
    public ResponseEntity<Map<String, String>> handleConsentDenied(ConsentDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "consent_denied", "error_description", ex.getMessage()));
    }

    /** Propaga 404 do patient-service (paciente inexistente) como 404 nosso. */
    @ExceptionHandler(HttpClientErrorException.NotFound.class)
    public ResponseEntity<Map<String, String>> handleDownstreamNotFound(HttpClientErrorException.NotFound ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "not_found"));
    }
}
