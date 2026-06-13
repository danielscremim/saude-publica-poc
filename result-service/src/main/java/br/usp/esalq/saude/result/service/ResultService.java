package br.usp.esalq.saude.result.service;

import br.usp.esalq.saude.result.dto.CreateResultRequest;
import br.usp.esalq.saude.result.dto.ResultResponse;
import br.usp.esalq.saude.result.entity.ExamResult;
import br.usp.esalq.saude.result.event.ExamCompletedEvent;
import br.usp.esalq.saude.result.repository.ExamResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ResultService {

    private static final Logger log = LoggerFactory.getLogger(ResultService.class);
    private static final String EXAM_COMPLETED_TOPIC = "exam.completed";

    private final ExamResultRepository repository;
    private final KafkaTemplate<String, ExamCompletedEvent> kafkaTemplate;
    private final AuditPublisher auditPublisher;

    public ResultService(ExamResultRepository repository,
                         KafkaTemplate<String, ExamCompletedEvent> kafkaTemplate,
                         AuditPublisher auditPublisher) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.auditPublisher = auditPublisher;
    }

    /**
     * Consome exam.completed do fluxo interno (lab-service). Idempotente:
     * se o examId ja existe (re-entrega ou eco do POST externo), ignora.
     */
    @KafkaListener(topics = EXAM_COMPLETED_TOPIC, groupId = "result-service")
    public void onExamCompleted(ExamCompletedEvent event) {
        try {
            ExamResult result = new ExamResult(
                    event.examId(), event.patientUuid(), event.examType(), event.origin(),
                    event.resultValue(), event.resultUnit(), event.completedAt());
            repository.save(result);
            log.info("Resultado armazenado (fluxo interno): examId={}, paciente={}",
                    event.examId(), event.patientUuid());
        } catch (DataIntegrityViolationException dup) {
            log.debug("Resultado ja persistido (idempotencia): examId={}", event.examId());
        }
    }

    /**
     * Recebe resultado de instituicao externa (LAB_PRIVADO, HOSPITAL_PRIVADO, etc.)
     * via POST autenticado (fluxo bidirecional). Apos persistir, publica
     * exam.completed para que notification-service e demais consumidores reajam,
     * e publica audit.events com o requesterId do JWT.
     */
    @Transactional
    public ResultResponse receiveExternal(CreateResultRequest req, String requesterId) {
        UUID examId = req.examId() != null ? req.examId() : UUID.randomUUID();
        Instant completedAt = req.completedAt() != null ? req.completedAt() : Instant.now();

        ExamResult result = new ExamResult(examId, req.patientUuid(), req.examType(),
                req.origin(), req.resultValue(), req.resultUnit(), completedAt);
        repository.save(result);

        ExamCompletedEvent event = new ExamCompletedEvent(
                examId, req.patientUuid(), req.examType(), req.origin(),
                req.resultValue(), req.resultUnit(), completedAt);
        kafkaTemplate.send(EXAM_COMPLETED_TOPIC, req.patientUuid().toString(), event);

        auditPublisher.publish(requesterId, req.patientUuid(), "WRITE_RESULT", req.origin());

        log.info("Resultado externo recebido: examId={}, paciente={}, origem={}, requester={}",
                examId, req.patientUuid(), req.origin(), requesterId);
        return toResponse(result);
    }

    public List<ResultResponse> findByPatient(UUID patientUuid) {
        return repository.findByPatientUuidOrderByCompletedAtDesc(patientUuid)
                .stream()
                .map(ResultService::toResponse)
                .toList();
    }

    private static ResultResponse toResponse(ExamResult r) {
        return new ResultResponse(r.getExamId(), r.getPatientUuid(), r.getExamType(),
                r.getOrigin(), r.getResultValue(), r.getResultUnit(), r.getCompletedAt());
    }
}
