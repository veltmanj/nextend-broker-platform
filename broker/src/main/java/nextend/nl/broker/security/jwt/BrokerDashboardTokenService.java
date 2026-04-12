package nextend.nl.broker.security.jwt;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BrokerDashboardTokenService {

  private static final String DASHBOARD_SCOPE = "DASHBOARD_READ";

  private final Algorithm algorithm;
  private final String issuer;
  private final Duration tokenValidity;

  public BrokerDashboardTokenService(
      @Value("${app.security.jwt.sKey}") String signingKeyHex,
      @Value("${app.security.jwt.issuer:nextend-broker}") String issuer,
      @Value("${app.security.jwt.dashboard-token-validity:PT15M}") Duration tokenValidity) {
    this.algorithm = Algorithm.HMAC512(HexFormat.of().parseHex(signingKeyHex));
    this.issuer = issuer;
    this.tokenValidity = tokenValidity;
  }

  public IssuedToken issueDashboardReadToken(String subject) {
    Instant issuedAt = Instant.now();
    Instant expiresAt = issuedAt.plus(tokenValidity);
    String token =
        JWT.create()
            .withIssuer(issuer)
            .withSubject(subject)
            .withIssuedAt(issuedAt)
            .withExpiresAt(expiresAt)
            .withJWTId(UUID.randomUUID().toString())
            .withClaim("scope", DASHBOARD_SCOPE)
            .sign(algorithm);

    return new IssuedToken(token, issuedAt, expiresAt, DASHBOARD_SCOPE);
  }

  public record IssuedToken(String token, Instant issuedAt, Instant expiresAt, String scope) {}
}
