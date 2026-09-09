package com.chatapp.security;

import com.chatapp.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Enumeration;

/** Builds TLS contexts from deployment-provided keystores and truststores. */
public final class TlsContextFactory {
    private static final Logger logger = LoggerFactory.getLogger(TlsContextFactory.class);
    private static final String KEYSTORE_TYPE = "PKCS12";
    private static final String TLS_PROTOCOL = "TLS";
    private static final Duration CERTIFICATE_RENEWAL_WARNING = Duration.ofDays(30);

    private TlsContextFactory() {}

    public static SSLContext createServerContext() {
        try {
            Path path = Path.of(AppConfig.getTlsKeyStorePath());
            char[] password = AppConfig.getTlsKeyStorePassword().toCharArray();
            try {
                KeyStore keyStore = loadKeyStore(path, password);
                validateServerCertificates(keyStore);
                KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                keyManagers.init(keyStore, password);
                SSLContext context = SSLContext.getInstance(TLS_PROTOCOL);
                context.init(keyManagers.getKeyManagers(), null, null);
                return context;
            } finally {
                Arrays.fill(password, '\0');
            }
        } catch (IOException | GeneralSecurityException | RuntimeException e) {
            throw new IllegalStateException("Unable to initialize TLS server context.", e);
        }
    }

    /**
     * Creates a TLS client context for the local container health probe.
     * Trust is limited to the X.509 certificates presented by the configured server keystore.
     */
    public static SSLContext createHealthCheckClientContext() {
        try {
            Path path = Path.of(AppConfig.getTlsKeyStorePath());
            char[] password = AppConfig.getTlsKeyStorePassword().toCharArray();
            try {
                KeyStore serverKeyStore = loadKeyStore(path, password);
                validateServerCertificates(serverKeyStore);

                KeyStore trustStore = KeyStore.getInstance(KEYSTORE_TYPE);
                trustStore.load(null, null);
                Enumeration<String> aliases = serverKeyStore.aliases();
                int certificateIndex = 0;
                while (aliases.hasMoreElements()) {
                    String alias = aliases.nextElement();
                    if (!serverKeyStore.isKeyEntry(alias)) continue;
                    Certificate certificate = serverKeyStore.getCertificate(alias);
                    if (certificate instanceof X509Certificate) {
                        trustStore.setCertificateEntry("health-check-" + certificateIndex++, certificate);
                    }
                }
                if (certificateIndex == 0) {
                    throw new GeneralSecurityException("TLS server keystore does not contain a trusted health-check certificate.");
                }

                TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                trustManagers.init(trustStore);
                SSLContext context = SSLContext.getInstance(TLS_PROTOCOL);
                context.init(null, trustManagers.getTrustManagers(), null);
                return context;
            } finally {
                Arrays.fill(password, '\0');
            }
        } catch (IOException | GeneralSecurityException | RuntimeException e) {
            throw new IllegalStateException("Unable to initialize TLS health-check client context.", e);
        }
    }

    private static KeyStore loadKeyStore(Path path, char[] password) throws IOException, GeneralSecurityException {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE);
        try (InputStream in = Files.newInputStream(path)) {
            keyStore.load(in, password);
        }
        return keyStore;
    }

    private static void validateServerCertificates(KeyStore keyStore) throws GeneralSecurityException {
        Instant now = Instant.now();
        Instant renewalDeadline = now.plus(CERTIFICATE_RENEWAL_WARNING);
        boolean foundServerCertificate = false;
        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (!keyStore.isKeyEntry(alias)) continue;
            Certificate certificate = keyStore.getCertificate(alias);
            if (!(certificate instanceof X509Certificate x509Certificate)) continue;
            foundServerCertificate = true;
            try {
                x509Certificate.checkValidity();
            } catch (java.security.cert.CertificateExpiredException e) {
                throw new GeneralSecurityException("TLS server certificate is expired for alias " + alias + ".", e);
            } catch (java.security.cert.CertificateNotYetValidException e) {
                throw new GeneralSecurityException("TLS server certificate is not yet valid for alias " + alias + ".", e);
            }
            Instant notAfter = x509Certificate.getNotAfter().toInstant();
            if (!notAfter.isAfter(renewalDeadline)) {
                long daysRemaining = Math.max(0L, Duration.between(now, notAfter).toDays());
                logger.warn("TLS server certificate for alias {} expires in {} days; renew it before expiry.", alias, daysRemaining);
            }
        }
        if (!foundServerCertificate) {
            throw new GeneralSecurityException("TLS server keystore does not contain an X.509 key certificate.");
        }
    }

    public static SSLContext createClientContext() {
        try {
            String trustStorePath = AppConfig.getTlsTrustStorePath();
            TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            if (trustStorePath.isBlank()) {
                trustManagers.init((KeyStore) null);
            } else {
                char[] password = AppConfig.getTlsTrustStorePassword().toCharArray();
                try {
                    KeyStore trustStore = KeyStore.getInstance(KEYSTORE_TYPE);
                    try (InputStream in = Files.newInputStream(Path.of(trustStorePath))) {
                        trustStore.load(in, password);
                    }
                    trustManagers.init(trustStore);
                } finally {
                    Arrays.fill(password, '\0');
                }
            }
            SSLContext context = SSLContext.getInstance(TLS_PROTOCOL);
            context.init(null, trustManagers.getTrustManagers(), null);
            return context;
        } catch (IOException | GeneralSecurityException | RuntimeException e) {
            throw new IllegalStateException("Unable to initialize TLS client context.", e);
        }
    }
}
