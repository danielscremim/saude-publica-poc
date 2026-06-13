package br.usp.esalq.saude.patient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

/** Dados de entrada para cadastro. O CPF entra aqui mas nunca sai nas respostas. */
public record CreatePatientRequest(
        @NotBlank @Pattern(regexp = "\\d{11}", message = "CPF deve conter 11 digitos")
        String cpf,
        @NotBlank String name,
        LocalDate birthDate
) { }
