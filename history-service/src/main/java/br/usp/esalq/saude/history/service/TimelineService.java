package br.usp.esalq.saude.history.service;

import br.usp.esalq.saude.history.client.ConsentClient;
import br.usp.esalq.saude.history.client.PatientClient;
import br.usp.esalq.saude.history.client.ResultClient;
import br.usp.esalq.saude.history.dto.ConsentCheckDto;
import br.usp.esalq.saude.history.dto.PatientDto;
import br.usp.esalq.saude.history.dto.ResultDto;
import br.usp.esalq.saude.history.dto.TimelineEntry;
import br.usp.esalq.saude.history.dto.TimelineResponse;
import br.usp.esalq.saude.history.security.AuthenticatedCaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Logica central do history-service: checagem de consent OBRIGATORIA antes de
 * qualquer leitura, agregacao patient+result, publicacao de evento de auditoria.
 *
 * Fluxo:
 *   1. Verifica consent (paciente x instituicao do JWT) -> 403 se negado.
 *   2. Agrega patient + results em paralelo seria o ideal; aqui sequencial pela PoC.
 *   3. Publica audit.events.
 *   4. Devolve a timeline (mais recentes primeiro).
 */
@Service
public class TimelineService {

    private static final Logger log = LoggerFactory.getLogger(TimelineService.class);

    private final PatientClient patientClient;
    private final ResultClient resultClient;
    private final ConsentClient consentClient;
    private final AuditPublisher auditPublisher;

    public TimelineService(PatientClient patientClient,
                           ResultClient resultClient,
                           ConsentClient consentClient,
                           AuditPublisher auditPublisher) {
        this.patientClient = patientClient;
        this.resultClient = resultClient;
        this.consentClient = consentClient;
        this.auditPublisher = auditPublisher;
    }

    public TimelineResponse getTimeline(UUID patientUuid, AuthenticatedCaller caller, String purpose) {
        ConsentCheckDto consent = consentClient.check(patientUuid, caller.institutionId());
        if (consent == null || !consent.granted()) {
            log.warn("Acesso negado por falta de consentimento: paciente={}, instituicao={}",
                    patientUuid, caller.institutionId());
            // Auditoria do ACESSO_NEGADO tambem importa para anomaly detection (RNF-06).
            auditPublisher.publish(caller.clientId(), patientUuid, "READ_TIMELINE_DENIED",
                    nullSafe(purpose));
            throw new ConsentDeniedException(
                    "Sem consentimento ativo do paciente para a instituicao " + caller.institutionId());
        }

        PatientDto patient = patientClient.findByUuid(patientUuid);
        List<ResultDto> results = resultClient.findByPatient(patientUuid);

        List<TimelineEntry> entries = results.stream()
                .map(r -> new TimelineEntry(r.examId(), r.examType(), r.origin(),
                        r.resultValue(), r.resultUnit(), r.completedAt()))
                .sorted(Comparator.comparing(TimelineEntry::completedAt).reversed())
                .toList();

        auditPublisher.publish(caller.clientId(), patientUuid, "READ_TIMELINE", nullSafe(purpose));

        return new TimelineResponse(patientUuid, patient, entries, entries.size());
    }

    private static String nullSafe(String s) {
        return s == null || s.isBlank() ? "UNSPECIFIED" : s;
    }

    public static class ConsentDeniedException extends RuntimeException {
        public ConsentDeniedException(String msg) { super(msg); }
    }
}
