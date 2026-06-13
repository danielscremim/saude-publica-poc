package br.usp.esalq.saude.exam.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String EXAM_REQUESTED_TOPIC = "exam.requested";

    @Bean
    public NewTopic examRequestedTopic() {
        return TopicBuilder.name(EXAM_REQUESTED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
