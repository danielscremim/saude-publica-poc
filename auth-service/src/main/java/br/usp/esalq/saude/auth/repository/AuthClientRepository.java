package br.usp.esalq.saude.auth.repository;

import br.usp.esalq.saude.auth.entity.AuthClient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AuthClientRepository extends JpaRepository<AuthClient, UUID> {
    Optional<AuthClient> findByClientId(String clientId);
}
