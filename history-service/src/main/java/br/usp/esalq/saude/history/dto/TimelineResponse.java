package br.usp.esalq.saude.history.dto;

import java.util.List;
import java.util.UUID;

/**
 * Resposta consolidada GET /v1/patients/{uuid}/clinical-timeline.
 * Estrutura padronizada para qualquer consumidor autorizado (RNF-05 interoperabilidade).
 */
public record TimelineResponse(
        UUID patientUuid,
        PatientDto patient,
        List<TimelineEntry> exams,
        int totalExams
) { }
