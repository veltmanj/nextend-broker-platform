package nextend.nl.broker.webtransport;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.incubator.codec.http3.DefaultHttp3SettingsFrame;
import io.netty.incubator.codec.http3.Http3;
import io.netty.incubator.codec.http3.Http3ServerConnectionHandler;
import io.netty.incubator.codec.http3.Http3SettingsFrame;
import io.netty.incubator.codec.quic.InsecureQuicTokenHandler;
import io.netty.incubator.codec.quic.Quic;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicSslContext;
import io.netty.incubator.codec.quic.QuicSslContextBuilder;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

public final class BrokerWebTransportBootstrap {
  private static final long SETTINGS_ENABLE_CONNECT_PROTOCOL = 0x08L;
  private static final long SETTINGS_H3_DATAGRAM = 0x33L;
  private static final long SETTINGS_WT_ENABLED = 0x2c7cf000L;
  private static final long DEFAULT_MAX_DATA = 10_000_000L;
  private static final long DEFAULT_MAX_STREAM_DATA = 1_000_000L;
  private static final long DEFAULT_MAX_BIDIRECTIONAL_STREAMS = 256L;
  private static final int DEFAULT_DATAGRAM_RECV_QUEUE_LENGTH = 128;
  private static final int DEFAULT_DATAGRAM_SEND_QUEUE_LENGTH = 128;

  private BrokerWebTransportBootstrap() {}

  public static BrokerWebTransportServerHandle bind(
      BrokerWebTransportProperties properties,
      BrokerWebTransportTlsMaterial certificate,
      BrokerWebTransportCertificateInfo certificateInfo)
      throws Exception {
    Quic.ensureAvailability();

    EventLoopGroup eventLoopGroup = new NioEventLoopGroup(1);
    try {
      ChannelFuture bindFuture =
          new Bootstrap()
              .group(eventLoopGroup)
              .channel(NioDatagramChannel.class)
              .handler(
                  Http3.newQuicServerCodecBuilder()
                      .sslContext(createServerSslContext(certificate))
                      .maxIdleTimeout(properties.getIdleTimeout().toMillis(), TimeUnit.MILLISECONDS)
                      .initialMaxData(DEFAULT_MAX_DATA)
                      .initialMaxStreamDataBidirectionalLocal(DEFAULT_MAX_STREAM_DATA)
                      .initialMaxStreamDataBidirectionalRemote(DEFAULT_MAX_STREAM_DATA)
                      .initialMaxStreamsBidirectional(DEFAULT_MAX_BIDIRECTIONAL_STREAMS)
                      .datagram(
                          DEFAULT_DATAGRAM_RECV_QUEUE_LENGTH, DEFAULT_DATAGRAM_SEND_QUEUE_LENGTH)
                      .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
                      .handler(
                          new ChannelInitializer<QuicChannel>() {
                            @Override
                            protected void initChannel(QuicChannel channel) {
                              channel
                                  .pipeline()
                                  .addLast(
                                      new Http3ServerConnectionHandler(
                                          new ChannelInitializer<QuicStreamChannel>() {
                                            @Override
                                            protected void initChannel(QuicStreamChannel channel) {
                                              channel
                                                  .pipeline()
                                                  .addLast(
                                                      new BrokerWebTransportRequestStreamHandler(
                                                          properties, certificateInfo));
                                            }
                                          },
                                          null,
                                          null,
                                          createWebTransportSettingsFrame(),
                                          true));
                            }
                          })
                      .build())
              .bind(new InetSocketAddress(properties.getBindAddress(), properties.getPort()))
              .syncUninterruptibly();

      Channel channel = bindFuture.channel();
      channel.closeFuture().addListener(__ -> eventLoopGroup.shutdownGracefully());
      return new BrokerWebTransportServerHandle(channel, eventLoopGroup, certificateInfo);
    } catch (Exception exception) {
      eventLoopGroup.shutdownGracefully().awaitUninterruptibly();
      throw exception;
    }
  }

  private static QuicSslContext createServerSslContext(BrokerWebTransportTlsMaterial certificate)
      throws Exception {
    return QuicSslContextBuilder.forServer(
            certificate.privateKeyFile(), null, certificate.certificateFile())
        .applicationProtocols(Http3.supportedApplicationProtocols())
        .build();
  }

  private static Http3SettingsFrame createWebTransportSettingsFrame() {
    DefaultHttp3SettingsFrame settings = new DefaultHttp3SettingsFrame();
    settings.put(SETTINGS_ENABLE_CONNECT_PROTOCOL, 1L);
    settings.put(SETTINGS_H3_DATAGRAM, 1L);
    settings.put(SETTINGS_WT_ENABLED, 1L);
    return settings;
  }
}
