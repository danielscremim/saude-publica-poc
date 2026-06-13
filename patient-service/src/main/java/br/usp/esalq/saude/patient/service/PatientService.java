package br.usp.esalq.saude.patient.service;

import br.usp.esalq.saude.patient.dto.CreatePatientRequest;
import br.usp.esalq.saude.patient.dto.PatientResponse;
import br.usp.esalq.saude.patient.entity.Patient;
import br.usp.esalq.saude.patient.repository.PatientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PatientService {

    private final PatientRepository repository;

    public PatientService(PatientRepository repository) {
        this.repository = repository;
    }

    /**
     * Cadastra (ou recupera) um paciente a partir do CPF e devolve o UUID interno.
     * O CPF -> UUID e a tokenizacao central do RNF-06: o CPF fica restrito a este
     * servico e jamais e devolvido ao exterior.
     */
    @Transactional
    public PatientResponse register(CreatePatientRequest req) {
        Patient patient = repository.findByCpf(req.cpf())
                .orElseGet(() -> repository.save(
                        new Patient(UUID.randomUUID(), req.cpf(), req.name(), req.birthDate())));
        return toResponse(patient);
    }

    @Transactional(readOnly = true)
    public PatientResponse findByUuid(UUID uuid) {
        return repository.findById(uuid)
                .map(this::toResponse)
                .orElseThrow(() -> new IllegalArgumentException("Paciente nao encontrado: " + uuid));
    }

    private PatientResponse toResponse(Patient p) {
        // Nunca inclui o CPF na resposta.
        return new PatientResponse(p.getUuid(), p.getName(), p.getBirthDate());
    }
}
