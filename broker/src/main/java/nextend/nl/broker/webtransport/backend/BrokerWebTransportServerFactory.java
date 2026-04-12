package nextend.nl.broker.webtransport.backend;

import nextend.nl.broker.h3.BrokerH3Properties;
import nextend.nl.broker.webtransport.BrokerWebTransportBackend;
import nextend.nl.broker.webtransport.BrokerWebTransportProperties;
import nextend.nl.broker.webtransport.BrokerWebTransportTlsMaterial;

public interface BrokerWebTransportServerFactory {
  BrokerWebTransportBackend backend();

  BrokerWebTransportServer bind(
      BrokerWebTransportProperties properties,
      BrokerH3Properties h3Properties,
      BrokerWebTransportTlsMaterial certificate)
      throws Exception;
}
