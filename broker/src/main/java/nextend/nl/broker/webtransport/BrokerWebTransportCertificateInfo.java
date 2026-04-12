package nextend.nl.broker.webtransport;

import java.security.MessageDigest;
import java.security.cert.X509Certificate;

public final class BrokerWebTransportCertificateInfo {
  private final String browserHost;
  private final String certificateHash;

  private BrokerWebTransportCertificateInfo(String browserHost, String certificateHash) {
    this.browserHost = browserHost;
    this.certificateHash = certificateHash;
  }

  public static BrokerWebTransportCertificateInfo fromFingerprint(
      BrokerWebTransportProperties properties, String certificateHash) {
    return new BrokerWebTransportCertificateInfo(
        resolveBrowserHost(properties.getBindAddress()), certificateHash);
  }

  public static BrokerWebTransportCertificateInfo from(
      BrokerWebTransportProperties properties, BrokerWebTransportTlsMaterial certificate)
      throws Exception {
    return fromFingerprint(properties, sha256Fingerprint(certificate.certificate()));
  }

  public String browserHost() {
    return browserHost;
  }

  public String certificateHash() {
    return certificateHash;
  }

  public String browserUrlHint(BrokerWebTransportProperties properties) {
    return "wts://" + browserHost + ":" + properties.getPort() + properties.getPath();
  }

  public String exampleUrl(BrokerWebTransportProperties properties) {
    return "https://localhost/?brokerUrl="
        + browserUrlHint(properties)
        + "&brokerCertHash="
        + certificateHash;
  }

  private static String resolveBrowserHost(String bindAddress) {
    if (bindAddress == null
        || bindAddress.isBlank()
        || "0.0.0.0".equals(bindAddress)
        || "::".equals(bindAddress)) {
      return "127.0.0.1";
    }

    return bindAddress;
  }

  private static String sha256Fingerprint(X509Certificate certificate) throws Exception {
    byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
    StringBuilder builder = new StringBuilder(digest.length * 3 - 1);

    for (int index = 0; index < digest.length; index++) {
      if (index > 0) {
        builder.append(':');
      }

      builder.append(String.format("%02x", digest[index] & 0xff));
    }

    return builder.toString();
  }
}
