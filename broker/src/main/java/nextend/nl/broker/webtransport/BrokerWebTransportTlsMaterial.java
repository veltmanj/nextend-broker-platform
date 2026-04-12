package nextend.nl.broker.webtransport;

import java.io.File;
import java.io.FileWriter;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

public final class BrokerWebTransportTlsMaterial {
  private final X509Certificate certificate;
  private final File certificateFile;
  private final File privateKeyFile;
  private final Path tempDirectory;

  private BrokerWebTransportTlsMaterial(
      X509Certificate certificate, File certificateFile, File privateKeyFile, Path tempDirectory) {
    this.certificate = certificate;
    this.certificateFile = certificateFile;
    this.privateKeyFile = privateKeyFile;
    this.tempDirectory = tempDirectory;
  }

  public static BrokerWebTransportTlsMaterial create() throws Exception {
    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
    keyPairGenerator.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());
    KeyPair keyPair = keyPairGenerator.generateKeyPair();

    Instant now = Instant.now();
    Date notBefore = Date.from(now.minus(Duration.ofMinutes(5)));
    Date notAfter = Date.from(now.plus(Duration.ofDays(13)));
    X500Name subject = new X500Name("CN=localhost,O=Nextend Broker WebTransport");

    JcaX509v3CertificateBuilder certificateBuilder =
        new JcaX509v3CertificateBuilder(
            subject,
            new BigInteger(160, new SecureRandom()).abs(),
            notBefore,
            notAfter,
            subject,
            keyPair.getPublic());
    certificateBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
    certificateBuilder.addExtension(
        Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
    certificateBuilder.addExtension(
        Extension.extendedKeyUsage, false, new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth));
    certificateBuilder.addExtension(
        Extension.subjectAlternativeName,
        false,
        new GeneralNames(
            new GeneralName[] {
              new GeneralName(GeneralName.dNSName, "localhost"),
              new GeneralName(GeneralName.iPAddress, "127.0.0.1")
            }));

    ContentSigner signer =
        new JcaContentSignerBuilder("SHA256withECDSA").build(keyPair.getPrivate());
    X509CertificateHolder certificateHolder = certificateBuilder.build(signer);
    X509Certificate certificate =
        new JcaX509CertificateConverter().getCertificate(certificateHolder);
    certificate.verify(keyPair.getPublic());

    Path tempDirectory = Files.createTempDirectory("nextend-broker-webtransport-cert");
    File certificateFile = tempDirectory.resolve("localhost-cert.pem").toFile();
    File privateKeyFile = tempDirectory.resolve("localhost-key.pem").toFile();

    try (JcaPEMWriter writer = new JcaPEMWriter(new FileWriter(privateKeyFile))) {
      writer.writeObject(keyPair.getPrivate());
    }
    try (JcaPEMWriter writer = new JcaPEMWriter(new FileWriter(certificateFile))) {
      writer.writeObject(certificate);
    }

    certificateFile.deleteOnExit();
    privateKeyFile.deleteOnExit();
    tempDirectory.toFile().deleteOnExit();

    return new BrokerWebTransportTlsMaterial(
        certificate, certificateFile, privateKeyFile, tempDirectory);
  }

  public X509Certificate certificate() {
    return certificate;
  }

  public File certificateFile() {
    return certificateFile;
  }

  public File privateKeyFile() {
    return privateKeyFile;
  }

  public void delete() {
    try {
      Files.deleteIfExists(certificateFile.toPath());
      Files.deleteIfExists(privateKeyFile.toPath());
      Files.deleteIfExists(tempDirectory);
    } catch (Exception ignored) {
      // Best effort cleanup for temporary certificate material.
    }
  }
}
