package nextend.nl.broker.security.jwt;

import java.time.Duration;
import javax.crypto.spec.SecretKeySpec;
import nextend.nl.broker.security.BrokerSecurityConfiguration;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.rsocket.messaging.RSocketStrategiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.codec.DataBufferDecoder;
import org.springframework.core.codec.DataBufferEncoder;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.messaging.rsocket.RSocketConnectorConfigurer;
import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler;
import org.springframework.security.config.annotation.rsocket.EnableRSocketSecurity;
import org.springframework.security.config.annotation.rsocket.RSocketSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtReactiveAuthenticationManager;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.rsocket.core.PayloadSocketAcceptorInterceptor;
import org.springframework.security.rsocket.metadata.BearerTokenAuthenticationEncoder;
import reactor.util.retry.Retry;

@Configuration
@EnableRSocketSecurity
public class BrokerJWTSecurityConfiguration extends BrokerSecurityConfiguration {

  private static final Logger log = LoggerFactory.getLogger(BrokerJWTSecurityConfiguration.class);

  @Value("${app.security.jwt.sKey}")
  private String sKey;

  private MacAlgorithm MAC_ALGORITHM = MacAlgorithm.HS512;

  @Value("${app.security.jwt.alg: AES}")
  private String AES = "AES";

  private Object disposable;

  private Object connector;

  @Bean
  RSocketStrategiesCustomizer rSocketStrategiesCustomizer() {
    return strategies -> {
      strategies
          .encoder(new BearerTokenAuthenticationEncoder())
          .encoder(new DataBufferEncoder())
          .encoder(new Jackson2JsonEncoder())
          .decoder(new DataBufferDecoder())
          .decoder(new Jackson2JsonDecoder());
    };
  }

  @Bean
  PayloadSocketAcceptorInterceptor authorization(RSocketSecurity rsocketSecurity) {
    RSocketSecurity security =
        pattern(rsocketSecurity)
            .jwt(
                jwtSpec -> {
                  try {
                    jwtSpec.authenticationManager(
                        jwtReactiveAuthenticationManager(getReactiveJWTDecoder()));
                  } catch (Exception e) {
                    throw new RuntimeException(e);
                  }
                });
    return security.build();
  }

  @Bean
  public ReactiveJwtDecoder getReactiveJWTDecoder() {
    log.debug(
        "[start getAccessTokenDecoder for key ending with: "
            + sKey.substring(sKey.length() - 5)
            + "]");
    try {
      SecretKeySpec secretKey = new SecretKeySpec(Hex.decodeHex(sKey.toCharArray()), AES);
      return NimbusReactiveJwtDecoder.withSecretKey(secretKey).macAlgorithm(MAC_ALGORITHM).build();
    } catch (DecoderException e) {
      throw new RuntimeException(e);
    }
  }

  @Primary
  @Bean
  RSocketConnectorConfigurer rSocketConfigurer(RSocketMessageHandler handler) {
    return configurer -> {
      configurer
          .acceptor(handler.responder())
          .reconnect(
              Retry.fixedDelay(Long.MAX_VALUE, Duration.ofSeconds(1))
                  .doBeforeRetry(s -> log.debug("Disconnected. Trying to connect...")));
    };
  }

  @Bean
  public JwtReactiveAuthenticationManager jwtReactiveAuthenticationManager(
      ReactiveJwtDecoder decoder) {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    BrokerJwtGrantedAuthoritiesConverter authoritiesConverter =
        new BrokerJwtGrantedAuthoritiesConverter();
    authoritiesConverter.setAuthorityPrefix("ROLE_");
    converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
    JwtReactiveAuthenticationManager manager = new JwtReactiveAuthenticationManager(decoder);
    manager.setJwtAuthenticationConverter(new ReactiveJwtAuthenticationConverterAdapter(converter));
    return manager;
  }

  @Bean
  RSocketMessageHandler messageHandler(RSocketStrategies strategies) {
    return getMessageHandler(strategies);
  }
}
