package com.chatapp.security;

import com.chatapp.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Enumeration;

/** Builds TLS contexts from deployment-provided keystores and truststores. */
public final class TlsContextFactory {
    private static final Logger logger = LoggerFactory.getLogger(TlsContextFactory.class);
    private static final String KEYSTORE_TYPE = "PKCS12";
    private static final String TLS_PROTOCOL = "TLS";

    private TlsContextFactory() {}

    public static SSLContext createServerContext() {
        try {
            Path path = Path.of(AppConfig.getTlsKeyStorePath());
            char[] password = AppConfig.getTlsKeyStorePassword().toCharArray();
            try {
                KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE);
                try (InputStream in = Files.newInputStream(path)) {
                    keyStore.load(in, password);
                }
                validateServerCertificates(keyStore);
                KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                keyManagers.init(keyStore, password);
                SSLContext context = SSLContext.getInstance(TLS_PROTOCOL);
                context.init(keyManagers.getKeyManagers(), null, null);
                return context;
            } finally {
                java.util.Arrays.fill(password, '\0');
            }
        } catch (Exception e) {
            throw new IllegalStateException("Unable to initialize TLS server context.", e);
        }
    }

    private static void validateServerCertificates(KeyStore keyStore) throws Exception {
        Instant now = Instant.now();
        Instant earliestExpiry = null;
        boolean foundServerCertificate = false;
        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (!keyStore.isKeyEntry(alias)) continue;
            java.security.cert.Certificate certificate = keyStore.getCertificate(alias);
            if (certificate instanceof X509Certificate x509Certificate) {
                foundServerCertificate = true;
                x509Certificate.checkValidity();
                Instant expiry = x509Certificate.getNotAfter().toInstant();
                if (earliestExpiry == null || expiry.isBefore(earliestExpiry)) {
                    earliestExpiry = expiry;
                }
            }
        }

        if (!foundServerCertificate || earliestExpiry == null) {
            throw new IllegalStateException("TLS server keystore contains no X.509 key-entry certificate.");
        }

        long daysRemaining = Duration.between(now, earliestExpiry).toDays();
        int warningDays = AppConfig.getTlsCertificateExpiryWarningDays();
        if (daysRemaining <= warningDays) {
            logger.warn("TLS server certificate expires in {} days.", Math.max(0, daysRemaining));
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
                    java.util.Arrays.fill(password, '\0');
                }
            }
            SSLContext context = SSLContext.getInstance(TLS_PROTOCOL);
            context.init(null, trustManagers.getTrustManagers(), null);
            return context;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to initialize TLS client context.", e);
        }
    }
}
