package nextend.nl.broker.webtransport.backend;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import nextend.nl.broker.h3.BrokerH3Properties;
import nextend.nl.broker.webtransport.BrokerWebTransportCertificateInfo;
import nextend.nl.broker.webtransport.BrokerWebTransportProperties;
import nextend.nl.broker.webtransport.BrokerWebTransportSidecarMode;
import nextend.nl.broker.webtransport.BrokerWebTransportTlsMaterial;

final class ChromiumQuicheSidecarBrokerWebTransportServer implements BrokerWebTransportServer {
  private static final org.slf4j.Logger log =
      LoggerFactory.getLogger(ChromiumQuicheSidecarBrokerWebTransportServer.class);
  private static final String DEFAULT_COMMAND = "nextend-broker-webtransport-native";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final HttpClient HTTP_CLIENT =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

  private final BrokerWebTransportCertificateInfo certificateInfo;
  private final Process process;
  private final int port;

  private ChromiumQuicheSidecarBrokerWebTransportServer(
      Process process, int port, BrokerWebTransportCertificateInfo certificateInfo) {
    this.certificateInfo = certificateInfo;
    this.process = process;
    this.port = port;
  }

  static BrokerWebTransportServer start(
      BrokerWebTransportProperties properties,
      BrokerH3Properties h3Properties,
      BrokerWebTransportTlsMaterial certificate)
      throws Exception {
    BrokerWebTransportProperties.ChromiumQuiche chromiumQuiche = properties.getChromiumQuiche();
    URI adminBaseUri = normalizeAdminBaseUri(chromiumQuiche.getAdminUrl());
    AtomicReference<String> lastLine = new AtomicReference<>("");
    Process process = null;
    String command = chromiumQuiche.getCommand();

    if (chromiumQuiche.getLaunchMode() == BrokerWebTransportSidecarMode.EMBEDDED) {
      if (command == null || command.isBlank()) {
        throw new UnsupportedOperationException(
            "The chromium-quiche sidecar command is not configured.");
      }

      command = resolveCommand(command);
      ProcessBuilder processBuilder = new ProcessBuilder("/bin/sh", "-lc", command);
      processBuilder.redirectErrorStream(true);
      processBuilder.environment().put("HOST", properties.getBindAddress());
      processBuilder.environment().put("PORT", Integer.toString(properties.getPort()));
      processBuilder.environment().put("SESSION_PATH", properties.getPath());
      processBuilder.environment().put("BROKER_URL", buildBrokerUrl(h3Properties));
      processBuilder.environment().put("BROKER_REJECT_UNAUTHORIZED", "false");
      processBuilder.environment().put("SIDECAR_ADMIN_HOST", adminBaseUri.getHost());
      processBuilder.environment().put("SIDECAR_ADMIN_PORT", Integer.toString(resolvePort(adminBaseUri)));
      processBuilder.environment().put(
          "SIDECAR_CONTRACT_VERSION", Integer.toString(chromiumQuiche.getContractVersion()));

      process = processBuilder.start();
      Thread logThread = createLogThread(process, lastLine);
      logThread.start();
    }

    BrokerWebTransportCertificateInfo sidecarCertificateInfo =
        awaitReady(process, properties, properties.getStartupTimeout(), adminBaseUri, lastLine, command);
    return new ChromiumQuicheSidecarBrokerWebTransportServer(
        process, properties.getPort(), sidecarCertificateInfo);
  }

  @Override
  public int port() {
    return port;
  }

  @Override
  public BrokerWebTransportCertificateInfo certificateInfo() {
    return certificateInfo;
  }

