package br.usp.esalq.saude.consent.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String CONSENT_REVOKED_TOPIC = "consent.revoked";

    @Bean
    public NewTopic consentRevokedTopic() {
        return TopicBuilder.name(CONSENT_REVOKED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
