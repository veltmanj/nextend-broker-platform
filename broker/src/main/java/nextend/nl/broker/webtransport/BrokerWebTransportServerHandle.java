package nextend.nl.broker.webtransport;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import java.net.InetSocketAddress;
import nextend.nl.broker.webtransport.backend.BrokerWebTransportServer;

public final class BrokerWebTransportServerHandle implements BrokerWebTransportServer {
  private final Channel channel;
  private final BrokerWebTransportCertificateInfo certificateInfo;
  private final EventLoopGroup eventLoopGroup;

  BrokerWebTransportServerHandle(
      Channel channel,
      EventLoopGroup eventLoopGroup,
      BrokerWebTransportCertificateInfo certificateInfo) {
    this.channel = channel;
    this.certificateInfo = certificateInfo;
    this.eventLoopGroup = eventLoopGroup;
  }

  @Override
  public int port() {
    return ((InetSocketAddress) channel.localAddress()).getPort();
  }

  @Override
  public BrokerWebTransportCertificateInfo certificateInfo() {
    return certificateInfo;
  }

  @Override
  public void disposeNow() {
    ChannelFuture closeFuture = channel.close();
    closeFuture.awaitUninterruptibly();
    eventLoopGroup.shutdownGracefully().awaitUninterruptibly();
  }
}
