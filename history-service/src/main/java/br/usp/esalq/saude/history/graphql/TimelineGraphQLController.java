package br.usp.esalq.saude.history.graphql;

import br.usp.esalq.saude.history.dto.TimelineEntry;
import br.usp.esalq.saude.history.dto.TimelineResponse;
import br.usp.esalq.saude.history.security.AuthenticatedCaller;
import br.usp.esalq.saude.history.service.TimelineService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.UUID;

/**
 * Transporte GraphQL da fachada de historico. Reutiliza o MESMO TimelineService
 * do endpoint REST: mesma checagem de consent, mesma auditoria. A diferenca esta
 * no contrato: o consumidor escolhe os campos (evita over-fetching) e pode
 * filtrar exames por tipo/quantidade sem novos endpoints (RNF-05).
 */
@Controller
public class TimelineGraphQLController {

    private final TimelineService service;

    public TimelineGraphQLController(TimelineService service) {
        this.service = service;
    }

    @QueryMapping
    public TimelineResponse patientHistory(@Argument String patientUuid,
                                           @Argument String purpose,
                                           @ContextValue(name = CallerContextInterceptor.CALLER_KEY, required = false)
                                           AuthenticatedCaller caller) {
        if (caller == null) {
            throw new UnauthenticatedException("missing_token");
        }
        return service.getTimeline(UUID.fromString(patientUuid), caller, purpose);
    }

    /**
     * Resolver do campo Timeline.exams com filtros. Opera sobre a lista ja agregada
     * (uma unica chamada a montante por query) — portanto sem problema N+1.
     */
    @SchemaMapping(typeName = "Timeline", field = "exams")
    public List<TimelineEntry> exams(TimelineResponse timeline,
                                     @Argument String examType,
                                     @Argument Integer limit) {
        var stream = timeline.exams().stream();
        if (examType != null && !examType.isBlank()) {
            stream = stream.filter(e -> e.examType().equalsIgnoreCase(examType));
        }
        if (limit != null && limit > 0) {
            stream = stream.limit(limit);
        }
        return stream.toList();
    }

    public static class UnauthenticatedException extends RuntimeException {
        public UnauthenticatedException(String msg) { super(msg); }
    }
}
