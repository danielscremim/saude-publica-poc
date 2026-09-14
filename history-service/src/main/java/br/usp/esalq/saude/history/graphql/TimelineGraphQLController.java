package br.usp.esalq.saude.history.graphql;

import br.usp.esalq.saude.history.client.AuditClient;
import br.usp.esalq.saude.history.client.NotificationClient;
import br.usp.esalq.saude.history.client.TriageClient;
import br.usp.esalq.saude.history.dto.AuditLogDto;
import br.usp.esalq.saude.history.dto.NotificationDto;
import br.usp.esalq.saude.history.dto.PatientView;
import br.usp.esalq.saude.history.dto.TimelineEntry;
import br.usp.esalq.saude.history.dto.TriageDto;
import br.usp.esalq.saude.history.security.AuthenticatedCaller;
import br.usp.esalq.saude.history.service.TimelineService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.ContextValue;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Fachada GraphQL (BFF) da camada de distribuicao.
 *
 * A query raiz valida o consent UMA VEZ (no TimelineService) e agrega paciente +
 * exames. Triagens, notificacoes e trilha de auditoria sao resolvidas SOB DEMANDA:
 * se o consumidor nao pedir o campo, o resolver nao executa e o servico a montante
 * nao e chamado — minimizacao de dados na camada de protocolo (LGPD Art. 6, III).
 *
 * Consequencia pratica: uma visao consolidada do paciente que exigiria varias
 * chamadas REST separadas e obtida em uma unica requisicao, reduzindo os pontos
 * de integracao do consumidor externo.
 */
@Controller
public class TimelineGraphQLController {

    private final TimelineService service;
    private final TriageClient triageClient;
    private final NotificationClient notificationClient;
    private final AuditClient auditClient;

    public TimelineGraphQLController(TimelineService service,
                                     TriageClient triageClient,
                                     NotificationClient notificationClient,
                                     AuditClient auditClient) {
        this.service = service;
        this.triageClient = triageClient;
        this.notificationClient = notificationClient;
        this.auditClient = auditClient;
    }

    @QueryMapping
    public PatientView patientHistory(@Argument String patientUuid,
                                      @Argument String purpose,
                                      @ContextValue(name = CallerContextInterceptor.CALLER_KEY, required = false)
                                      AuthenticatedCaller caller) {
        if (caller == null) {
            throw new UnauthenticatedException("missing_token");
        }
        return service.getPatientView(UUID.fromString(patientUuid), caller, purpose);
    }

    /** Exames ja agregados na query raiz; filtros aplicados em memoria (sem N+1). */
    @SchemaMapping(typeName = "PatientView", field = "exams")
    public List<TimelineEntry> exams(PatientView view,
                                     @Argument String examType,
                                     @Argument Integer limit) {
        Stream<TimelineEntry> s = view.exams().stream();
        if (notBlank(examType)) s = s.filter(e -> e.examType().equalsIgnoreCase(examType));
        return limited(s, limit);
    }

    /** Buscado no triage-service APENAS se o campo for solicitado. */
    @SchemaMapping(typeName = "PatientView", field = "triages")
    public List<TriageDto> triages(PatientView view,
                                   @Argument String priority,
                                   @Argument Integer limit) {
        List<TriageDto> all = orEmpty(triageClient.findByPatient(view.patientUuid()));
        Stream<TriageDto> s = all.stream()
                .sorted(Comparator.comparing(TriageDto::performedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())));
        if (notBlank(priority)) s = s.filter(t -> priority.equalsIgnoreCase(t.priority()));
        return limited(s, limit);
    }

    /** Buscado no notification-service APENAS se o campo for solicitado. */
    @SchemaMapping(typeName = "PatientView", field = "notifications")
    public List<NotificationDto> notifications(PatientView view,
                                               @Argument String channel,
                                               @Argument Integer limit) {
        List<NotificationDto> all = orEmpty(notificationClient.findByPatient(view.patientUuid()));
        Stream<NotificationDto> s = all.stream()
                .sorted(Comparator.comparing(NotificationDto::createdAt,
                        Comparator.nullsLast(Comparator.reverseOrder())));
        if (notBlank(channel)) s = s.filter(n -> channel.equalsIgnoreCase(n.channel()));
        return limited(s, limit);
    }

    /**
     * Trilha de acesso aos dados do paciente (transparencia LGPD: "quem acessou
     * meus dados?"). Buscada no audit-service APENAS se o campo for solicitado.
     */
    @SchemaMapping(typeName = "PatientView", field = "auditTrail")
    public List<AuditLogDto> auditTrail(PatientView view,
                                        @Argument String action,
                                        @Argument Integer limit) {
        List<AuditLogDto> all = orEmpty(auditClient.findByPatient(view.patientUuid()));
        Stream<AuditLogDto> s = all.stream()
                .sorted(Comparator.comparing(AuditLogDto::timestamp,
                        Comparator.nullsLast(Comparator.reverseOrder())));
        if (notBlank(action)) s = s.filter(a -> action.equalsIgnoreCase(a.action()));
        return limited(s, limit);
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

    private static <T> List<T> orEmpty(List<T> list) { return list == null ? List.of() : list; }

    private static <T> List<T> limited(Stream<T> s, Integer limit) {
        return (limit != null && limit > 0 ? s.limit(limit) : s).toList();
    }

    public static class UnauthenticatedException extends RuntimeException {
        public UnauthenticatedException(String msg) { super(msg); }
    }
}
