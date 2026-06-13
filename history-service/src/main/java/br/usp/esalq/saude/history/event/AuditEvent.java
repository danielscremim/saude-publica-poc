package br.usp.esalq.saude.history.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento Kafka publicado em audit.events por todos os servicos de dados.
 * Consumido pelo audit-service (a ser implementado) para log imutavel + anomaly detection.
 *
 *   requesterId   = sub do JWT (clientId)
 *   sourceService = nome do servico que registrou o acesso
 *   action        = operacao (ex: READ_TIMELINE)
 *   purpose       = finalidade declarada (ex: TREATMENT, RESEARCH)
 */
public record AuditEvent(
        UUID eventId,
        String requesterId,
        UUID patientUuid,
        String action,
        String purpose,
        String sourceService,
        Instant timestamp
) { }
