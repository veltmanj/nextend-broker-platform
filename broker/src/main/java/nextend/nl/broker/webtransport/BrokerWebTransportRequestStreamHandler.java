package nextend.nl.broker.webtransport;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.incubator.codec.http3.DefaultHttp3DataFrame;
import io.netty.incubator.codec.http3.DefaultHttp3Headers;
import io.netty.incubator.codec.http3.DefaultHttp3HeadersFrame;
import io.netty.incubator.codec.http3.Http3DataFrame;
import io.netty.incubator.codec.http3.Http3Headers;
import io.netty.incubator.codec.http3.Http3HeadersFrame;
import io.netty.incubator.codec.http3.Http3RequestStreamInboundHandler;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class BrokerWebTransportRequestStreamHandler extends Http3RequestStreamInboundHandler {
  private static final Logger log =
      LoggerFactory.getLogger(BrokerWebTransportRequestStreamHandler.class);
  private static final CharSequence HEADER_PROTOCOL = ":protocol";
  private static final CharSequence HEADER_CONTENT_TYPE = "content-type";
  private static final CharSequence HEADER_CACHE_CONTROL = "cache-control";
  private static final CharSequence CONTENT_TYPE_JSON = "application/json";
  private static final String WEBTRANSPORT_PROTOCOL = "webtransport";
  private static final String WEBTRANSPORT_H3_PROTOCOL = "webtransport-h3";

  private final BrokerWebTransportCertificateInfo certificateInfo;
  private final BrokerWebTransportProperties properties;

  BrokerWebTransportRequestStreamHandler(
      BrokerWebTransportProperties properties, BrokerWebTransportCertificateInfo certificateInfo) {
    this.properties = properties;
    this.certificateInfo = certificateInfo;
  }

  @Override
  protected void channelRead(ChannelHandlerContext context, Http3HeadersFrame frame) {
    Http3Headers headers = frame.headers();
    CharSequence method = headers.method();
    CharSequence path = headers.path();
    CharSequence protocol = headers.get(HEADER_PROTOCOL);

    if (matchesGet(method, path, properties.getPath() + "/health")) {
      writeJsonResponse(
          context,
          "200",
          "{\"status\":\"UP\",\"transport\":\"http3\",\"mode\":\"broker-native-webtransport-scaffold\","
              + "\"path\":\""
              + properties.getPath()
              + "\"}");
      return;
    }

    if (matchesGet(method, path, properties.getPath() + "/info")) {
      writeJsonResponse(
          context,
          "200",
          "{\"mode\":\"broker-native-webtransport-scaffold\","
              + "\"transport\":\"http3\","
              + "\"backend\":\""
              + properties.getBackend().getPropertyValue()
              + "\","
              + "\"bindAddress\":\""
              + properties.getBindAddress()
              + "\","
              + "\"port\":"
              + properties.getPort()
              + ",\"path\":\""
              + properties.getPath()
              + "\",\"brokerCertHash\":\""
              + certificateInfo.certificateHash()
              + "\",\"browserUrlHint\":\""
              + certificateInfo.browserUrlHint(properties)
              + "\",\"maxSessions\":"
              + properties.getMaxSessions()
              + "}");
      return;
    }

    if (matchesConnect(method, path, protocol, properties.getPath())) {
      log.info(
          "Accepted experimental broker WebTransport CONNECT on {} with protocol {}",
          path,
          protocol);
      writeConnectAccepted(context);
      return;
    }

    if (matchesPath(path, properties.getPath())) {
      writeJsonResponse(
          context,
          "405",
          "{\"error\":\"method_not_allowed\","
              + "\"message\":\"Use CONNECT with :protocol=webtransport.\"}");
      return;
    }

    writeJsonResponse(
        context,
        "404",
        "{\"error\":\"not_found\",\"message\":\"No broker WebTransport route matched.\"}");
  }

  @Override
  protected void channelRead(ChannelHandlerContext context, Http3DataFrame frame) {
    frame.release();
  }

  @Override
  protected void channelInputClosed(ChannelHandlerContext context) {
    context.close();
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
    context.close();
  }

  private static boolean matchesGet(CharSequence method, CharSequence path, String expectedPath) {
    return equalsAscii(method, "GET") && matchesPath(path, expectedPath);
  }

  private static boolean matchesConnect(
      CharSequence method, CharSequence path, CharSequence protocol, String expectedPath) {
    return equalsAscii(method, "CONNECT")
        && matchesWebTransportProtocol(protocol)
        && matchesPath(path, expectedPath);
  }

  private static boolean matchesPath(CharSequence actualPath, String expectedPath) {
    return actualPath != null && expectedPath.contentEquals(actualPath);
  }

  private static boolean equalsAscii(CharSequence actual, String expected) {
    return actual != null && expected.equalsIgnoreCase(actual.toString());
  }

  private static boolean matchesWebTransportProtocol(CharSequence protocol) {
    return equalsAscii(protocol, WEBTRANSPORT_PROTOCOL)
        || equalsAscii(protocol, WEBTRANSPORT_H3_PROTOCOL);
  }

  private static void writeJsonResponse(ChannelHandlerContext context, String status, String body) {
    ByteBuf buffer = Unpooled.copiedBuffer(body, CharsetUtil.UTF_8);
    DefaultHttp3Headers headers = new DefaultHttp3Headers();
    headers.status(status);
    headers.add(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON);
    headers.add(HEADER_CACHE_CONTROL, "no-store");
    headers.add("content-length", Integer.toString(buffer.readableBytes()));

    context.write(new DefaultHttp3HeadersFrame(headers));
    context
        .writeAndFlush(new DefaultHttp3DataFrame(buffer))
        .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT)
        .addListener(ChannelFutureListener.CLOSE);
  }

  private static void writeConnectAccepted(ChannelHandlerContext context) {
    DefaultHttp3Headers headers = new DefaultHttp3Headers();
    headers.status("200");
    headers.add(HEADER_CACHE_CONTROL, "no-store");
    context.writeAndFlush(new DefaultHttp3HeadersFrame(headers));
  }
}
