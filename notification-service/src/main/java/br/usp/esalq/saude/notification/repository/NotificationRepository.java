package br.usp.esalq.saude.notification.repository;

import br.usp.esalq.saude.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findByPatientUuidOrderByCreatedAtDesc(UUID patientUuid);
}
