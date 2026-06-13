package br.usp.esalq.saude.patient.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Entidade Paciente. O CPF e armazenado de forma controlada e NUNCA exposto
 * em respostas de API. O identificador publico do paciente e o UUID interno
 * (tokenizacao - RNF-06).
 */
@Entity
@Table(name = "patients",
       uniqueConstraints = @UniqueConstraint(columnNames = "cpf"))
public class Patient {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID uuid;

    @Column(nullable = false, unique = true)
    private String cpf;

    @Column(nullable = false)
    private String name;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    protected Patient() { }

    public Patient(UUID uuid, String cpf, String name, LocalDate birthDate) {
        this.uuid = uuid;
        this.cpf = cpf;
        this.name = name;
        this.birthDate = birthDate;
    }

    public UUID getUuid() { return uuid; }
    public String getCpf() { return cpf; }
    public String getName() { return name; }
    public LocalDate getBirthDate() { return birthDate; }
}
