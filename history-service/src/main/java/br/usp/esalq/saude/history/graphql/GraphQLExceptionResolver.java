package br.usp.esalq.saude.history.graphql;

import br.usp.esalq.saude.history.service.TimelineService.ConsentDeniedException;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Em GraphQL o HTTP e sempre 200; a semantica de erro vai em errors[].extensions.classification.
 * Mapeamento equivalente ao GlobalExceptionHandler do REST:
 *   ConsentDeniedException -> FORBIDDEN     (RNF-06)
 *   Unauthenticated        -> UNAUTHORIZED
 *   404 a montante         -> NOT_FOUND
 */
@Component
public class GraphQLExceptionResolver extends DataFetcherExceptionResolverAdapter {

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        if (ex instanceof ConsentDeniedException) {
            return build(env, ErrorType.FORBIDDEN, "consent_denied", ex.getMessage());
        }
        if (ex instanceof TimelineGraphQLController.UnauthenticatedException) {
            return build(env, ErrorType.UNAUTHORIZED, "unauthorized", ex.getMessage());
        }
        if (ex instanceof HttpClientErrorException.NotFound) {
            return build(env, ErrorType.NOT_FOUND, "not_found", "Paciente nao encontrado");
        }
        return null; // demais excecoes: comportamento padrao (INTERNAL_ERROR)
    }

    private static GraphQLError build(DataFetchingEnvironment env, ErrorType type, String code, String msg) {
        return GraphqlErrorBuilder.newError(env)
                .errorType(type)
                .message(code + ": " + msg)
                .build();
    }
}
