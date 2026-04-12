package nextend.nl.broker.webtransport.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import nextend.nl.broker.h3.BrokerH3Properties;
import nextend.nl.broker.webtransport.BrokerWebTransportProperties;
import nextend.nl.broker.webtransport.BrokerWebTransportSidecarMode;

class ChromiumQuicheSidecarBrokerWebTransportServerTests {

  private HttpServer httpServer;

  @AfterEach
  void tearDown() {
    if (httpServer != null) {
      httpServer.stop(0);
    }
  }

  @Test
  void companionModeUsesAdminContractForReadiness() throws Exception {
    httpServer = startServer(1, true, "12:34:56");

    BrokerWebTransportProperties properties = new BrokerWebTransportProperties();
    properties.setBindAddress("0.0.0.0");
    properties.getChromiumQuiche().setLaunchMode(BrokerWebTransportSidecarMode.COMPANION);
    properties.getChromiumQuiche().setAdminUrl("http://127.0.0.1:" + httpServer.getAddress().getPort());
    properties.getChromiumQuiche().setContractVersion(1);

    BrokerH3Properties h3Properties = new BrokerH3Properties();
    BrokerWebTransportServer server =
        ChromiumQuicheSidecarBrokerWebTransportServer.start(properties, h3Properties, null);

    assertEquals("12:34:56", server.certificateInfo().certificateHash());
    assertEquals(7443, server.port());
    server.disposeNow();
  }

  @Test
  void companionModeRejectsMismatchedContractVersion() throws Exception {
    httpServer = startServer(2, true, "12:34:56");

    BrokerWebTransportProperties properties = new BrokerWebTransportProperties();
    properties.getChromiumQuiche().setLaunchMode(BrokerWebTransportSidecarMode.COMPANION);
    properties.getChromiumQuiche().setAdminUrl("http://127.0.0.1:" + httpServer.getAddress().getPort());
    properties.getChromiumQuiche().setContractVersion(1);
    properties.setStartupTimeout(java.time.Duration.ofSeconds(2));

    BrokerH3Properties h3Properties = new BrokerH3Properties();

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> ChromiumQuicheSidecarBrokerWebTransportServer.start(properties, h3Properties, null));

    assertEquals(
        "The chromium-quiche sidecar contract version 2 does not match the broker expectation 1.",
        exception.getMessage());
  }

  private HttpServer startServer(int contractVersion, boolean ready, String fingerprint)
      throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/info",
        jsonHandler(
            "{" +
            "\"version\":\"1.0.0-alpha.0\"," +
            "\"buildRevision\":\"test\"," +
            "\"contractVersion\":" + contractVersion +
            "}"));
    server.createContext(
        "/readyz",
        exchange -> {
          String body =
              "{" +
              "\"status\":\"" + (ready ? "ready" : "starting") + "\"," +
              "\"contractVersion\":" + contractVersion + "," +
              "\"certificateFingerprint\":\"" + fingerprint + "\"," +
              "\"sessionPath\":\"/broker/wt\"," +
              "\"brokerUrl\":\"ws://127.0.0.1:6934\"" +
              "}";
          writeResponse(exchange, ready ? 200 : 503, body);
        });
    server.start();
    return server;
  }

  private HttpHandler jsonHandler(String body) {
    return exchange -> writeResponse(exchange, 200, body);
  }

  private void writeResponse(HttpExchange exchange, int statusCode, String body)
      throws IOException {
    byte[] payload = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(statusCode, payload.length);
    try (OutputStream outputStream = exchange.getResponseBody()) {
      outputStream.write(payload);
    }
  }
}
