package nextend.nl.broker.webtransport;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nextend.broker.webtransport")
public class BrokerWebTransportProperties {

  private boolean enabled = false;
  private BrokerWebTransportBackend backend = BrokerWebTransportBackend.NETTY_EXPERIMENTAL;
  private boolean discoveryRouteEnabled = true;
  private boolean authTokenRouteEnabled = true;
  private String bindAddress = "0.0.0.0";
  private int port = 7443;
  private String path = "/broker/wt";
  private String authTokenPath = "/broker/auth/token";
  private int maxSessions = 100;
  private Duration idleTimeout = Duration.ofSeconds(30);
  private Duration startupTimeout = Duration.ofSeconds(20);
  private List<String> allowedOrigins = new ArrayList<>();
  private ChromiumQuiche chromiumQuiche = new ChromiumQuiche();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public BrokerWebTransportBackend getBackend() {
    return backend;
  }

  public void setBackend(BrokerWebTransportBackend backend) {
    this.backend = backend;
  }

  public boolean isDiscoveryRouteEnabled() {
    return discoveryRouteEnabled;
  }

  public void setDiscoveryRouteEnabled(boolean discoveryRouteEnabled) {
    this.discoveryRouteEnabled = discoveryRouteEnabled;
  }

  public boolean isAuthTokenRouteEnabled() {
    return authTokenRouteEnabled;
  }

  public void setAuthTokenRouteEnabled(boolean authTokenRouteEnabled) {
    this.authTokenRouteEnabled = authTokenRouteEnabled;
  }

  public String getBindAddress() {
    return bindAddress;
  }

  public void setBindAddress(String bindAddress) {
    this.bindAddress = bindAddress;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public String getAuthTokenPath() {
    return authTokenPath;
  }

  public void setAuthTokenPath(String authTokenPath) {
    this.authTokenPath = authTokenPath;
  }

  public int getMaxSessions() {
    return maxSessions;
  }

  public void setMaxSessions(int maxSessions) {
    this.maxSessions = maxSessions;
  }

  public Duration getIdleTimeout() {
    return idleTimeout;
  }

  public void setIdleTimeout(Duration idleTimeout) {
    this.idleTimeout = idleTimeout;
  }

  public Duration getStartupTimeout() {
    return startupTimeout;
  }

  public void setStartupTimeout(Duration startupTimeout) {
    this.startupTimeout = startupTimeout;
  }

  public List<String> getAllowedOrigins() {
    return allowedOrigins;
  }

  public void setAllowedOrigins(List<String> allowedOrigins) {
    this.allowedOrigins = allowedOrigins;
  }

  public ChromiumQuiche getChromiumQuiche() {
    return chromiumQuiche;
  }

  public void setChromiumQuiche(ChromiumQuiche chromiumQuiche) {
    this.chromiumQuiche = chromiumQuiche;
  }

  public static class ChromiumQuiche {
    private BrokerWebTransportNativeMode mode = BrokerWebTransportNativeMode.SIDECAR;
    private BrokerWebTransportSidecarMode launchMode = BrokerWebTransportSidecarMode.EMBEDDED;
    private String command = "nextend-broker-webtransport-native";
    private String adminUrl = "http://127.0.0.1:9090";
    private int contractVersion = 1;
    private String libraryPath;

    public BrokerWebTransportNativeMode getMode() {
      return mode;
    }

    public void setMode(BrokerWebTransportNativeMode mode) {
      this.mode = mode;
    }

    public String getCommand() {
      return command;
    }

    public void setCommand(String command) {
      this.command = command;
    }

    public BrokerWebTransportSidecarMode getLaunchMode() {
      return launchMode;
    }

    public void setLaunchMode(BrokerWebTransportSidecarMode launchMode) {
      this.launchMode = launchMode;
    }

    public String getAdminUrl() {
      return adminUrl;
    }

    public void setAdminUrl(String adminUrl) {
      this.adminUrl = adminUrl;
    }

    public int getContractVersion() {
      return contractVersion;
    }

    public void setContractVersion(int contractVersion) {
      this.contractVersion = contractVersion;
    }

    public String getLibraryPath() {
      return libraryPath;
    }

    public void setLibraryPath(String libraryPath) {
      this.libraryPath = libraryPath;
    }
  }
}
