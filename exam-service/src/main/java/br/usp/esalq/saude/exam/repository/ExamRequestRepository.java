package br.usp.esalq.saude.exam.repository;

import br.usp.esalq.saude.exam.entity.ExamRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ExamRequestRepository extends JpaRepository<ExamRequest, UUID> { }
