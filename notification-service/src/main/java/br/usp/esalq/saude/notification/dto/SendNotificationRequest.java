package br.usp.esalq.saude.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Envio manual de notificacao (uso administrativo/teste). */
public record SendNotificationRequest(
        @NotNull UUID patientUuid,
        @NotBlank String channel,
        @NotBlank String subject,
        @NotBlank String message
) { }
