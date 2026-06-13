package br.usp.esalq.saude.notification.service;

import br.usp.esalq.saude.notification.dto.NotificationResponse;
import br.usp.esalq.saude.notification.dto.SendNotificationRequest;
import br.usp.esalq.saude.notification.entity.Notification;
import br.usp.esalq.saude.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Persiste a notificacao e a "envia" pelo canal escolhido. Hoje apenas LOG;
 * EMAIL/WEBHOOK ficam preparados (basta novo case + integracao).
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public NotificationResponse send(SendNotificationRequest req) {
        Notification n = new Notification(UUID.randomUUID(),
                req.patientUuid(), req.channel(), req.subject(), req.message());
        repository.save(n);
        dispatch(n);
        repository.save(n);
        return toResponse(n);
    }

    /** Cria + dispara uma notificacao a partir de um evento interno. */
    @Transactional
    public NotificationResponse fromEvent(UUID patientUuid, String channel,
                                          String subject, String message) {
        return send(new SendNotificationRequest(patientUuid, channel, subject, message));
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> byPatient(UUID patientUuid) {
        return repository.findByPatientUuidOrderByCreatedAtDesc(patientUuid)
                .stream().map(NotificationService::toResponse).toList();
    }

    private void dispatch(Notification n) {
        switch (n.getChannel().toUpperCase()) {
            case "LOG" -> {
                log.info("[NOTIFICACAO][paciente={}] {}: {}",
                        n.getPatientUuid(), n.getSubject(), n.getMessage());
                n.markSent();
            }
            case "EMAIL", "SMS", "WEBHOOK" -> {
                // Integracao real ficara para versao futura.
                log.info("[NOTIFICACAO][canal {} ainda nao integrado][paciente={}] {}",
                        n.getChannel(), n.getPatientUuid(), n.getSubject());
                n.markSent();
            }
            default -> {
                log.warn("Canal desconhecido: {}", n.getChannel());
                n.markFailed();
            }
        }
    }

    private static NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getPatientUuid(), n.getChannel(),
                n.getSubject(), n.getMessage(), n.getStatus(), n.getCreatedAt(), n.getSentAt());
    }
}
