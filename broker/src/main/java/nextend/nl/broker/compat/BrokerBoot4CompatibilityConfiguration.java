package nextend.nl.broker.compat;

import io.rsocket.broker.spring.ServerTransportFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.client.reactive.ReactorResourceFactory;
import org.springframework.boot.rsocket.server.RSocketServerCustomizer;

@Configuration(proxyBeanMethods = false)
public class BrokerBoot4CompatibilityConfiguration {
  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  ServerTransportFactory boot4ServerTransportFactory(
      ReactorResourceFactory resourceFactory,
      ObjectProvider<RSocketServerCustomizer> customizers) {
    return new Boot4ServerTransportFactory(resourceFactory, customizers);
  }
}
