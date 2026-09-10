package br.usp.esalq.saude.history;

import br.usp.esalq.saude.history.client.ConsentClient;
import br.usp.esalq.saude.history.client.PatientClient;
import br.usp.esalq.saude.history.client.ResultClient;
import br.usp.esalq.saude.history.dto.ConsentCheckDto;
import br.usp.esalq.saude.history.dto.PatientDto;
import br.usp.esalq.saude.history.dto.ResultDto;
import br.usp.esalq.saude.history.service.AuditPublisher;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Teste de integracao do transporte GraphQL: sobe o servico em porta aleatoria e
 * exercita o caminho real HTTP -> JwtAuthFilter -> CallerContextInterceptor ->
 * resolvers -> TimelineService (com os clients a montante mockados).
 * Cobre: consulta seletiva, filtros de campo, consent negado (FORBIDDEN),
 * ausencia de token (401), limite de profundidade e paridade com o REST.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class HistoryGraphQLTest {

    @LocalServerPort int port;
    @Value("${auth.jwt.secret}") String secret;
    @Value("${auth.jwt.issuer}") String issuer;

    @MockBean PatientClient patientClient;
    @MockBean ResultClient resultClient;
    @MockBean ConsentClient consentClient;
    @MockBean AuditPublisher auditPublisher;   // evita Kafka real

    final UUID patient = UUID.randomUUID();
    String token;

    @BeforeEach
    void setUp() {
        token = Jwts.builder()
                .subject("hospital-privado-x")
                .issuer(issuer)
                .claim("inst", "HOSP-X")
                .claim("scope", "history:read:own_patients")
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret)))
                .compact();

        when(patientClient.findByUuid(patient))
                .thenReturn(new PatientDto(patient, "Maria Silva", LocalDate.of(1985, 3, 12)));
        when(resultClient.findByPatient(patient)).thenReturn(List.of(
                new ResultDto(UUID.randomUUID(), patient, "GLICEMIA", "UBS", 98.5, "mg/dL", Instant.parse("2026-01-10T10:00:00Z")),
                new ResultDto(UUID.randomUUID(), patient, "COLESTEROL", "LAB_PRIVADO", 190.0, "mg/dL", Instant.parse("2026-03-05T10:00:00Z")),
                new ResultDto(UUID.randomUUID(), patient, "GLICEMIA", "HOSPITAL_PRIVADO", 110.2, "mg/dL", Instant.parse("2026-06-01T10:00:00Z"))));
    }

    private HttpGraphQlTester tester(String bearer) {
        WebTestClient.Builder b = WebTestClient.bindToServer().baseUrl("http://localhost:" + port + "/graphql");
        if (bearer != null) b = b.defaultHeader("Authorization", "Bearer " + bearer);
        return HttpGraphQlTester.create(b.build());
    }

    @Test
    void consultaSeletivaRetornaApenasCamposPedidos_eOrdenaMaisRecentePrimeiro() {
        when(consentClient.check(eq(patient), eq("HOSP-X")))
                .thenReturn(new ConsentCheckDto(patient, "HOSP-X", true, "history:read:own_patients"));

        tester(token).document("""
                query($id: ID!) { patientHistory(patientUuid: $id, purpose: "TREATMENT") {
                  totalExams exams { examType resultValue completedAt } } }""")
                .variable("id", patient.toString())
                .execute()
                .path("patientHistory.totalExams").entity(Integer.class).isEqualTo(3)
                .path("patientHistory.exams[0].examType").entity(String.class).isEqualTo("GLICEMIA")
                .path("patientHistory.exams[0].resultValue").entity(Double.class).isEqualTo(110.2)
                .path("patientHistory.exams[0].completedAt").entity(String.class).isEqualTo("2026-06-01T10:00:00Z")
                // campo nao pedido nao deve existir na resposta (evita over-fetching)
                .path("patientHistory.exams[0].origin").pathDoesNotExist();

        verify(auditPublisher).publish(eq("hospital-privado-x"), eq(patient), eq("READ_TIMELINE"), eq("TREATMENT"));
    }

    @Test
    void filtroPorTipoELimiteNoCampoExams() {
        when(consentClient.check(any(), any()))
                .thenReturn(new ConsentCheckDto(patient, "HOSP-X", true, "x"));

        tester(token).document("""
                query($id: ID!) { patientHistory(patientUuid: $id) {
                  exams(examType: "GLICEMIA", limit: 1) { examType origin } } }""")
                .variable("id", patient.toString())
                .execute()
                .path("patientHistory.exams").entityList(Object.class).hasSize(1)
                .path("patientHistory.exams[0].origin").entity(String.class).isEqualTo("HOSPITAL_PRIVADO");
    }

    @Test
    void semConsentimento_retornaErroFORBIDDEN_eAuditaNegacao() {
        when(consentClient.check(any(), any()))
                .thenReturn(new ConsentCheckDto(patient, "HOSP-X", false, null));

        tester(token).document("""
                query($id: ID!) { patientHistory(patientUuid: $id) { totalExams } }""")
                .variable("id", patient.toString())
                .execute()
                .errors().satisfy(errors -> {
                    assertThat(errors).hasSize(1);
                    assertThat(errors.get(0).getErrorType().toString()).isEqualTo("FORBIDDEN");
                    assertThat(errors.get(0).getMessage()).startsWith("consent_denied");
                });

        verify(auditPublisher).publish(any(), eq(patient), eq("READ_TIMELINE_DENIED"), any());
    }

    @Test
    void semToken_retorna401AntesDeChegarAoGraphQL() {
        WebTestClient client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        client.post().uri("/graphql")
                .header("Content-Type", "application/json")
                .bodyValue("{\"query\":\"{ __typename }\"}")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void queryComProfundidadeAcimaDoLimite_eRejeitada() {
        when(consentClient.check(any(), any()))
                .thenReturn(new ConsentCheckDto(patient, "HOSP-X", true, "x"));

        // Introspeccao aninhada: __schema.types.fields.type.fields.type... (profundidade > 5)
        tester(token).document("""
                { __schema { types { fields { type { fields { type { fields { name } } } } } } } }""")
                .execute()
                .errors().satisfy(errors -> {
                    assertThat(errors).isNotEmpty();
                    assertThat(errors.get(0).getMessage()).containsIgnoringCase("depth");
                });
    }

    @Test
    void endpointRESTContinuaFuncionando_paridadeDeContrato() {
        when(consentClient.check(any(), any()))
                .thenReturn(new ConsentCheckDto(patient, "HOSP-X", true, "x"));

        WebTestClient client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        client.get().uri("/v1/patients/" + patient + "/clinical-timeline?purpose=TREATMENT")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalExams").isEqualTo(3)
                .jsonPath("$.exams[0].examType").isEqualTo("GLICEMIA")
                .jsonPath("$.exams[0].origin").exists();   // REST devolve TODOS os campos (over-fetching)
    }
}
