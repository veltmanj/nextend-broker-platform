package nextend.nl.broker.security;

import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.rsocket.RSocketSecurity;

public class BrokerSecurityConfiguration {

  protected RSocketSecurity pattern(RSocketSecurity security) {
    return security
        .authorizePayload(
            authorize ->
                authorize
                    .setup()
                    .permitAll()
                    .route("cluster.remote-broker-info")
                    .permitAll()
                    .route("cluster.broker-info")
                    .permitAll()
                    .route("cluster.route-join")
                    .permitAll()
                    .route("hello")
                    .permitAll()
                    .route("neo.stream")
                    .hasRole("DASHBOARD_READ")
                    .anyRequest()
                    .authenticated()
                    .anyExchange()
                    .authenticated())
        .jwt(Customizer.withDefaults());
  }

  protected RSocketMessageHandler getMessageHandler(RSocketStrategies strategies) {
    RSocketMessageHandler mh = new RSocketMessageHandler();
    mh.getArgumentResolverConfigurer()
        .addCustomResolver(
            new org.springframework.security.messaging.handler.invocation.reactive
                .AuthenticationPrincipalArgumentResolver());
    mh.setRSocketStrategies(strategies);
    return mh;
  }
}
