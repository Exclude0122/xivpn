package cn.gov.xivpn2.service;

import static android.content.Context.MODE_PRIVATE;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import javax.net.ssl.X509TrustManager;

import cn.gov.xivpn2.NotificationID;
import cn.gov.xivpn2.R;
import cn.gov.xivpn2.Utils;
import cn.gov.xivpn2.database.AppDatabase;
import cn.gov.xivpn2.database.Proxy;
import cn.gov.xivpn2.database.Rules;
import cn.gov.xivpn2.database.Subscription;
import cn.gov.xivpn2.ui.CrashLogActivity;
import cn.gov.xivpn2.xrayconfig.HttpUpgradeSettings;
import cn.gov.xivpn2.xrayconfig.Outbound;
import cn.gov.xivpn2.xrayconfig.RealitySettings;
import cn.gov.xivpn2.xrayconfig.ShadowsocksServerSettings;
import cn.gov.xivpn2.xrayconfig.ShadowsocksSettings;
import cn.gov.xivpn2.xrayconfig.StreamSettings;
import cn.gov.xivpn2.xrayconfig.TLSSettings;
import cn.gov.xivpn2.xrayconfig.TrojanServerSettings;
import cn.gov.xivpn2.xrayconfig.TrojanSettings;
import cn.gov.xivpn2.xrayconfig.VlessServerSettings;
import cn.gov.xivpn2.xrayconfig.VlessSettings;
import cn.gov.xivpn2.xrayconfig.VlessUser;
import cn.gov.xivpn2.xrayconfig.VmessServerSettings;
import cn.gov.xivpn2.xrayconfig.VmessSettings;
import cn.gov.xivpn2.xrayconfig.VmessShare;
import cn.gov.xivpn2.xrayconfig.VmessUser;
import cn.gov.xivpn2.xrayconfig.WsSettings;
import cn.gov.xivpn2.xrayconfig.XHttpSettings;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SubscriptionWork extends Worker {
    private static final String TAG = "SubscriptionWorker";

    public SubscriptionWork(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    private static Map<String, String> splitQuery(String query) throws UnsupportedEncodingException {
        Map<String, String> query_pairs = new LinkedHashMap<String, String>();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            query_pairs.put(URLDecoder.decode(pair.substring(0, idx), "UTF-8"), URLDecoder.decode(pair.substring(idx + 1), "UTF-8"));
        }
        return query_pairs;
    }

    private static String quote(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private static String queryFromMap(Map<String, String> queries) {
        StringBuilder s = new StringBuilder();

        boolean first = true;

        for (Map.Entry<String, String> entry : queries.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }

            if (first) {
                first = false;
                s.append("?");
            } else {
                s.append("&");
            }

            s.append(quote(entry.getKey()));
            s.append("=");
            s.append(quote(entry.getValue()));
        }

        return s.toString();
    }

    private static String nullable(String s, String d) {
        if (d == null) throw new NullPointerException("d must not be null");

        if (s == null) {
            return d;
        }
        return s;
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.i(TAG, "doWork");

        // start foreground service

        Notification foregroundNotification = new Notification.Builder(getApplicationContext(), "XiVPNService")
                .setContentText(getApplicationContext().getString(R.string.subscription_updating))
                .setOngoing(true)
                .setSmallIcon(R.drawable.baseline_refresh_24)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            setForegroundAsync(new ForegroundInfo(NotificationID.getID(), foregroundNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE));
        } else {
            setForegroundAsync(new ForegroundInfo(NotificationID.getID(), foregroundNotification));
        }

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Log.e(TAG, "sleep", e);
            return Result.success();
        }

        OkHttpClient.Builder builder = new OkHttpClient.Builder();

        if (PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("subscription_no_verify", false)) {
            Log.w(TAG, "subscription no verify");
            builder.sslSocketFactory(Utils.trustAllSslSocketFactory, ((X509TrustManager) Utils.trustAllCerts[0]));
            builder.hostnameVerifier((hostname, session) -> true);
        }

        OkHttpClient client = builder.build();
        for (Subscription subscription : AppDatabase.getInstance().subscriptionDao().findAll()) {

            // update subscription

            Log.i(TAG, "begin update: " + subscription.label + ", " + subscription.url);

            Response response = null;
            String body = null;
            int statusCode = -1;
            try {

                Request request = new Request.Builder()
                        .url(subscription.url)
                        .build();

                response = client.newCall(request).execute();


                // delete old proxies
                AppDatabase.getInstance().proxyDao().deleteBySubscription(subscription.label);

                // parse subscription and add proxies
                statusCode = response.code();
                body = response.body().string();
                parse(body, subscription.label);

                Notification notification = new Notification.Builder(getApplicationContext(), "XiVPNSubscriptions")
                        .setContentTitle(getApplicationContext().getString(R.string.subscription_updated) + subscription.label)
                        .setSmallIcon(R.drawable.outline_info_24)
                        .build();
                getApplicationContext().getSystemService(NotificationManager.class).notify(NotificationID.getID(), notification);

            } catch (Exception e) {

                Log.e(TAG, "update " + subscription.label, e);

                // save error message to a file
                String fileName = "subscription_" + UUID.randomUUID() + ".txt";
                File file = new File(getApplicationContext().getCacheDir(), fileName);

                try {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Could not update subscription: ").append(subscription.label).append("\n\n");

                    sb.append(e.getClass().getSimpleName()).append(": ").append(e.getMessage()).append("\n\n");

                    if (statusCode > 0) {
                        sb.append("Response status code: ").append(statusCode).append("\n\n");
                        if (body != null) {
                            sb.append("Response Body:\n").append(body).append("\n\n");
                        }
                    }
                    FileOutputStream outputStream = new FileOutputStream(file);
                    outputStream.write(sb.toString().getBytes(StandardCharsets.UTF_8));
                    outputStream.close();
                } catch (IOException e2) {
                    Log.e(TAG, "write crash log", e2);
                }

                Intent intent = new Intent(getApplicationContext(), CrashLogActivity.class);
                intent.putExtra("FILE", fileName);

                // post error message
                Notification notification = new Notification.Builder(getApplicationContext(), "XiVPNSubscriptions")
                        .setContentTitle(getApplicationContext().getString(R.string.subscription_error) + subscription.label)
                        .setContentText(getApplicationContext().getString(R.string.click_for_details))
                        .setSmallIcon(R.drawable.baseline_error_24)
                        .setContentIntent(PendingIntent.getActivity(getApplicationContext(), NotificationID.getID(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE))
                        .build();


                getApplicationContext().getSystemService(NotificationManager.class).notify(NotificationID.getID(), notification);

            } finally {
                if (response != null) {
                    try {
                        response.close();
                    } catch (Exception e) {
                        Log.e(TAG, "close response", e);
                    }
                }
            }

        }

        try {
            Rules.resetDeletedProxies(getApplicationContext().getSharedPreferences("XIVPN", MODE_PRIVATE), getApplicationContext().getFilesDir());
        } catch (IOException e) {
            Log.e(TAG, "reset deleted proxies", e);
        }

        XiVPNService.markConfigStale(getApplicationContext());

        Log.i(TAG, "doWork finish");
        return Result.success();
    }

    /**
     * Parse subscription text and add proxies
     *
     * @param text base64 encoded, one line per proxy
     */
    private void parse(String text, String label) {
        // decode base64
        String textDecoded = new String(Base64.decode(text, Base64.DEFAULT), StandardCharsets.UTF_8);

        String[] lines = textDecoded.split("\\r?\\n");

        for (String line : lines) {
            line = line.replace(" ", "%20").replace("|", "%7c");
            Log.i(TAG, "parse " + line);

            parseLine(line, label);
        }
    }

    public static boolean parseLine(String line, String subscription) {
        Proxy proxy = null;

        try {
            if (line.startsWith("ss://")) {
                proxy = parseShadowsocks(line);
            } else if (line.startsWith("vmess://")) {
                proxy = parseVmess(line);
            } else if (line.startsWith("trojan://")) {
                proxy = parseTrojan(line);
            } else if (line.startsWith("vless://")) {
                proxy = parseVless(line);
            }
        } catch (Exception e) {
            Log.e(TAG, "parse " + subscription + " " + line, e);
        }

        if (proxy == null) return false;

        proxy.subscription = subscription;

        int n = 2;
        String baseLabel = proxy.label;

        if (proxy.label.matches("[\\s\\S]* \\d+$")) {
            int splitAt = proxy.label.lastIndexOf(" ");
            n = Integer.parseInt(proxy.label.substring(splitAt + 1)) + 1;
            baseLabel = proxy.label.substring(0, splitAt);
        }

        while (AppDatabase.getInstance().proxyDao().find(proxy.label, proxy.subscription) != null) {
            // add number to label if already exists
            proxy.label = baseLabel + " " + n;
            n++;
        }
        AppDatabase.getInstance().proxyDao().add(proxy);

        return true;
    }

    private static String quoteHost(String host) {
        if ((host.indexOf(':') >= 0) && !host.startsWith("[") && !host.endsWith("]")) {
            return "[" + host + "]";
        }
        return host;
    }

    public static class MarshalProxyException extends Exception {
        public int resId;

        public MarshalProxyException(int resId, String m) {
            super(m);
            this.resId = resId;
        }
    }

    public static String marshalProxy(Proxy proxy) throws MarshalProxyException {
        try {
            if (proxy.protocol.equals("shadowsocks")) {
                Outbound<ShadowsocksSettings> outbound = new Gson().fromJson(proxy.config, new TypeToken<>() {
                });

                ShadowsocksServerSettings server = outbound.settings.servers.get(0);

                return "ss://"
                        + server.method + ":" + quote(server.password)
                        + "@" + quoteHost(server.address) + ":" + server.port
                        + "#" + quote(proxy.label);
            } else if (proxy.protocol.equals("vmess")) {
                Outbound<VmessSettings> outbound = new Gson().fromJson(proxy.config, new TypeToken<>() {
                });

                VmessServerSettings server = outbound.settings.vnext.get(0);
                VmessUser vmessUser = server.users.get(0);

                VmessShare vmessShare = new VmessShare();
                vmessShare.ps = proxy.label;
                vmessShare.add = server.address;
                vmessShare.port = Integer.valueOf(server.port).toString();

                vmessShare.security = vmessUser.security;
                vmessShare.id = vmessUser.id;

                vmessShare.network = outbound.streamSettings.network;
                if (vmessShare.network.equals("ws")) {
                    vmessShare.path = outbound.streamSettings.wsSettings.path;
                    vmessShare.host = outbound.streamSettings.wsSettings.host;
                } else if (vmessShare.network.equals("httpupgrade")) {
                    vmessShare.path = outbound.streamSettings.httpupgradeSettings.path;
                    vmessShare.host = outbound.streamSettings.httpupgradeSettings.host;
                } else if (vmessShare.network.equals("xhttp")) {
                    vmessShare.path = outbound.streamSettings.xHttpSettings.path;
                    vmessShare.host = outbound.streamSettings.xHttpSettings.host;
                    vmessShare.type = outbound.streamSettings.xHttpSettings.mode;
                }

                vmessShare.tls = outbound.streamSettings.security;
                if (vmessShare.tls != null) {
                    if (vmessShare.tls.equals("tls")) {
                        vmessShare.sni = outbound.streamSettings.tlsSettings.serverName;
                        vmessShare.alpn = String.join(",", outbound.streamSettings.tlsSettings.alpn);
                        vmessShare.fingerprint = outbound.streamSettings.tlsSettings.fingerprint;
                    } else {
                        throw new MarshalProxyException(R.string.security_option_unsupported, vmessShare.tls);
                    }
                }

                String link = new Gson().toJson(vmessShare);
                return "vmess://" + Base64.encodeToString(link.getBytes(StandardCharsets.UTF_8), Base64.URL_SAFE | Base64.NO_WRAP);
            } else if (proxy.protocol.equals("trojan")) {
                Outbound<TrojanSettings> outbound = new Gson().fromJson(proxy.config, new TypeToken<>() {
                });

                TrojanServerSettings server = outbound.settings.servers.get(0);
                TLSSettings tlsSettings = outbound.streamSettings.tlsSettings;

                if (!outbound.streamSettings.security.equals("tls")) {
                    throw new MarshalProxyException(R.string.security_option_unsupported, outbound.streamSettings.security);
                }

                Map<String, String> queries = new LinkedHashMap<>();
                queries.put("sni", tlsSettings.serverName);
                queries.put("alpn", String.join(",", tlsSettings.alpn));
                queries.put("fp", tlsSettings.fingerprint);

                return "trojan://"
                        + quote(server.password)
                        + "@" + quoteHost(server.address) + ":" + server.port
                        + queryFromMap(queries) + "#" + quote(proxy.label);
            } else if (proxy.protocol.equals("vless")) {
                Outbound<VlessSettings> outbound = new Gson().fromJson(proxy.config, new TypeToken<>() {
                });

                VlessServerSettings server = outbound.settings.vnext.get(0);
                VlessUser vlessUser = server.users.get(0);
                StreamSettings streamSettings = outbound.streamSettings;

                Map<String, String> queries = new LinkedHashMap<>();

                queries.put("type", streamSettings.network);
                if (streamSettings.network.equals("ws")) {
                    queries.put("host", streamSettings.wsSettings.host);
                    queries.put("path", streamSettings.wsSettings.path);
                } else if (streamSettings.network.equals("xhttp")) {
                    queries.put("host", streamSettings.xHttpSettings.host);
                    queries.put("path", streamSettings.xHttpSettings.path);
                    queries.put("mode", streamSettings.xHttpSettings.mode);
                } else if (streamSettings.network.equals("httpupgrade")) {
                    queries.put("host", streamSettings.httpupgradeSettings.host);
                    queries.put("path", streamSettings.httpupgradeSettings.path);
                }

                queries.put("encryption", vlessUser.encryption.equals("none") ? "" : vlessUser.encryption);
                queries.put("flow", vlessUser.flow);

                queries.put("security", nullable(streamSettings.security, ""));
                if (streamSettings.security != null) {
                    if (streamSettings.security.equals("tls")) {
                        queries.put("sni", streamSettings.tlsSettings.serverName);
                        queries.put("fp", streamSettings.tlsSettings.fingerprint);
                        queries.put("alpn", String.join(",", streamSettings.tlsSettings.alpn));
                    } else if (streamSettings.security.equals("reality")) {
                        queries.put("sni", streamSettings.realitySettings.serverName);
                        queries.put("fp", streamSettings.realitySettings.fingerprint);
                        queries.put("pbk", streamSettings.realitySettings.publicKey);
                        queries.put("sid", streamSettings.realitySettings.shortId);
                    }
                }

                return "vless://"
                        + vlessUser.id
                        + "@" + quoteHost(server.address) + ":" + server.port
                        + queryFromMap(queries) + "#" + quote(proxy.label);
            } else {
                throw new MarshalProxyException(R.string.protocol_unsupported, proxy.protocol);
            }
        } catch (JsonSyntaxException | IndexOutOfBoundsException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Parse shadowsocks URI
     */
    private static Proxy parseShadowsocks(String line) throws UnsupportedEncodingException {
        Proxy proxy = new Proxy();
        proxy.protocol = "shadowsocks";

        URI uri = URI.create(line);

        String userInfo = nullable(uri.getRawUserInfo(), "");
        String hostname = uri.getHost();
        String port = String.valueOf(uri.getPort());
        String plugin = nullable(uri.getRawQuery(), "");
        String label = nullable(uri.getRawFragment(), "SS");

        Outbound<ShadowsocksSettings> outbound = new Outbound<>();
        outbound.settings = new ShadowsocksSettings();
        outbound.protocol = "shadowsocks";

        // network
        outbound.streamSettings = new StreamSettings();
        outbound.streamSettings.network = "tcp";
        outbound.streamSettings.security = "none";

        // shadowssocks server

        ShadowsocksServerSettings server = new ShadowsocksServerSettings();
        outbound.settings.servers.add(server);

        if (!userInfo.contains(":")) {
            userInfo = new String(Base64.decode(userInfo, Base64.URL_SAFE), StandardCharsets.UTF_8);
        } else {
            // userinfo is percent encoded if it is not base64 encoded
            userInfo = URLDecoder.decode(userInfo, "UTF-8");
        }

        String[] parts = userInfo.split(":", 2);
        server.method = parts[0];
        server.password = parts[1];

        server.address = hostname;
        server.port = Integer.parseInt(port);
        server.out = false;

        proxy.label = URLDecoder.decode(label, "UTF-8");
        proxy.config = new Gson().toJson(outbound);

        return proxy;
    }

    private static Proxy parseVmess(String line) {
        if (!line.startsWith("vmess://")) {
            throw new IllegalArgumentException("invalid vmess " + line);
        }

        line = line.substring(8);

        String json = new String(Base64.decode(line, Base64.URL_SAFE), StandardCharsets.UTF_8);

        Gson gson = new GsonBuilder().create();
        VmessShare vmessShare = gson.fromJson(json, VmessShare.class);

        Proxy proxy = new Proxy();
        proxy.label = vmessShare.ps;
        proxy.protocol = "vmess";

        Outbound<VmessSettings> outbound = new Outbound<>();
        outbound.protocol = "vmess";
        outbound.settings = new VmessSettings();

        VmessServerSettings server = new VmessServerSettings();
        outbound.settings.vnext.add(server);
        server.address = vmessShare.add;
        server.port = Integer.parseInt(vmessShare.port);

        VmessUser vmessUser = new VmessUser();
        vmessUser.security = vmessShare.security;
        vmessUser.id = vmessShare.id;
        server.users.add(vmessUser);

        outbound.streamSettings = new StreamSettings();
        outbound.streamSettings.network = vmessShare.network;
        if (vmessShare.network.equals("ws")) {
            outbound.streamSettings.wsSettings = new WsSettings();
            outbound.streamSettings.wsSettings.path = vmessShare.path;
            outbound.streamSettings.wsSettings.host = vmessShare.host;
        }
        if (vmessShare.network.equals("httpupgrade")) {
            outbound.streamSettings.httpupgradeSettings = new HttpUpgradeSettings();
            outbound.streamSettings.httpupgradeSettings.host = vmessShare.host;
            outbound.streamSettings.httpupgradeSettings.path = vmessShare.path;
        }
        if (vmessShare.network.equals("xhttp")) {
            outbound.streamSettings.xHttpSettings = new XHttpSettings();
            outbound.streamSettings.xHttpSettings.host = vmessShare.host;
            outbound.streamSettings.xHttpSettings.path = vmessShare.path;
            outbound.streamSettings.xHttpSettings.mode = !vmessShare.type.isEmpty() ? vmessShare.type : "auto";
            outbound.streamSettings.xHttpSettings.downloadSettings = null;

            if (!vmessShare.type.equals("packet-up") && !vmessShare.type.equals("stream-up") && !vmessShare.type.equals("auto") && !vmessShare.type.equals("stream-one")) {
                throw new IllegalArgumentException("unsupported type " + vmessShare.type);
            }
        }
        outbound.streamSettings.security = vmessShare.tls;
        if (outbound.streamSettings.security.equals("tls")) {
            outbound.streamSettings.tlsSettings = new TLSSettings();
            outbound.streamSettings.tlsSettings.serverName = vmessShare.sni;
            outbound.streamSettings.tlsSettings.alpn = vmessShare.alpn.split(",");
            outbound.streamSettings.tlsSettings.fingerprint = vmessShare.fingerprint;
        }

        proxy.config = gson.toJson(outbound);

        return proxy;
    }

    private static Proxy parseTrojan(String line) throws MalformedURLException, URISyntaxException, UnsupportedEncodingException {
        if (!line.startsWith("trojan://")) {
            throw new IllegalArgumentException("invalid trojan " + line);
        }

        URI uri = new URI(line);
        Map<String, String> query = splitQuery(nullable(uri.getRawQuery(), ""));

        Proxy proxy = new Proxy();
        proxy.label = URLDecoder.decode(nullable(uri.getFragment(), "TROJAN"), "UTF-8");
        proxy.protocol = "trojan";

        Outbound<TrojanSettings> outbound = new Outbound<>();
        outbound.protocol = "trojan";
        outbound.settings = new TrojanSettings();

        TrojanServerSettings trojanServerSettings = new TrojanServerSettings();
        trojanServerSettings.address = uri.getHost();
        trojanServerSettings.port = uri.getPort();
        trojanServerSettings.password = nullable(uri.getUserInfo(), "");
        outbound.settings.servers.add(trojanServerSettings);

        outbound.streamSettings = new StreamSettings();
        outbound.streamSettings.network = "tcp";
        outbound.streamSettings.security = "tls";
        outbound.streamSettings.tlsSettings = new TLSSettings();
        outbound.streamSettings.tlsSettings.serverName = query.getOrDefault("sni", uri.getHost());
        outbound.streamSettings.tlsSettings.alpn = !query.containsKey("alpn") ? new String[]{"h2", "http/1.1"} : query.get("alpn").split(",");
        outbound.streamSettings.tlsSettings.fingerprint = query.getOrDefault("fp", "chrome");

        proxy.config = new Gson().toJson(outbound);

        return proxy;

    }

    private static Proxy parseVless(String line) throws URISyntaxException, UnsupportedEncodingException {
        if (!line.startsWith("vless://")) {
            throw new IllegalArgumentException("invalid vless " + line);
        }

        URI uri = new URI(line);

        Proxy proxy = new Proxy();
        proxy.label = URLDecoder.decode(nullable(uri.getFragment(), "VLESS"), "UTF-8");
        proxy.protocol = "vless";

        Outbound<VlessSettings> outbound = new Outbound<>();
        outbound.protocol = "vless";
        outbound.settings = new VlessSettings();
        outbound.settings.vnext = new ArrayList<>();

        Map<String, String> query = splitQuery(nullable(uri.getRawQuery(), ""));

        VlessServerSettings vlessServerSettings = new VlessServerSettings();
        vlessServerSettings.address = uri.getHost();
        vlessServerSettings.port = uri.getPort();
        VlessUser vlessUser = new VlessUser();
        vlessUser.id = nullable(uri.getUserInfo(), "");
        vlessUser.encryption = query.getOrDefault("encryption", "none");
        vlessUser.flow = nullable(query.get("flow"), "");
        vlessServerSettings.users.add(vlessUser);

        outbound.settings.vnext.add(vlessServerSettings);

        outbound.streamSettings = new StreamSettings();
        outbound.streamSettings.network = "tcp";

        if ("ws".equals(query.get("type"))) {
            outbound.streamSettings.network = "ws";
            outbound.streamSettings.wsSettings = new WsSettings();
            outbound.streamSettings.wsSettings.host = query.getOrDefault("host", "");
            outbound.streamSettings.wsSettings.path = query.getOrDefault("path", "/");
        } else if ("xhttp".equals(query.get("type"))) {
            outbound.streamSettings.network = "xhttp";
            outbound.streamSettings.xHttpSettings = new XHttpSettings();
            outbound.streamSettings.xHttpSettings.host = query.getOrDefault("host", "");
            outbound.streamSettings.xHttpSettings.path = query.getOrDefault("path", "/");
            outbound.streamSettings.xHttpSettings.mode = query.getOrDefault("mode", "packet-up");
            outbound.streamSettings.xHttpSettings.downloadSettings = null;
        } else if ("httpupgrade".equals(query.get("type"))) {
            outbound.streamSettings.network = "httpupgrade";
            outbound.streamSettings.httpupgradeSettings = new HttpUpgradeSettings();
            outbound.streamSettings.httpupgradeSettings.host = query.getOrDefault("host", "");
            outbound.streamSettings.httpupgradeSettings.path = query.getOrDefault("path", "/");
        }

        outbound.streamSettings.security = "none";
        if (query.getOrDefault("security", "").equals("tls")) {
            outbound.streamSettings.security = "tls";
            outbound.streamSettings.tlsSettings = new TLSSettings();
            outbound.streamSettings.tlsSettings.serverName = query.getOrDefault("sni", uri.getHost());
            outbound.streamSettings.tlsSettings.fingerprint = query.getOrDefault("fp", "chrome");
            if (query.containsKey("alpn")) {
                outbound.streamSettings.tlsSettings.alpn = query.get("alpn").split(":");
            } else {
                outbound.streamSettings.tlsSettings.alpn = new String[]{"h2", "http/1.1"};
            }
        } else if (query.getOrDefault("security", "").equals("reality")) {
            outbound.streamSettings.security = "reality";
            outbound.streamSettings.realitySettings = new RealitySettings();
            outbound.streamSettings.realitySettings.fingerprint = query.getOrDefault("fp", "chrome");
            outbound.streamSettings.realitySettings.serverName = query.getOrDefault("sni", uri.getHost());
            outbound.streamSettings.realitySettings.publicKey = query.getOrDefault("pbk", "");
            outbound.streamSettings.realitySettings.shortId = query.getOrDefault("sid", "");
        }

        proxy.config = new Gson().toJson(outbound);

        return proxy;
    }
}
