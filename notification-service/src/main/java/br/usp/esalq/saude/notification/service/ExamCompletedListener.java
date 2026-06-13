package br.usp.esalq.saude.notification.service;

import br.usp.esalq.saude.notification.event.ExamCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Reage a exam.completed transformando em notificacao para o paciente.
 * Sem retry custom: em falha, o offset nao avanca e a notificacao sera tentada de novo.
 */
@Component
public class ExamCompletedListener {

    private static final Logger log = LoggerFactory.getLogger(ExamCompletedListener.class);

    private final NotificationService notificationService;

    public ExamCompletedListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = "exam.completed", groupId = "notification-service")
    public void onExamCompleted(ExamCompletedEvent event) {
        String subject = "Resultado do exame " + event.examType() + " disponivel";
        String message = String.format(
                "Seu exame de %s (origem %s) ficou pronto: %s %s. Consulte o historico para detalhes.",
                event.examType(), event.origin(), event.resultValue(), event.resultUnit());
        notificationService.fromEvent(event.patientUuid(), "LOG", subject, message);
        log.debug("Notificacao gerada para examId={}", event.examId());
    }
}
