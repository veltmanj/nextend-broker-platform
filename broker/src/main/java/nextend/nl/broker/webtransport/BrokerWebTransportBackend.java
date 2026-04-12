package nextend.nl.broker.webtransport;

public enum BrokerWebTransportBackend {
  NETTY_EXPERIMENTAL("netty-experimental"),
  CHROMIUM_QUICHE("chromium-quiche");

  private final String propertyValue;

  BrokerWebTransportBackend(String propertyValue) {
    this.propertyValue = propertyValue;
  }

  public String getPropertyValue() {
    return propertyValue;
  }
}
