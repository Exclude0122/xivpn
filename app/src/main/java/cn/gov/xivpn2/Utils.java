package cn.gov.xivpn2;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public class Utils {
    public static boolean isValidPort(String v) {
        if (v.isEmpty()) return false;
        try {
            int i = Integer.parseInt(v);
            return i <= 65535 && i >= 1;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Add new file to zip
     */
    public static void addFileToZip(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        CRC32 crc32 = new CRC32();
        crc32.update(bytes);

        ZipEntry e = new ZipEntry(name);
        e.setSize(bytes.length);
        e.setCrc(crc32.getValue());
        zip.putNextEntry(e);
        zip.write(bytes);

        zip.closeEntry();
    }

    /**
     * Add new file to zip
     */
    public static void addFileToZip(ZipOutputStream zip, String name, File file) throws IOException {
        byte[] bytes = FileUtils.readFileToByteArray(file);

        CRC32 crc32 = new CRC32();
        crc32.update(bytes);

        ZipEntry e = new ZipEntry(name);
        e.setCrc(crc32.getValue());
        e.setSize(bytes.length);
        zip.putNextEntry(e);
        zip.write(bytes);

        zip.closeEntry();
    }

    public static final X509TrustManager defaultTrustManager = initDefaultTrustManager();

    private static X509TrustManager initDefaultTrustManager() {
        try {
            TrustManagerFactory tmf =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null); // use system CA store

            for (TrustManager tm : tmf.getTrustManagers()) {
                if (tm instanceof X509TrustManager) {
                    return (X509TrustManager) tm;
                }
            }
            throw new IllegalStateException("No X509TrustManager available");
        } catch (Exception e) {
            throw new RuntimeException("Unable to initialize default trust manager", e);
        }
    }

    private static final SSLContext sslContext;

    static {
        try {
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{defaultTrustManager}, new java.security.SecureRandom());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static final SSLSocketFactory secureSocketFactory = sslContext.getSocketFactory();
}
