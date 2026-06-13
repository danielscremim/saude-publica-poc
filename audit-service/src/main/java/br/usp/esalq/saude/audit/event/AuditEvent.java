package br.usp.esalq.saude.audit.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Espelho do evento publicado em audit.events por todos os servicos de dados.
 * O record e duplicado em cada servico (convencao do projeto - ver CLAUDE.md).
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
