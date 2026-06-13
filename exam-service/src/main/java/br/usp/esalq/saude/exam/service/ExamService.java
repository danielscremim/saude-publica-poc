package br.usp.esalq.saude.exam.service;

import br.usp.esalq.saude.exam.config.KafkaTopicConfig;
import br.usp.esalq.saude.exam.dto.CreateExamRequest;
import br.usp.esalq.saude.exam.dto.ExamResponse;
import br.usp.esalq.saude.exam.entity.ExamRequest;
import br.usp.esalq.saude.exam.event.ExamRequestedEvent;
import br.usp.esalq.saude.exam.repository.ExamRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ExamService {

    private static final Logger log = LoggerFactory.getLogger(ExamService.class);

    private final ExamRequestRepository repository;
    private final KafkaTemplate<String, ExamRequestedEvent> kafkaTemplate;

    public ExamService(ExamRequestRepository repository,
                       KafkaTemplate<String, ExamRequestedEvent> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Persiste a solicitacao de exame e publica o evento exam.requested no Kafka,
     * desacoplando o registro do exame do seu processamento laboratorial.
     */
    @Transactional
    public ExamResponse requestExam(CreateExamRequest req) {
        ExamRequest exam = new ExamRequest(
                UUID.randomUUID(), req.patientUuid(), req.examType(), req.origin());
        repository.save(exam);

        ExamRequestedEvent event = new ExamRequestedEvent(
                exam.getExamId(), exam.getPatientUuid(), exam.getExamType(),
                exam.getOrigin(), exam.getRequestedAt());

        // A chave do Kafka e o patientUuid: garante ordenacao por paciente.
        kafkaTemplate.send(KafkaTopicConfig.EXAM_REQUESTED_TOPIC,
                exam.getPatientUuid().toString(), event);
        log.info("Evento publicado em {}: examId={}", KafkaTopicConfig.EXAM_REQUESTED_TOPIC, exam.getExamId());

        return toResponse(exam);
    }

    private ExamResponse toResponse(ExamRequest e) {
        return new ExamResponse(e.getExamId(), e.getPatientUuid(), e.getExamType(),
                e.getOrigin(), e.getStatus(), e.getRequestedAt());
    }
}
