package br.usp.esalq.saude.auth.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Cliente OAuth2 (instituicao consumidora da plataforma).
 * O client_secret e armazenado APENAS como hash BCrypt.
 * O campo scopes e uma lista separada por espaco (formato OAuth2 padrao).
 */
@Entity
@Table(name = "auth_clients",
       uniqueConstraints = @UniqueConstraint(columnNames = "client_id"))
public class AuthClient {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "client_id", nullable = false, unique = true)
    private String clientId;

    @Column(name = "client_secret_hash", nullable = false)
    private String clientSecretHash;

    /** Identificador da instituicao (UBS, hospital, laboratorio). */
    @Column(name = "institution_id", nullable = false)
    private String institutionId;

    /** Escopos separados por espaco. Ex: "result:write history:read:own_patients". */
    @Column(nullable = false, length = 1024)
    private String scopes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuthClient() { }

    public AuthClient(UUID id, String clientId, String clientSecretHash,
                      String institutionId, String scopes) {
        this.id = id;
        this.clientId = clientId;
        this.clientSecretHash = clientSecretHash;
        this.institutionId = institutionId;
        this.scopes = scopes;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getClientId() { return clientId; }
    public String getClientSecretHash() { return clientSecretHash; }
    public String getInstitutionId() { return institutionId; }
    public String getScopes() { return scopes; }
    public Instant getCreatedAt() { return createdAt; }
}
