package br.usp.esalq.saude.history.controller;

import br.usp.esalq.saude.history.dto.TimelineResponse;
import br.usp.esalq.saude.history.security.AuthenticatedCaller;
import br.usp.esalq.saude.history.security.JwtAuthFilter;
import br.usp.esalq.saude.history.service.TimelineService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/patients")
public class TimelineController {

    private final TimelineService service;

    public TimelineController(TimelineService service) {
        this.service = service;
    }

    /**
     * Linha do tempo clinica do paciente. Requer JWT valido E consentimento ativo
     * da instituicao do JWT para o paciente (RNF-06).
     *
     * @param purpose finalidade declarada (TREATMENT, RESEARCH, etc.); vai para auditoria.
     */
    @GetMapping("/{patientUuid}/clinical-timeline")
    public ResponseEntity<TimelineResponse> getTimeline(
            @PathVariable UUID patientUuid,
            @RequestParam(required = false) String purpose,
            HttpServletRequest request) {

        AuthenticatedCaller caller = (AuthenticatedCaller) request.getAttribute(JwtAuthFilter.CALLER_ATTR);
        return ResponseEntity.ok(service.getTimeline(patientUuid, caller, purpose));
    }
}