  @Override
  public void disposeNow() {
    if (process == null || !process.isAlive()) {
      return;
    }

    process.destroy();
    try {
      if (!process.waitFor(5, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        process.waitFor(5, TimeUnit.SECONDS);
      }
    } catch (InterruptedException interruptedException) {
      Thread.currentThread().interrupt();
      process.destroyForcibly();
    }
  }

  private static Thread createLogThread(
      Process process, AtomicReference<String> lastLine) {
    Thread thread =
        new Thread(
            () -> {
              try (BufferedReader reader =
                  new BufferedReader(
                      new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                  lastLine.set(line);
                  log.info("[chromium-quiche-sidecar] {}", line);
                }
              } catch (IOException exception) {
                lastLine.set(exception.getMessage());
                log.warn("The chromium-quiche sidecar log stream closed unexpectedly", exception);
              }
            },
            "chromium-quiche-sidecar-log");
    thread.setDaemon(true);
    return thread;
  }

  private static BrokerWebTransportCertificateInfo awaitReady(
      Process process,
      BrokerWebTransportProperties properties,
      Duration startupTimeout,
      URI adminBaseUri,
      AtomicReference<String> lastLine,
      String command)
      throws Exception {
    long deadlineNanos = System.nanoTime() + startupTimeout.toNanos();
    Integer expectedContractVersion = null;

    while (System.nanoTime() < deadlineNanos) {
      if (process != null && !process.isAlive()) {
        throw new IllegalStateException(
            "The chromium-quiche sidecar exited before it became ready. Command="
                + command
                + ", last output="
                + lastLine.get());
      }

      try {
        if (expectedContractVersion == null) {
          expectedContractVersion = fetchSidecarContractVersion(adminBaseUri, properties);
        }

        SidecarReadyState readyState = fetchReadyState(adminBaseUri);
        if (readyState.ready()) {
          if (readyState.contractVersion() != expectedContractVersion) {
            throw new IllegalStateException(
                "The chromium-quiche sidecar reported contract version "
                    + readyState.contractVersion()
                    + " but the broker expects "
                    + expectedContractVersion
                    + ".");
          }

          if (readyState.certificateHash().isBlank()) {
            throw new IllegalStateException(
                "The chromium-quiche sidecar became ready without reporting its certificate hash.");
          }

          return BrokerWebTransportCertificateInfo.fromFingerprint(
              properties, readyState.certificateHash());
        }
      } catch (IOException exception) {
        lastLine.set(exception.getMessage());
      }

      TimeUnit.MILLISECONDS.sleep(200);
    }

    if (process != null && process.isAlive()) {
      process.destroy();
      process.waitFor(5, TimeUnit.SECONDS);
    }

    throw new IllegalStateException(
        "The chromium-quiche sidecar did not report readiness within "
            + startupTimeout
            + ". Admin URL="
            + adminBaseUri
            + ", last output="
            + lastLine.get());
  }

  private static int fetchSidecarContractVersion(
      URI adminBaseUri, BrokerWebTransportProperties properties) throws Exception {
    JsonNode info = fetchJson(adminBaseUri.resolve("/info"));
    JsonNode contractVersionNode = info.get("contractVersion");
    if (contractVersionNode == null || !contractVersionNode.canConvertToInt()) {
      throw new IllegalStateException(
          "The chromium-quiche sidecar did not report a numeric contract version.");
    }

    int contractVersion = contractVersionNode.asInt();
    int expected = properties.getChromiumQuiche().getContractVersion();
    if (contractVersion != expected) {
      throw new IllegalStateException(
          "The chromium-quiche sidecar contract version "
              + contractVersion
              + " does not match the broker expectation "
              + expected
              + ".");
    }

    return contractVersion;
  }

  private static SidecarReadyState fetchReadyState(URI adminBaseUri) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(adminBaseUri.resolve("/readyz"))
            .timeout(Duration.ofSeconds(2))
            .GET()
            .build();
    HttpResponse<String> response =
        HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    if (response.statusCode() != 200 && response.statusCode() != 503) {
      throw new IOException(
          "The chromium-quiche sidecar /readyz endpoint responded with status "
              + response.statusCode()
              + ".");
    }

    JsonNode body = OBJECT_MAPPER.readTree(response.body());
    String certificateHash = body.path("certificateFingerprint").asText("");
    int contractVersion = body.path("contractVersion").asInt(-1);
    boolean ready = response.statusCode() == 200 && "ready".equals(body.path("status").asText());
    return new SidecarReadyState(ready, certificateHash, contractVersion);
  }

  private static JsonNode fetchJson(URI uri) throws Exception {
    HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(2)).GET().build();
    HttpResponse<String> response =
        HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    if (response.statusCode() != 200) {
      throw new IOException(
          "The chromium-quiche sidecar admin endpoint "
              + uri
              + " responded with status "
              + response.statusCode()
              + ".");
    }

    return OBJECT_MAPPER.readTree(response.body());
  }

  private static URI normalizeAdminBaseUri(String adminUrl) {
    if (adminUrl == null || adminUrl.isBlank()) {
      return URI.create("http://127.0.0.1:9090");
    }

    URI uri = URI.create(adminUrl.endsWith("/") ? adminUrl.substring(0, adminUrl.length() - 1) : adminUrl);
    if (uri.getHost() == null || uri.getHost().isBlank()) {
      throw new IllegalArgumentException("The chromium-quiche sidecar admin URL must include a host.");
    }
    return uri;
  }

  private static int resolvePort(URI adminBaseUri) {
    if (adminBaseUri.getPort() > 0) {
      return adminBaseUri.getPort();
    }

    return "https".equalsIgnoreCase(adminBaseUri.getScheme()) ? 443 : 80;
  }

  private record SidecarReadyState(boolean ready, String certificateHash, int contractVersion) {}

  private static String buildBrokerUrl(BrokerH3Properties h3Properties) {
    return "h3://"
      + resolveInternalBrokerHost(h3Properties.getBindAddress())
      + ":"
      + h3Properties.getPort()
      + h3Properties.getPath();
  }

  private static String resolveInternalBrokerHost(String bindAddress) {
    String podHostname = System.getenv("HOSTNAME");
    if (podHostname != null && !podHostname.isBlank()) {
      return podHostname;
    }

    if (bindAddress == null
        || bindAddress.isBlank()
        || "0.0.0.0".equals(bindAddress)
        || "::".equals(bindAddress)) {
      return "127.0.0.1";
    }

    return bindAddress;
  }

  private static String resolveCommand(String command) {
    if (!DEFAULT_COMMAND.equals(command)) {
      return command;
    }

    Path localSourcePath =
        Path.of(System.getProperty("user.dir"))
            .resolveSibling("sidecar")
            .resolve("src")
            .resolve("server.mjs");
    if (Files.isRegularFile(localSourcePath)) {
      return "node " + localSourcePath;
    }

    return command;
  }
}
