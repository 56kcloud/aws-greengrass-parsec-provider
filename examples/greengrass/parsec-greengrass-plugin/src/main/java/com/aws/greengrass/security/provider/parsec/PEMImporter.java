package com.aws.greengrass.security.provider.parsec;

/**
 *
 */
import java.io.*;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;

public class PEMImporter {

  public static SSLServerSocketFactory createSSLFactory(File privateKeyPem, File certificatePem, String password) throws Exception {
    final SSLContext context = SSLContext.getInstance("TLS");
    final KeyStore keystore = createKeyStore(privateKeyPem, certificatePem, password);
    final KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
    kmf.init(keystore, password.toCharArray());
    final KeyManager[] km = kmf.getKeyManagers();
    context.init(km, null, null);
    return context.getServerSocketFactory();
  }

  /**
   * Create a KeyStore from standard PEM files
   *
   * @param privateKeyPem the private key PEM file
   * @param certificatePem the certificate(s) PEM file
   * @param password to set to protect the private key
   */
  public static KeyStore createKeyStore(File privateKeyPem, File certificatePem, final String password)
      throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException, InvalidKeySpecException {
    final X509Certificate[] cert = createCertificates(certificatePem);
    final KeyStore keystore = KeyStore.getInstance("JKS");
    keystore.load(null);
    // Import private key
    final PrivateKey key = createPrivateKey(privateKeyPem);
    keystore.setKeyEntry(privateKeyPem.getName(), key, password.toCharArray(), cert);
    return keystore;
  }

  private static PrivateKey createPrivateKey(File privateKeyPem) throws IOException, InvalidKeySpecException, NoSuchAlgorithmException {
    final BufferedReader r = new BufferedReader(new FileReader(privateKeyPem));
    String s = r.readLine();
    if (s == null || !s.contains("BEGIN PRIVATE KEY")) {
      r.close();
      throw new IllegalArgumentException("No PRIVATE KEY found");
    }
    final StringBuilder b = new StringBuilder();
    s = "";
    while (s != null) {
      if (s.contains("END PRIVATE KEY")) {
        break;
      }
      b.append(s);
      s = r.readLine();
    }
    r.close();
    final String hexString = b.toString();
    final byte[] bytes = Base64.getDecoder().decode(hexString);
    return generatePrivateKeyFromDER(bytes);
  }

  private static X509Certificate[] createCertificates(File certificatePem) throws IOException, CertificateException {
    final List<X509Certificate> result = new ArrayList<X509Certificate>();
    final BufferedReader r = new BufferedReader(new FileReader(certificatePem));
    String s = r.readLine();
    if (s == null || !s.contains("BEGIN CERTIFICATE")) {
      r.close();
      throw new IllegalArgumentException("No CERTIFICATE found");
    }
    StringBuilder b = new StringBuilder();
    while (s != null) {
      if (s.contains("END CERTIFICATE")) {
        String hexString = b.toString();
        final byte[] bytes = Base64.getDecoder().decode(hexString);
        X509Certificate cert = generateCertificateFromDER(bytes);
        result.add(cert);
        b = new StringBuilder();
      } else {
        if (!s.startsWith("----")) {
          b.append(s);
        }
      }
      s = r.readLine();
    }
    r.close();

    return result.toArray(new X509Certificate[result.size()]);
  }

  private static RSAPrivateKey generatePrivateKeyFromDER(byte[] keyBytes) throws InvalidKeySpecException, NoSuchAlgorithmException {
    final PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
    final KeyFactory factory = KeyFactory.getInstance("RSA");
    return (RSAPrivateKey) factory.generatePrivate(spec);
  }

  private static X509Certificate generateCertificateFromDER(byte[] certBytes) throws CertificateException {
    final CertificateFactory factory = CertificateFactory.getInstance("X.509");
    return (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(certBytes));
  }

}