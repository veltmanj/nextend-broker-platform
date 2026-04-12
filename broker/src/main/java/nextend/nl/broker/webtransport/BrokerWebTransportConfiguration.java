package nextend.nl.broker.webtransport;

import io.netty.handler.codec.http.HttpHeaderNames;
import java.util.List;
import nextend.nl.broker.h3.BrokerH3Properties;
import nextend.nl.broker.security.jwt.BrokerDashboardTokenService;
import nextend.nl.broker.webtransport.backend.BrokerWebTransportServer;
import nextend.nl.broker.webtransport.backend.BrokerWebTransportServerFactory;
import nextend.nl.broker.webtransport.backend.ChromiumQuicheBrokerWebTransportServerFactory;
import nextend.nl.broker.webtransport.backend.NettyExperimentalBrokerWebTransportServerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.reactor.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.reactor.netty.NettyRouteProvider;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BrokerWebTransportProperties.class)
@ConditionalOnProperty(
    prefix = "nextend.broker.webtransport",
    name = "enabled",
    havingValue = "true")
public class BrokerWebTransportConfiguration {

  private static final Logger log = LoggerFactory.getLogger(BrokerWebTransportConfiguration.class);

  @Bean
  BrokerWebTransportServerFactory nettyExperimentalBrokerWebTransportServerFactory() {
    return new NettyExperimentalBrokerWebTransportServerFactory();
  }

  @Bean
  BrokerWebTransportServerFactory chromiumQuicheBrokerWebTransportServerFactory() {
    return new ChromiumQuicheBrokerWebTransportServerFactory();
  }

  @Bean
  BrokerWebTransportTlsMaterial brokerWebTransportCertificate() throws Exception {
    return BrokerWebTransportTlsMaterial.create();
  }

  @Bean
  DisposableBean brokerWebTransportCertificateCleanup(
      @Qualifier("brokerWebTransportCertificate") BrokerWebTransportTlsMaterial certificate) {
    return certificate::delete;
  }

  @Bean(destroyMethod = "disposeNow")
  BrokerWebTransportServer brokerWebTransportHttp3Server(
      BrokerWebTransportProperties properties,
      BrokerH3Properties h3Properties,
      @Qualifier("brokerWebTransportCertificate") BrokerWebTransportTlsMaterial certificate,
      List<BrokerWebTransportServerFactory> serverFactories)
      throws Exception {
    BrokerWebTransportServerFactory selectedFactory =
        serverFactories.stream()
            .filter(factory -> factory.backend() == properties.getBackend())
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No WebTransport server factory registered for backend "
                            + properties.getBackend().getPropertyValue()));
    BrokerWebTransportServer server = selectedFactory.bind(properties, h3Properties, certificate);

    log.info(
        "Started broker-owned WebTransport listener using backend {} on {}:{}{}",
        properties.getBackend().getPropertyValue(),
        properties.getBindAddress(),
        server.port(),
        properties.getPath());
    log.info(
        "Broker WebTransport certificate hash {} (hint: {}, example: {})",
        server.certificateInfo().certificateHash(),
        server.certificateInfo().browserUrlHint(properties),
        server.certificateInfo().exampleUrl(properties));

    return server;
  }

  @Bean
  WebServerFactoryCustomizer<NettyReactiveWebServerFactory>
      brokerWebTransportDiscoveryRouteCustomizer(
          BrokerWebTransportProperties properties,
          BrokerWebTransportServer brokerWebTransportHttp3Server,
          BrokerDashboardTokenService brokerDashboardTokenService) {
    return factory ->
        factory.addRouteProviders(
            (NettyRouteProvider)
                routes -> {
                  if (properties.isDiscoveryRouteEnabled()) {
                    routes.get(
                        properties.getPath() + "/info",
                        (request, response) ->
                            jsonResponse(
                                request,
                                response,
                                io.netty.handler.codec.http.HttpResponseStatus.OK,
                                "{\"mode\":\"broker-native-webtransport-scaffold\","
                                    + "\"transport\":\"http3\","
                                    + "\"backend\":\""
                                    + properties.getBackend().getPropertyValue()
                                    + "\","
                                    + "\"host\":\""
                                    + properties.getBindAddress()
                                    + "\","
                                    + "\"port\":"
                                    + properties.getPort()
                                    + ",\"maxSessions\":"
                                    + properties.getMaxSessions()
                                    + ",\"path\":\""
                                    + properties.getPath()
                                    + "\",\"brokerCertHash\":\""
                                    + brokerWebTransportHttp3Server
                                        .certificateInfo()
                                        .certificateHash()
                                    + "\",\"browserUrlHint\":\""
                                    + brokerWebTransportHttp3Server
                                        .certificateInfo()
                                        .browserUrlHint(properties)
                                    + "\"}",
                                properties));
                  }

                  if (properties.isAuthTokenRouteEnabled()) {
                    routes.get(
                        properties.getAuthTokenPath(),
                        (request, response) -> {
                          BrokerDashboardTokenService.IssuedToken token =
                              brokerDashboardTokenService.issueDashboardReadToken("nextend-neo");
                          return jsonResponse(
                              request,
                              response,
                              io.netty.handler.codec.http.HttpResponseStatus.OK,
                              "{\"token\":\""
                                  + token.token()
                                  + "\",\"scope\":\""
                                  + token.scope()
                                  + "\",\"issuedAt\":\""
                                  + token.issuedAt()
                                  + "\",\"expiresAt\":\""
                                  + token.expiresAt()
                                  + "\"}",
                              properties);
                        });
                  }

                  return routes;
                });
  }

  private static Mono<Void> jsonResponse(
      HttpServerRequest request,
      HttpServerResponse response,
      io.netty.handler.codec.http.HttpResponseStatus status,
      String body,
      BrokerWebTransportProperties properties) {
    response.status(status).header(HttpHeaderNames.CONTENT_TYPE, "application/json");
    applyCorsHeaders(request, response, properties);
    return response.sendString(Mono.just(body)).then();
  }

  private static void applyCorsHeaders(
      HttpServerRequest request,
      HttpServerResponse response,
      BrokerWebTransportProperties properties) {
    String origin = request.requestHeaders().get(HttpHeaderNames.ORIGIN);
    if (origin == null || origin.isBlank()) {
      return;
    }

    if (properties.getAllowedOrigins().contains("*")
        || properties.getAllowedOrigins().contains(origin)) {
      response
          .header(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, origin)
          .header(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, "GET")
          .header(HttpHeaderNames.VARY, HttpHeaderNames.ORIGIN.toString());
    }
  }
}
