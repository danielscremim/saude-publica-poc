package br.usp.esalq.saude.result.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.UUID;

/**
 * Resultado enviado por uma instituicao externa (LAB_PRIVADO, HOSPITAL_PRIVADO, LAB_PUBLICO).
 * examId pode ser omitido para casos onde o resultado nao tem solicitacao previa
 * (ex: hospital privado enviando um resultado pre-existente).
 */
public record CreateResultRequest(
        UUID examId,
        @NotNull UUID patientUuid,
        @NotBlank String examType,
        @NotBlank
        @Pattern(regexp = "UBS|LAB_PUBLICO|LAB_PRIVADO|HOSPITAL_PRIVADO",
                 message = "origin deve ser UBS, LAB_PUBLICO, LAB_PRIVADO ou HOSPITAL_PRIVADO")
        String origin,
        double resultValue,
        @NotBlank String resultUnit,
        Instant completedAt
) { }
