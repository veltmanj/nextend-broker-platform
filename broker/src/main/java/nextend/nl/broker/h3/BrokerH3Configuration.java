package nextend.nl.broker.h3;

import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.incubator.codec.http3.Http3;
import io.netty.incubator.codec.quic.QuicSslContext;
import io.netty.incubator.codec.quic.QuicSslContextBuilder;
import io.rsocket.broker.acceptor.BrokerSocketAcceptor;
import io.rsocket.transport.netty.Http3TransportConfig;
import io.rsocket.transport.netty.QuicTransportConfig;
import io.rsocket.transport.netty.server.Http3ServerTransport;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.rsocket.context.RSocketServerBootstrap;
import org.springframework.boot.rsocket.netty.NettyRSocketServer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BrokerH3Properties.class)
@ConditionalOnProperty(prefix = "nextend.broker.h3", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BrokerH3Configuration {

    @Bean
    SelfSignedCertificate brokerH3Certificate() throws Exception {
        return new SelfSignedCertificate();
    }

    @Bean
    DisposableBean brokerH3CertificateCleanup(
            @Qualifier("brokerH3Certificate") SelfSignedCertificate certificate
    ) {
        return certificate::delete;
    }

    @Bean
    RSocketServerBootstrap brokerH3ServerBootstrap(
            BrokerH3Properties properties,
            BrokerSocketAcceptor brokerSocketAcceptor,
            @Qualifier("brokerH3Certificate") SelfSignedCertificate certificate
    ) {
        return new RSocketServerBootstrap(
                acceptor -> {
                    Http3ServerTransport transport = Http3ServerTransport
                            .create(properties.getBindAddress(), properties.getPort())
                            .config(buildTransportConfig(properties, certificate));

                    return new NettyRSocketServer(
                            io.rsocket.core.RSocketServer.create(acceptor).bind(transport),
                            Duration.ofSeconds(30)
                    );
                },
                brokerSocketAcceptor
        );
    }

    private static Http3TransportConfig buildTransportConfig(
            BrokerH3Properties properties,
            SelfSignedCertificate certificate
    ) {
        try {
            QuicSslContext sslContext = QuicSslContextBuilder
                    .forServer(certificate.privateKey(), null, certificate.certificate())
                    .applicationProtocols(Http3.supportedApplicationProtocols())
                    .build();

            return Http3TransportConfig.builder()
                    .path(properties.getPath())
                    .quicConfig(
                            QuicTransportConfig.builder()
                                    .sslContext(sslContext)
                                    .build()
                    )
                    .build();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create HTTP/3 server configuration", exception);
        }
    }
}
