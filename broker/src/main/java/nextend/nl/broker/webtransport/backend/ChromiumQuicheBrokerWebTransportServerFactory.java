package nextend.nl.broker.webtransport.backend;

import nextend.nl.broker.h3.BrokerH3Properties;
import nextend.nl.broker.webtransport.BrokerWebTransportBackend;
import nextend.nl.broker.webtransport.BrokerWebTransportNativeMode;
import nextend.nl.broker.webtransport.BrokerWebTransportProperties;
import nextend.nl.broker.webtransport.BrokerWebTransportTlsMaterial;

public final class ChromiumQuicheBrokerWebTransportServerFactory
    implements BrokerWebTransportServerFactory {

  @Override
  public BrokerWebTransportBackend backend() {
    return BrokerWebTransportBackend.CHROMIUM_QUICHE;
  }

  @Override
  public BrokerWebTransportServer bind(
      BrokerWebTransportProperties properties,
      BrokerH3Properties h3Properties,
      BrokerWebTransportTlsMaterial certificate)
      throws Exception {
    BrokerWebTransportProperties.ChromiumQuiche chromiumQuiche = properties.getChromiumQuiche();
    if (chromiumQuiche.getMode() == BrokerWebTransportNativeMode.SIDECAR) {
      return ChromiumQuicheSidecarBrokerWebTransportServer.start(
          properties, h3Properties, certificate);
    }

    throw new UnsupportedOperationException(
        "The chromium-quiche WebTransport backend is not implemented yet. "
            + "Planned mode="
            + chromiumQuiche.getMode().name().toLowerCase()
            + ", command="
            + chromiumQuiche.getCommand()
            + ", libraryPath="
            + chromiumQuiche.getLibraryPath());
  }
}
