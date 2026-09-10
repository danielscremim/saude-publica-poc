package br.usp.esalq.saude.history.graphql;

import br.usp.esalq.saude.history.security.AuthenticatedCaller;
import br.usp.esalq.saude.history.security.JwtAuthFilter;
import br.usp.esalq.saude.history.security.JwtValidator;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Disponibiliza o AuthenticatedCaller (ja validado pelo JwtAuthFilter) dentro do
 * GraphQLContext, para que os resolvers o recebam via @ContextValue.
 * Fallback: se o atributo nao estiver presente, revalida o header Authorization.
 */
@Component
public class CallerContextInterceptor implements WebGraphQlInterceptor {

    public static final String CALLER_KEY = "caller";

    private final JwtValidator validator;

    public CallerContextInterceptor(JwtValidator validator) {
        this.validator = validator;
    }

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        Object attr = request.getAttributes().get(JwtAuthFilter.CALLER_ATTR);
        AuthenticatedCaller caller;
        if (attr instanceof AuthenticatedCaller c) {
            caller = c;
        } else {
            String token = JwtValidator.extractBearer(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
            caller = token == null ? null : validator.validate(token);
        }
        if (caller != null) {
            request.configureExecutionInput((input, builder) ->
                    builder.graphQLContext(Map.of(CALLER_KEY, caller)).build());
        }
        return chain.next(request);
    }
}
