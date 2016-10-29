package com.digitalglobe.hydrate.security.module.cert;


import java.math.BigInteger;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.security.Security;

import java.time.LocalDate;
import java.time.temporal.TemporalAmount;
import java.time.ZoneOffset;
import java.util.Date;

// Requires BouncyCastle 1.54 PKIX/XMS/EAC/PKCS/OCSP/TSP/OPENSSL package
// https://www.bouncycastle.org/latest_releases.html
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

// Requires BouncyCastle 1.54 base provider package
// https://www.bouncycastle.org/latest_releases.html
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import com.digitalglobe.hydrate.logging.factory.HydrateLoggerFactory;
import com.digitalglobe.hydrate.logging.logger.HydrateLogger;
import com.safenetinc.luna.provider.LunaCertificateX509;

public class CertificateGenerator {
    
    private final static HydrateLogger LOGGER = HydrateLoggerFactory.getDefaultHydrateLogger(CertificateGenerator.class);
	    
  public static X509Certificate generateSelfSignedCertificate(KeyPair keyPair,
                                                    TemporalAmount relativeExpiration) throws Exception {
    Date expirationDate = Date.from(LocalDate.now().plus(relativeExpiration).atStartOfDay().toInstant(ZoneOffset.UTC));
    X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
        new X500Name("CN=localhost"),
        BigInteger.valueOf(System.currentTimeMillis()),
        new Date(System.currentTimeMillis()),
        expirationDate,
        new X500Name("CN=localhost"), //same since it is self-signed
        keyPair.getPublic()
    );
    addProviderIfNecessary();
    ContentSigner signer = new JcaContentSignerBuilder("SHA1WithRSA")
                                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                                .build(keyPair.getPrivate());

    X509CertificateHolder holder = certBuilder.build(signer);
    X509Certificate cert = new JcaX509CertificateConverter()
                                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                                .getCertificate(holder);

    return cert;
  }

  public static X509Certificate generateLunaSelfSignedCertificate(KeyPair keyPair,
                                                    TemporalAmount relativeExpiration) throws Exception {
    Date expirationDate = Date.from(LocalDate.now().plus(relativeExpiration).atStartOfDay().toInstant(ZoneOffset.UTC));
    return LunaCertificateX509.SelfSign(
      keyPair,
      "CN=localhost",
      BigInteger.valueOf(System.currentTimeMillis()),
      new Date(System.currentTimeMillis()),
      expirationDate
    );
  }

  private static void addProviderIfNecessary() {
    if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
      LOGGER.info("adding Bouncy Castle provider");
      Security.addProvider(new BouncyCastleProvider());
    }
  }
}

