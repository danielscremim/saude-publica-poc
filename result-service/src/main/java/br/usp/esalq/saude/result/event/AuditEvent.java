package br.usp.esalq.saude.result.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento publicado em audit.events por todos os servicos de dados (RNF-03).
 * Record duplicado por servico (convencao do projeto - ver CLAUDE.md).
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
