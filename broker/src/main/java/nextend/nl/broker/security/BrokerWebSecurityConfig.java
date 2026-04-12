package nextend.nl.broker.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebSecurity
public class BrokerWebSecurityConfig {

  @Bean
  public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
    return http.exceptionHandling(
            exceptionHandlingSpec ->
                exceptionHandlingSpec
                    .authenticationEntryPoint(
                        (swe, e) ->
                            Mono.fromRunnable(
                                () -> {
                                  swe.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                                }))
                    .accessDeniedHandler(
                        (swe, e) ->
                            Mono.fromRunnable(
                                () -> {
                                  swe.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                                })))
        .csrf(ServerHttpSecurity.CsrfSpec::disable)
        .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
        .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
        .authorizeExchange(
            authorizeExchangeSpec ->
                authorizeExchangeSpec
                    .pathMatchers(
                        HttpMethod.GET,
                        "/actuator/health",
                        "/actuator/health/**",
                        "/actuator/info",
                        "/actuator/prometheus")
                    .permitAll()
                    .pathMatchers(HttpMethod.GET, "/broker/wt/info", "/broker/auth/token")
                    .permitAll()
                    .pathMatchers("/actuator/**")
                    .denyAll()
                    .anyExchange()
                    .denyAll())
        .build();
  }
}
