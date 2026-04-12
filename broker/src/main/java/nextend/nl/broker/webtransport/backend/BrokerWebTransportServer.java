package nextend.nl.broker.webtransport.backend;

import nextend.nl.broker.webtransport.BrokerWebTransportCertificateInfo;

public interface BrokerWebTransportServer {
  int port();

  BrokerWebTransportCertificateInfo certificateInfo();

  void disposeNow();
}
