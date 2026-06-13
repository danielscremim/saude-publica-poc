package br.usp.esalq.saude.result.controller;

import br.usp.esalq.saude.result.dto.CreateResultRequest;
import br.usp.esalq.saude.result.dto.ResultResponse;
import br.usp.esalq.saude.result.security.AuthenticatedCaller;
import br.usp.esalq.saude.result.security.JwtAuthFilter;
import br.usp.esalq.saude.result.service.ResultService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/results")
public class ResultController {

    private static final String REQUIRED_SCOPE = "result:write";

    private final ResultService service;

    public ResultController(ResultService service) {
        this.service = service;
    }

    /** Retorna o historico de resultados de um paciente (mais recentes primeiro). */
    @GetMapping("/patient/{patientUuid}")
    public ResponseEntity<List<ResultResponse>> getByPatient(@PathVariable UUID patientUuid) {
        return ResponseEntity.ok(service.findByPatient(patientUuid));
    }

    /**
     * Recebimento de resultado de instituicao externa (fluxo bidirecional).
     * Requer JWT valido + scope 'result:write' no token.
     */
    @PostMapping
    public ResponseEntity<ResultResponse> create(@Valid @RequestBody CreateResultRequest request,
                                                  HttpServletRequest httpRequest) {
        AuthenticatedCaller caller =
                (AuthenticatedCaller) httpRequest.getAttribute(JwtAuthFilter.CALLER_ATTR);
        if (caller == null || !caller.hasScope(REQUIRED_SCOPE)) {
            throw new ScopeMissingException("scope '" + REQUIRED_SCOPE + "' obrigatorio");
        }
        ResultResponse response = service.receiveExternal(request, caller.clientId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    public static class ScopeMissingException extends RuntimeException {
        public ScopeMissingException(String msg) { super(msg); }
    }
}
