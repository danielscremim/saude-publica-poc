package br.usp.esalq.saude.exam.controller;

import br.usp.esalq.saude.exam.dto.CreateExamRequest;
import br.usp.esalq.saude.exam.dto.ExamResponse;
import br.usp.esalq.saude.exam.service.ExamService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/exams")
public class ExamController {

    private final ExamService service;

    public ExamController(ExamService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ExamResponse> create(@Valid @RequestBody CreateExamRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.requestExam(request));
    }
}
