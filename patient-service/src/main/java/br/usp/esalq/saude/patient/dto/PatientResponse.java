package br.usp.esalq.saude.patient.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Resposta publica do paciente. Observe que o CPF NAO esta presente:
 * apenas o UUID interno e exposto externamente (RNF-06 - tokenizacao).
 */
public record PatientResponse(
        UUID uuid,
        String name,
        LocalDate birthDate
) { }
