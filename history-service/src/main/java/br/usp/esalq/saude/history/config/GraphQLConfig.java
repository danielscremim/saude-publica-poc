package br.usp.esalq.saude.history.config;

import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.analysis.MaxQueryDepthInstrumentation;
import graphql.execution.instrumentation.Instrumentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Protecao contra queries abusivas (aninhamento profundo / muitos campos), que em
 * GraphQL substitui o rate limit por recurso que o Kong aplica a rotas REST (RNF-06).
 * Uma query acima do limite e rejeitada antes de qualquer resolver executar.
 */
@Configuration
public class GraphQLConfig {

    @Bean
    public Instrumentation maxDepth(@Value("${graphql.limits.max-depth:5}") int maxDepth) {
        return new MaxQueryDepthInstrumentation(maxDepth);
    }

    @Bean
    public Instrumentation maxComplexity(@Value("${graphql.limits.max-complexity:100}") int maxComplexity) {
        return new MaxQueryComplexityInstrumentation(maxComplexity);
    }
}
