package br.usp.esalq.saude.exam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateExamRequest(
        @NotNull UUID patientUuid,
        @NotBlank String examType,
        @NotBlank String origin
) { }
