package br.usp.esalq.saude.triage.service;

import br.usp.esalq.saude.triage.dto.CreateTriageRequest;
import br.usp.esalq.saude.triage.dto.TriageResponse;
import br.usp.esalq.saude.triage.entity.Triage;
import br.usp.esalq.saude.triage.entity.TriagePriority;
import br.usp.esalq.saude.triage.repository.TriageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TriageService {

    private static final Logger log = LoggerFactory.getLogger(TriageService.class);

    private final TriageRepository repository;
    private final TriagePriorityClassifier classifier;

    public TriageService(TriageRepository repository, TriagePriorityClassifier classifier) {
        this.repository = repository;
        this.classifier = classifier;
    }

    @Transactional
    public TriageResponse register(CreateTriageRequest req) {
        TriagePriority priority = resolvePriority(req);

        Triage triage = new Triage(UUID.randomUUID(), req.patientUuid(), req.performedBy(),
                req.unit(), req.bloodPressureSystolic(), req.bloodPressureDiastolic(),
                req.heartRate(), req.respiratoryRate(), req.temperature(),
                req.oxygenSaturation(), req.painLevel(), req.complaint(), priority);
        repository.save(triage);

        log.info("Triagem registrada: paciente={}, unidade={}, prioridade={}",
                req.patientUuid(), req.unit(), priority);
        return toResponse(triage);
    }

    @Transactional(readOnly = true)
    public List<TriageResponse> byPatient(UUID patientUuid) {
        return repository.findByPatientUuidOrderByPerformedAtDesc(patientUuid)
                .stream().map(TriageService::toResponse).toList();
    }

    private TriagePriority resolvePriority(CreateTriageRequest req) {
        if (req.priority() != null && !req.priority().isBlank()) {
            try {
                return TriagePriority.valueOf(req.priority().toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(
                        "Prioridade invalida: " + req.priority() + ". Use RED, ORANGE, YELLOW, GREEN ou BLUE.");
            }
        }
        return classifier.classify(req.bloodPressureSystolic(), req.bloodPressureDiastolic(),
                req.heartRate(), req.respiratoryRate(), req.temperature(),
                req.oxygenSaturation(), req.painLevel());
    }

    private static TriageResponse toResponse(Triage t) {
        return new TriageResponse(t.getId(), t.getPatientUuid(), t.getPerformedBy(), t.getUnit(),
                t.getBloodPressureSystolic(), t.getBloodPressureDiastolic(),
                t.getHeartRate(), t.getRespiratoryRate(), t.getTemperature(),
                t.getOxygenSaturation(), t.getPainLevel(), t.getComplaint(),
                t.getPriority().name(), t.getPerformedAt());
    }
}
