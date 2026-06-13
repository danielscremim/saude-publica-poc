package br.usp.esalq.saude.consent.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento Kafka publicado quando um paciente revoga o consentimento dado a uma
 * instituicao. Consumido por todos os servicos de dados para invalidacao em
 * cascata (RNF-06: revogacao propagada em <= 1s).
 */
public record ConsentRevokedEvent(
        UUID patientUuid,
        String institutionId,
        Instant revokedAt
) { }
