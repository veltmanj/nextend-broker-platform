package nextend.nl.broker.webtransport.backend;

import nextend.nl.broker.h3.BrokerH3Properties;
import nextend.nl.broker.webtransport.BrokerWebTransportBackend;
import nextend.nl.broker.webtransport.BrokerWebTransportBootstrap;
import nextend.nl.broker.webtransport.BrokerWebTransportCertificateInfo;
import nextend.nl.broker.webtransport.BrokerWebTransportProperties;
import nextend.nl.broker.webtransport.BrokerWebTransportTlsMaterial;

public final class NettyExperimentalBrokerWebTransportServerFactory
    implements BrokerWebTransportServerFactory {

  @Override
  public BrokerWebTransportBackend backend() {
    return BrokerWebTransportBackend.NETTY_EXPERIMENTAL;
  }

  @Override
  public BrokerWebTransportServer bind(
      BrokerWebTransportProperties properties,
      BrokerH3Properties h3Properties,
      BrokerWebTransportTlsMaterial certificate)
      throws Exception {
    return BrokerWebTransportBootstrap.bind(
        properties, certificate, BrokerWebTransportCertificateInfo.from(properties, certificate));
  }
}
