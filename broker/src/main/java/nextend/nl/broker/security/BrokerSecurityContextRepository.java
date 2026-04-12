package nextend.nl.broker.security;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public class BrokerSecurityContextRepository implements ServerSecurityContextRepository {


    private ReactiveAuthenticationManager authenticationManager;

    public BrokerSecurityContextRepository(ReactiveAuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    @Override
    public Mono<Void> save(ServerWebExchange exchange,
                           org.springframework.security.core.context.SecurityContext context) {
        return Mono.error(new UnsupportedOperationException("Not supported"));
    }

    @Override
    public Mono<org.springframework.security.core.context.SecurityContext> load(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        if (request.getPath().value().startsWith("/actuator") ) {
            return Mono.empty();
        }
        return null;
    }

}
