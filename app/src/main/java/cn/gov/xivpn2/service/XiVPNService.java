package cn.gov.xivpn2.service;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.VpnService;
import android.os.Binder;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.StrictMode;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.Executors;

import cn.gov.xivpn2.NotificationID;
import cn.gov.xivpn2.R;
import cn.gov.xivpn2.database.AppDatabase;
import cn.gov.xivpn2.database.Proxy;
import cn.gov.xivpn2.database.Rules;
import cn.gov.xivpn2.ui.MainActivity;
import cn.gov.xivpn2.xrayconfig.Config;
import cn.gov.xivpn2.xrayconfig.Inbound;
import cn.gov.xivpn2.xrayconfig.Outbound;
import cn.gov.xivpn2.xrayconfig.ProxyChain;
import cn.gov.xivpn2.xrayconfig.ProxyChainSettings;
import cn.gov.xivpn2.xrayconfig.Routing;
import cn.gov.xivpn2.xrayconfig.RoutingRule;
import cn.gov.xivpn2.xrayconfig.Sniffing;
import cn.gov.xivpn2.xrayconfig.Sockopt;
import cn.gov.xivpn2.xrayconfig.StreamSettings;

public class XiVPNService extends VpnService implements SocketProtect {

    public static final int SOCKS_PORT = 18964;
    private final IBinder binder = new XiVPNBinder();
    private final String TAG = "XiVPNService";
    private final Set<VPNStatusListener> listeners = new HashSet<>();
    private Status status = Status.DISCONNECTED;
    private ParcelFileDescriptor fileDescriptor;

    @Override
    public void onCreate() {
        Log.i(TAG, "on create");
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // https://developer.android.com/develop/connectivity/vpn#user_experience_2
        // https://developer.android.com/develop/connectivity/vpn#detect_always-on
        // We set always-on to false when the service is started by the app,
        // so we assume service started without always-on is started by the system.

        boolean shouldStart = intent == null || intent.getBooleanExtra("always-on", true) || (intent.getAction() != null && intent.getAction().equals("cn.gov.xivpn2.START"));

        // start vpn
        if (shouldStart) {
            if (status != Status.DISCONNECTED) {
                Log.d(TAG, "on start command already started");
                return Service.START_NOT_STICKY;
            }

            Log.i(TAG, "start foreground");

            // start foreground service
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "XiVPNService");
            builder.setContentText("XiVPN is running");
            builder.setSmallIcon(R.drawable.baseline_vpn_key_24);
            builder.setContentIntent(PendingIntent.getActivity(this, 20, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            startForeground(NotificationID.getID(), builder.build());

            // start
            try {
                Config config = buildXrayConfig();
                startVPN(config);
            } catch (IllegalArgumentException | IOException | InterruptedException | ErrnoException e) {
                Log.e(TAG, "start vpn", e);
                for (VPNStatusListener listener : listeners) {
                    listener.onMessage(e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }
        }

        // stop vpn
        if (intent != null && intent.getAction() != null && intent.getAction().equals("cn.gov.xivpn2.STOP")) {
            if (status != Status.CONNECTED) {
                Log.d(TAG, "on start command already stopped");
                return Service.START_NOT_STICKY;
            }

            stopForeground(true);
            stopVPN();
        }

        return Service.START_NOT_STICKY;
    }

    public synchronized void startVPN(Config config) throws IOException, InterruptedException, ErrnoException {
        if (status != Status.DISCONNECTED) return;

        status = Status.CONNECTING;
        for (VPNStatusListener listener : listeners) {
            listener.onStatusChanged(status);
        }

        // establish vpn
        Builder vpnBuilder = new Builder();
        vpnBuilder.addRoute("0.0.0.0", 0);
        vpnBuilder.addRoute("[::]", 0);
        vpnBuilder.addAddress("10.89.64.1", 32);
        vpnBuilder.addDnsServer("8.8.8.8");
        vpnBuilder.addDnsServer("8.8.4.4");

        Set<String> apps = getSharedPreferences("XIVPN", MODE_PRIVATE).getStringSet("APP_LIST", new HashSet<>());
        boolean blacklist = PreferenceManager.getDefaultSharedPreferences(this).getString("split_tunnel_mode", "Blacklist").equals("Blacklist");

        Log.i(TAG, "is blacklist: " + blacklist);
        for (String app : apps) {
            try {
                Log.i(TAG, "add app: " + app);
                if (blacklist) {
                    vpnBuilder.addDisallowedApplication(app);
                } else {
                    vpnBuilder.addAllowedApplication(app);
                }
            } catch (PackageManager.NameNotFoundException e) {
                Log.w(TAG, "package not found: " + app);
            }
        }

        fileDescriptor = vpnBuilder.establish();

        // logging
        String logFile = "";
        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("logs", false)) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault());
            String datetime = sdf.format(new Date());
            logFile = getFilesDir().getAbsolutePath() + "/logs/" + datetime + ".txt";
            new File(getFilesDir().getAbsolutePath(), "logs").mkdirs();
        }
        Log.i(TAG, "log file " + logFile);

        // start libxivpn
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String xrayConfig = gson.toJson(config);
        Log.i(TAG, "xray config: " + xrayConfig);

        FileOutputStream configStream = new FileOutputStream(new File(getFilesDir(), "config.json"));
        configStream.write(xrayConfig.getBytes(StandardCharsets.UTF_8));
        configStream.close();

        LocalServerSocket socket = new LocalServerSocket("xivpn_ipc");

        Process p = new ProcessBuilder()
                .directory(getFilesDir())
                .redirectError(new File(getFilesDir(), "xray.log"))
                .command(getApplicationInfo().nativeLibraryDir + "/libxivpn.so")
                .start();

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        LocalSocket localSocket = socket.accept();

        Log.i(TAG, "accepted socket");

        InputStream reader = localSocket.getInputStream();
        OutputStream writer = localSocket.getOutputStream();

        FileDescriptor[] fds = {fileDescriptor.getFileDescriptor()};
        localSocket.setFileDescriptorsForSend(fds);
        writer.write(new byte[1], 0, 1);
        writer.flush();

        // TODO
        new Thread(() -> {
            Scanner scanner = new Scanner(p.getErrorStream());
            while (scanner.hasNextLine()) {
                Log.i(TAG, scanner.nextLine());
            }
            scanner.close();
        }).start();

        new Thread(() -> {
            try {
                Field fdField = FileDescriptor.class.getDeclaredField("descriptor");
                fdField.setAccessible(true);

                byte[] buf = new byte[1];
                while (reader.read(buf) != -1) {
                    FileDescriptor[] fds_ = localSocket.getAncillaryFileDescriptors();
                    if (fds_.length != 1) {
                        Log.e(TAG, "expect 1 fds, found " + fds_.length);
                        return;
                    }

                    protectFd(fdField.getInt(fds_[0]));
                    Os.close(fds_[0]);
                    writer.write(buf);
                    writer.flush();
                }
            } catch (IOException e) {
                Log.e(TAG, "read from ipc socket", e);
            } catch (NoSuchFieldException e) {
                Log.e(TAG, "descriptor field not found", e);
            } catch (IllegalAccessException e) {
                Log.e(TAG, "access descriptor field", e);
            } catch (ErrnoException e) {
                Log.e(TAG, "close fd", e);
            }
        }).start();

        status = Status.CONNECTED;
        for (VPNStatusListener listener : listeners) {
            listener.onStatusChanged(status);
        }
    }

