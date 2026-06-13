package br.usp.esalq.saude.consent.service;

import br.usp.esalq.saude.consent.config.KafkaTopicConfig;
import br.usp.esalq.saude.consent.dto.ConsentCheckResponse;
import br.usp.esalq.saude.consent.dto.ConsentResponse;
import br.usp.esalq.saude.consent.dto.CreateConsentRequest;
import br.usp.esalq.saude.consent.entity.Consent;
import br.usp.esalq.saude.consent.event.ConsentRevokedEvent;
import br.usp.esalq.saude.consent.repository.ConsentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ConsentService {

    private static final Logger log = LoggerFactory.getLogger(ConsentService.class);

    private final ConsentRepository repository;
    private final KafkaTemplate<String, ConsentRevokedEvent> kafkaTemplate;

    public ConsentService(ConsentRepository repository,
                          KafkaTemplate<String, ConsentRevokedEvent> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Transactional
    public ConsentResponse grant(CreateConsentRequest req) {
        Consent consent = new Consent(UUID.randomUUID(), req.patientUuid(),
                req.institutionId(), req.scope());
        repository.save(consent);
        log.info("Consentimento concedido: paciente={}, instituicao={}, escopo={}",
                req.patientUuid(), req.institutionId(), req.scope());
        return toResponse(consent);
    }

    /**
     * Revoga o consentimento e publica consent.revoked no Kafka.
     * Servicos de dados consomem o evento e invalidam caches locais (RNF-06: <= 1s).
     */
    @Transactional
    public ConsentResponse revoke(UUID consentId) {
        Consent consent = repository.findById(consentId)
                .orElseThrow(() -> new IllegalArgumentException("Consentimento nao encontrado: " + consentId));

        if (consent.isActive()) {
            consent.revoke();
            repository.save(consent);

            ConsentRevokedEvent event = new ConsentRevokedEvent(
                    consent.getPatientUuid(), consent.getInstitutionId(), consent.getRevokedAt());
            kafkaTemplate.send(KafkaTopicConfig.CONSENT_REVOKED_TOPIC,
                    consent.getPatientUuid().toString(), event);
            log.info("Consentimento revogado e publicado em {}: paciente={}, instituicao={}",
                    KafkaTopicConfig.CONSENT_REVOKED_TOPIC,
                    consent.getPatientUuid(), consent.getInstitutionId());
        }
        return toResponse(consent);
    }

    /**
     * Resposta rapida (sim/nao) usada pelos servicos de dados (history) antes
     * de qualquer leitura. Consulta indexada por (patient_uuid, institution_id).
     */
    @Transactional(readOnly = true)
    public ConsentCheckResponse check(UUID patientUuid, String institutionId) {
        List<Consent> active = repository.findActive(patientUuid, institutionId);
        String scopes = active.stream()
                .map(Consent::getScope)
                .distinct()
                .collect(Collectors.joining(" "));
        return new ConsentCheckResponse(patientUuid, institutionId, !active.isEmpty(), scopes);
    }

    @Transactional(readOnly = true)
    public List<ConsentResponse> listByPatient(UUID patientUuid) {
        return repository.findByPatientUuid(patientUuid).stream()
                .map(this::toResponse)
                .toList();
    }

    private ConsentResponse toResponse(Consent c) {
        return new ConsentResponse(c.getId(), c.getPatientUuid(), c.getInstitutionId(),
                c.getScope(), c.getGrantedAt(), c.getRevokedAt());
    }
}
