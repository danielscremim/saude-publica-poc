package br.usp.esalq.saude.result.repository;

import br.usp.esalq.saude.result.entity.ExamResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExamResultRepository extends JpaRepository<ExamResult, UUID> {
    List<ExamResult> findByPatientUuidOrderByCompletedAtDesc(UUID patientUuid);
}
