package cn.gov.xivpn2;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
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

    public static final TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
        @Override
        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
        }

        @Override
        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
        }

        @Override
        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            return new java.security.cert.X509Certificate[]{};
        }
    }};

    private static final SSLContext trustAllSslContext;

    static {
        try {
            trustAllSslContext = SSLContext.getInstance("SSL");
            trustAllSslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException(e);
        }
    }

    public static final SSLSocketFactory trustAllSslSocketFactory = trustAllSslContext.getSocketFactory();
}