    public synchronized void stopVPN() {
        if (status != Status.CONNECTED) return;

        try {
            fileDescriptor.close();
        } catch (IOException e) {
            Log.e(TAG, "close fd", e);
        }

        // LibXivpn.xivpn_stop();

        status = Status.DISCONNECTED;
        for (VPNStatusListener listener : listeners) {
            listener.onStatusChanged(status);
        }

        stopSelf();
    }

    private Config buildXrayConfig() {
        Config config = new Config();
        config.inbounds = new ArrayList<>();
        config.outbounds = new ArrayList<>();

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        // logs
        config.log.loglevel = preferences.getString("log_level", "warning");

        // socks5 inbound
        Inbound socks5Inbound = new Inbound();
        socks5Inbound.protocol = "socks";
        socks5Inbound.port = XiVPNService.SOCKS_PORT;
        socks5Inbound.listen = "10.89.64.1";
        socks5Inbound.settings = new HashMap<>();
        socks5Inbound.settings.put("udp", true);

        socks5Inbound.sniffing = new Sniffing();
        socks5Inbound.sniffing.enabled = preferences.getBoolean("sniffing", true);
        socks5Inbound.sniffing.destOverride = List.of("http", "tls");
        socks5Inbound.sniffing.routeOnly = preferences.getBoolean("sniffing_route_only", true);

        config.inbounds.add(socks5Inbound);

        try {

            // routing
            List<RoutingRule> rules = Rules.readRules(getFilesDir());

            config.routing = new Routing();
            config.routing.rules = rules;

            // outbound
            HashSet<Long> proxyIds = new HashSet<>();

            for (RoutingRule rule : rules) {
                long id = AppDatabase.getInstance().proxyDao().find(rule.outboundLabel, rule.outboundSubscription).id;
                Log.d(TAG, "build xray config: add proxy: " + id + " | " + rule.outboundLabel + " | " + rule.outboundSubscription);
                proxyIds.add(id);

                rule.outboundTag = String.format(Locale.ROOT, "#%d %s (%s)", id, rule.outboundLabel, rule.outboundSubscription);
                if (rule.domain.isEmpty()) rule.domain = null;
                if (rule.ip.isEmpty()) rule.ip = null;
                if (rule.port.isEmpty()) rule.port = null;
                if (rule.protocol.isEmpty()) rule.protocol = null;
                rule.outboundLabel = null;
                rule.outboundSubscription = null;
                rule.label = null;
            }

            Gson gson = new GsonBuilder().setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE).create();

            // catch all
            SharedPreferences sp = getSharedPreferences("XIVPN", Context.MODE_PRIVATE);
            String selectedLabel = sp.getString("SELECTED_LABEL", "No Proxy (Bypass Mode)");
            String selectedSubscription = sp.getString("SELECTED_SUBSCRIPTION", "none");
            Proxy catchAll = AppDatabase.getInstance().proxyDao().find(selectedLabel, selectedSubscription);


            ArrayList<Long> proxyIdsList = new ArrayList<>(proxyIds);
            proxyIdsList.remove(catchAll.id);
            proxyIdsList.add(0, catchAll.id);

            // outbounds
            for (Long id : proxyIdsList) {
                Proxy proxy = AppDatabase.getInstance().proxyDao().findById(id);
                if (proxy.protocol.equals("proxy-chain")) {
                    // proxy chain
                    Outbound<ProxyChainSettings> proxyChainOutbound = gson.fromJson(proxy.config, new TypeToken<Outbound<ProxyChainSettings>>() {

                    }.getType());

                    List<ProxyChain> proxyChains = proxyChainOutbound.settings.proxies;

                    for (int i = proxyChains.size() - 1; i >= 0; i--) {
                        ProxyChain each = proxyChains.get(i);

                        Proxy p = AppDatabase.getInstance().proxyDao().find(each.label, each.subscription);
                        if (p == null) {
                            throw new IllegalArgumentException(String.format(Locale.ROOT, getString(R.string.proxy_chain_not_found), proxy.label, each.label));
                        }
                        if (p.protocol.equals("proxy-chain")) {
                            throw new IllegalArgumentException(String.format(Locale.ROOT, getString(R.string.proxy_chain_nesting_error), proxy.label));
                        }

                        Outbound<?> outbound = gson.fromJson(p.config, Outbound.class);
                        if (i == proxyChains.size() - 1) {
                            outbound.tag = String.format(Locale.ROOT, "#%d %s (%s)", id, proxy.label, proxy.subscription);
                        } else {
                            outbound.tag = String.format(Locale.ROOT, "CHAIN #%d %s (%s)", id, each.label, each.subscription);
                        }

                        if (i > 0) {
                            if (outbound.streamSettings == null) {
                                outbound.streamSettings = new StreamSettings();
                                outbound.streamSettings.network = "tcp";
                            }
                            outbound.streamSettings.sockopt = new Sockopt();
                            outbound.streamSettings.sockopt.dialerProxy = String.format(Locale.ROOT, "CHAIN #%d %s (%s)", id, proxyChains.get(i - 1).label, proxyChains.get(i - 1).subscription);
                        }

                        config.outbounds.add(outbound);
                    }


                } else {
                    Outbound<?> outbound = gson.fromJson(proxy.config, Outbound.class);
                    outbound.tag = String.format(Locale.ROOT, "#%d %s (%s)", id, proxy.label, proxy.subscription);
                    config.outbounds.add(outbound);
                }

            }
        } catch (IOException e) {
            Log.wtf(TAG, "build xray config", e);
        }

        return config;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void protectFd(int fd) {
        Log.d(TAG, "protect " + fd);
        this.protect(fd);

    }

    public enum Status {
        CONNECTED, CONNECTING, DISCONNECTED,
    }

    public interface VPNStatusListener {
        void onStatusChanged(Status status);

        void onMessage(String msg);
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "on destroy");
        super.onDestroy();
    }

    public class XiVPNBinder extends Binder {

        public Status getStatus() {
            return XiVPNService.this.status;
        }

        public void addListener(VPNStatusListener listener) {
            Log.d(TAG, "add listener " + listener.toString());
            XiVPNService.this.listeners.add(listener);
        }

        public void removeListener(VPNStatusListener listener) {
            Log.d(TAG, "remove listener " + listener.toString());
            XiVPNService.this.listeners.remove(listener);
        }

    }
}