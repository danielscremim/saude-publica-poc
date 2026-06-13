package br.usp.esalq.saude.result.controller;

import br.usp.esalq.saude.result.controller.ResultController.ScopeMissingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ScopeMissingException.class)
    public ResponseEntity<Map<String, String>> handleScopeMissing(ScopeMissingException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "insufficient_scope", "error_description", ex.getMessage()));
    }
}
