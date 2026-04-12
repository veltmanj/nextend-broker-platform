package nextend.nl.broker.compat;

import io.rsocket.broker.spring.ServerTransportFactory;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.rsocket.netty.NettyRSocketServerFactory;
import org.springframework.boot.rsocket.server.RSocketServer.Transport;
import org.springframework.boot.rsocket.server.RSocketServerCustomizer;
import org.springframework.boot.rsocket.server.RSocketServerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.client.reactive.ReactorResourceFactory;

@Order(Ordered.HIGHEST_PRECEDENCE)
public class Boot4ServerTransportFactory implements ServerTransportFactory {
  private final ReactorResourceFactory resourceFactory;
  private final ObjectProvider<RSocketServerCustomizer> customizers;

  public Boot4ServerTransportFactory(
      ReactorResourceFactory resourceFactory,
      ObjectProvider<RSocketServerCustomizer> customizers) {
    this.resourceFactory = resourceFactory;
    this.customizers = customizers;
  }

  @Override
  public boolean supports(URI uri) {
    return isWebsocket(uri) || isTcp(uri);
  }

  @Override
  public RSocketServerFactory create(URI uri) {
    NettyRSocketServerFactory factory = new NettyRSocketServerFactory();
    factory.setResourceFactory(this.resourceFactory);
    factory.setTransport(findTransport(uri));

    InetAddress address = getHostAsAddress(uri);
    if (address != null) {
      factory.setAddress(address);
    }

    if (uri.getPort() >= 0) {
      factory.setPort(uri.getPort());
    }

    factory.setRSocketServerCustomizers(this.customizers.orderedStream().toList());
    return factory;
  }

  private static Transport findTransport(URI uri) {
    if (isWebsocket(uri)) {
      return Transport.WEBSOCKET;
    }
    if (isTcp(uri)) {
      return Transport.TCP;
    }
    throw new IllegalStateException("Unsupported broker server transport URI: " + uri);
  }

  private static InetAddress getHostAsAddress(URI uri) {
    if (uri == null || uri.getHost() == null || uri.getHost().isBlank()) {
      return null;
    }

    try {
      return InetAddress.getByName(uri.getHost());
    } catch (UnknownHostException exception) {
      throw new IllegalStateException("Unable to resolve host: " + uri.getHost(), exception);
    }
  }

  private static boolean isTcp(URI uri) {
    return uri != null && "tcp".equalsIgnoreCase(uri.getScheme());
  }

  private static boolean isWebsocket(URI uri) {
    if (uri == null) {
      return false;
    }

    String scheme = uri.getScheme();
    return "ws".equalsIgnoreCase(scheme) || "wss".equalsIgnoreCase(scheme);
  }
}
