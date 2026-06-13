package br.usp.esalq.saude.lab.service;

import br.usp.esalq.saude.lab.config.KafkaTopicConfig;
import br.usp.esalq.saude.lab.event.ExamCompletedEvent;
import br.usp.esalq.saude.lab.event.ExamRequestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Consome o topico exam.requested, simula o processamento laboratorial do exame
 * e publica o resultado no topico exam.completed.
 */
@Service
public class LabProcessingService {

    private static final Logger log = LoggerFactory.getLogger(LabProcessingService.class);

    private final KafkaTemplate<String, ExamCompletedEvent> kafkaTemplate;

    public LabProcessingService(KafkaTemplate<String, ExamCompletedEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = KafkaTopicConfig.EXAM_REQUESTED_TOPIC, groupId = "lab-service")
    public void onExamRequested(ExamRequestedEvent event) {
        log.info("Recebido exam.requested: examId={}, tipo={}", event.examId(), event.examType());

        // Simula o resultado do exame (em um cenario real, integraria com equipamento/LIS).
        double value = generateResultFor(event.examType());
        String unit = unitFor(event.examType());

        ExamCompletedEvent completed = new ExamCompletedEvent(
                event.examId(), event.patientUuid(), event.examType(),
                event.origin(), value, unit, Instant.now());

        kafkaTemplate.send(KafkaTopicConfig.EXAM_COMPLETED_TOPIC,
                event.patientUuid().toString(), completed);
        log.info("Publicado exam.completed: examId={}, resultado={} {}", event.examId(), value, unit);
    }

    private double generateResultFor(String examType) {
        return switch (examType.toUpperCase()) {
            case "GLICEMIA" -> round(ThreadLocalRandom.current().nextDouble(70, 200));
            case "HEMOGLOBINA" -> round(ThreadLocalRandom.current().nextDouble(11, 17));
            case "COLESTEROL" -> round(ThreadLocalRandom.current().nextDouble(120, 280));
            default -> round(ThreadLocalRandom.current().nextDouble(1, 100));
        };
    }

    private String unitFor(String examType) {
        return switch (examType.toUpperCase()) {
            case "GLICEMIA", "COLESTEROL" -> "mg/dL";
            case "HEMOGLOBINA" -> "g/dL";
            default -> "un";
        };
    }

    private double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
