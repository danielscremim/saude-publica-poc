package br.usp.esalq.saude.history.dto;

import java.time.LocalDate;
import java.util.UUID;

/** Espelho da resposta do patient-service (RNF-06: jamais inclui CPF). */
public record PatientDto(UUID uuid, String name, LocalDate birthDate) { }
